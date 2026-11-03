package uy.vuelos.uruguay;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opportunistic, low-frequency OpenStreetMap lookup. Visible Activity only.
 * One external query at most every 90s and only when needed; never a route tracker.
 */
public final class SpeedLimitLookup {
    public interface Callback {
        void onLimit(RoadSpeedMatcher.Match match,String state);
    }
    private static final String ENDPOINT="https://overpass-api.de/api/interpreter";
    private static final long MIN_QUERY_MS=90000L;
    private static final long MAX_CACHE_MS=300000L;
    private static final float QUERY_RADIUS_METERS=900f;
    private static final float CACHE_RADIUS_METERS=680f;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<RoadSpeedMatcher.Road> roads=Collections.emptyList();
    private double queryLat=Double.NaN,queryLon=Double.NaN;
    private long lastQueryElapsed=0L;
    private volatile boolean alive=false;
    private boolean pending=false;
    private int generation=0;

    public void resume(){alive=true;}
    public void pause(){alive=false;generation++;} // async completions may never update a hidden app
    public void close(){pause();worker.shutdownNow();}

    public void onLocation(Location pos,Callback onResult){
        if(!alive || pos==null)return;
        float acc=pos.hasAccuracy()?pos.getAccuracy():Float.POSITIVE_INFINITY;
        if(acc>25f){
            onResult.onLimit(RoadSpeedMatcher.Match.unavailable(),"GPS impreciso");
            return;
        }
        if(Double.isFinite(queryLat) && Double.isFinite(queryLon)){
            float[] d=new float[1];
            Location.distanceBetween(pos.getLatitude(),pos.getLongitude(),queryLat,queryLon,d);
            if(d[0]<=CACHE_RADIUS_METERS){
                RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(roads,
                    pos.getLatitude(),pos.getLongitude(),acc);
                onResult.onLimit(match,match.confident?"Límite cartográfico":"Sin límite registrado");
            }else onResult.onLimit(RoadSpeedMatcher.Match.unavailable(),"Consultando otra calle");
        }else onResult.onLimit(RoadSpeedMatcher.Match.unavailable(),"Consultando mapa");

        long now=android.os.SystemClock.elapsedRealtime();
        boolean hasCache=Double.isFinite(queryLat);
        boolean expired=now-lastQueryElapsed>=MAX_CACHE_MS;
        float away=Float.POSITIVE_INFINITY;
        if(hasCache){
            float[] d=new float[1];
            Location.distanceBetween(pos.getLatitude(),pos.getLongitude(),queryLat,queryLon,d);
            away=d[0];
        }
        boolean need= !hasCache || expired || away>650f;
        if(pending || !need || (lastQueryElapsed>0 && now-lastQueryElapsed<MIN_QUERY_MS))return;
        pending=true;
        lastQueryElapsed=now;
        final int requestGeneration=++generation;
        final double lat=pos.getLatitude(),lon=pos.getLongitude();
        worker.execute(()->{
            List<RoadSpeedMatcher.Road> found=null;
            String state="Límite no disponible";
            try {
                found=download(lat,lon);
                state=found.isEmpty()?"Sin límite registrado":"Datos viales recibidos";
            } catch(Exception e) {
                state="Fuente cartográfica no disponible";
            }
            final List<RoadSpeedMatcher.Road> result=found;
            final String message=state;
            handler.post(()->{
                if(!alive||requestGeneration!=generation)return;
                pending=false;
                if(result==null){
                    // Fail closed, do not show a stale limit after a failed move to new roads.
                    roads=Collections.emptyList();
                    queryLat=lat;queryLon=lon;
                }else{
                    roads=result;
                    queryLat=lat;queryLon=lon;
                }
                if(result!=null && pos.hasAccuracy()){
                    RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(result,
                        pos.getLatitude(),pos.getLongitude(),pos.getAccuracy());
                    onResult.onLimit(match,match.confident?"Límite cartográfico":message);
                } else onResult.onLimit(RoadSpeedMatcher.Match.unavailable(),message);
            });
        });
    }

    private static List<RoadSpeedMatcher.Road> download(double lat,double lon) throws Exception {
        // This query is deliberately bounded, rate-limited and used only with consent.
        String q=String.format(Locale.US,
            "[out:json][timeout:12];way(around:900,%.6f,%.6f)[\"highway\"][\"maxspeed\"];out tags geom;",
            lat,lon);
        byte[] body=("data="+java.net.URLEncoder.encode(q,"UTF-8"))
            .getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent","VuelosUruguaySpeedLimit-QA/1.11 (OSM-based opt-in)");
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(14000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            try(OutputStream out=conn.getOutputStream()){out.write(body);}
            if(conn.getResponseCode()!=200)throw new IllegalStateException("Servicio cartográfico temporalmente no disponible");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];
                int n,total=0;
                while((n=in.read(buffer))!=-1){
                    total+=n;
                    if(total>2300000)throw new IllegalStateException("Respuesta excesiva");
                    out.write(buffer,0,n);
                }
                return RoadSpeedMatcher.parseOverpass(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
}
