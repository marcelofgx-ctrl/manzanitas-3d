package uy.vuelos.uruguay;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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

/** Low-frequency OSM road lookup, initiated only with user's foreground GPS consent.
 * Queries all drivable streets, not only streets with maxspeed, so the true nearest
 * untagged street cannot accidentally inherit the limit of another road.
 */
public final class SpeedLimitLookup {
    public interface Callback {
        void onLimit(RoadSpeedMatcher.Match match,String state);
    }
    private static final String ENDPOINT="https://overpass-api.de/api/interpreter";
    private static final long MIN_QUERY_MS=90000L;
    private static final long MAX_CACHE_MS=300000L;
    private static final float CACHE_RADIUS_METERS=480f;
    private static final float REQUERY_DISTANCE_METERS=430f;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<RoadSpeedMatcher.Road> roads=Collections.emptyList();
    private double queryLat=Double.NaN,queryLon=Double.NaN;
    private long lastQueryElapsed=0L;
    private volatile boolean alive=false;
    private boolean pending=false;
    private int generation=0;
    private Location latestLocation;
    private String failureMessage="";

    public void resume(){alive=true;}
    public void pause(){
        alive=false;generation++;pending=false;latestLocation=null;
    }
    public void close(){pause();worker.shutdownNow();}

    private static String explain(RoadSpeedMatcher.Match match){
        if(match.confident)return "Límite cartográfico";
        switch(match.reason){
            case RoadSpeedMatcher.NO_LIMIT:
                return (match.name==null||match.name.isEmpty()?
                    "Calle sin límite cargado":"Sin límite cargado · "+match.name);
            case RoadSpeedMatcher.AMBIGUOUS:
                return "Calles cercanas: límite ambiguo";
            case RoadSpeedMatcher.GPS_INACCURATE:
                return "GPS poco preciso · acercate a la calle";
            case RoadSpeedMatcher.NO_ROAD:
            default:
                return "No se pudo identificar la calle";
        }
    }

    public void onLocation(Location location,Callback callback){
        if(!alive || location==null)return;
        latestLocation=new Location(location);
        float accuracy=location.hasAccuracy()?location.getAccuracy():Float.POSITIVE_INFINITY;
        if(accuracy>25f){
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                Double.isFinite(accuracy)?
                    "GPS impreciso (±"+Math.round(accuracy)+" m)":"Esperando GPS preciso");
            return;
        }
        boolean cached=Double.isFinite(queryLat)&&Double.isFinite(queryLon);
        float distance=Float.POSITIVE_INFINITY;
        if(cached){
            float[] result=new float[1];
            Location.distanceBetween(location.getLatitude(),location.getLongitude(),
                queryLat,queryLon,result);
            distance=result[0];
        }
        if(cached&&distance<=CACHE_RADIUS_METERS){
            RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(roads,
                location.getLatitude(),location.getLongitude(),accuracy);
            callback.onLimit(match,explain(match));
        } else {
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                pending?"Buscando límite de calle…":
                failureMessage.isEmpty()?"Identificando la calle…":failureMessage);
        }

        long now=SystemClock.elapsedRealtime();
        boolean elapsed=lastQueryElapsed==0||now-lastQueryElapsed>=MIN_QUERY_MS;
        boolean expired=lastQueryElapsed>0 && now-lastQueryElapsed>=MAX_CACHE_MS;
        boolean need=!cached || expired || distance>REQUERY_DISTANCE_METERS;
        if(pending||!need||!elapsed)return;

        final int request=++generation;
        pending=true;lastQueryElapsed=now;
        final double lat=location.getLatitude(),lon=location.getLongitude();
        worker.execute(()->{
            List<RoadSpeedMatcher.Road> found=null;
            String error="";
            try{found=download(lat,lon);}
            catch(Exception e){error="Fuente cartográfica sin respuesta · reintentando";}
            final List<RoadSpeedMatcher.Road> received=found;
            final String failure=error;
            main.post(()->{
                if(!alive||request!=generation)return;
                pending=false;
                failureMessage=failure;
                if(received==null){
                    // A failed query must not be cached as "no roads" for five minutes.
                    queryLat=Double.NaN;queryLon=Double.NaN;
                    roads=Collections.emptyList();
                    callback.onLimit(RoadSpeedMatcher.Match.unavailable(),failure);
                    return;
                }
                roads=received;
                queryLat=lat;queryLon=lon;
                Location last=latestLocation;
                if(last==null||!last.hasAccuracy())return;
                float[] away=new float[1];
                Location.distanceBetween(last.getLatitude(),last.getLongitude(),
                    lat,lon,away);
                if(away[0]>CACHE_RADIUS_METERS){
                    callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                        "Cambiaste de zona · buscando otra calle");
                    return;
                }
                RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(received,
                    last.getLatitude(),last.getLongitude(),last.getAccuracy());
                callback.onLimit(match,explain(match));
            });
        });
    }

    private static List<RoadSpeedMatcher.Road> download(double lat,double lon)throws Exception{
        // Compact query: include drivable roads with and without maxspeed tags.
        // Limit requests, cache locally in memory and never record travel history.
        String q=String.format(Locale.US,
            "[out:json][timeout:16];way(around:650,%.6f,%.6f)"+
            "[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|residential|"+
            "unclassified|living_street|service|road)(|_link)$\"];out tags geom;",
            lat,lon);
        byte[] body=("data="+java.net.URLEncoder.encode(q,"UTF-8"))
            .getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setRequestProperty("User-Agent","VuelosUruguay-QA/1.14 (foreground opt-in road limits)");
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            c.setConnectTimeout(10000);
            c.setReadTimeout(18000);
            c.setDoOutput(true);c.setUseCaches(false);
            try(OutputStream o=c.getOutputStream()){o.write(body);}
            if(c.getResponseCode()!=200)throw new IllegalStateException("Overpass unavailable");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] chunk=new byte[8192];int length,total=0;
                while((length=in.read(chunk))!=-1){
                    total+=length;
                    if(total>5000000)throw new IllegalStateException("Map response too large");
                    buffer.write(chunk,0,length);
                }
                return RoadSpeedMatcher.parseOverpass(buffer.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
}
