package uy.vuelos.uruguay;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read a locally bundled OSM snapshot. No network, no accounts, no GPS history.
 * Only the tile containing the user's current position is loaded on demand.
 */
public final class OfflineRoadLookup {
    public interface Callback { void onLimit(RoadSpeedMatcher.Match match,String description); }
    private static final String ASSET="uy_roads_offline.zip";
    private static final String LOCAL="uy_roads_offline_v117.zip";
    private static final double MIN_LAT=-35.15,MAX_LAT=-34.05,MIN_LON=-57.3,MAX_LON=-55.2;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ZipFile zip;
    private boolean active=false,pending=false;
    private int generation=0;
    private int loadedLat=Integer.MAX_VALUE,loadedLon=Integer.MAX_VALUE;
    private List<RoadSpeedMatcher.Road> cached=Collections.emptyList();
    private Location latestFix;
    private String cacheError="";

    public OfflineRoadLookup(Context ctx){context=ctx.getApplicationContext();}
    public void resume(){active=true;}
    public void pause(){
        active=false;pending=false;generation++;
        loadedLat=Integer.MAX_VALUE;loadedLon=Integer.MAX_VALUE;
        cached=Collections.emptyList();latestFix=null;cacheError="";
    }
    public void close(){
        pause();worker.shutdownNow();
        try { if(zip!=null)zip.close(); }catch(Exception ignored){}
        zip=null;
    }
    private static String explain(RoadSpeedMatcher.Match m){
        if(m.confident)return m.name.isEmpty()?"Límite de OSM offline":
            "Límite OSM offline · "+m.name;
        switch(m.reason){
            case RoadSpeedMatcher.NO_LIMIT:return m.name.isEmpty()?
                "Calle sin límite registrado":"Sin límite registrado · "+m.name;
            case RoadSpeedMatcher.AMBIGUOUS:return "GPS entre calles con distintos límites";
            case RoadSpeedMatcher.GPS_INACCURATE:return "GPS impreciso";
            default:return "Calle no identificada en el mapa local";
        }
    }

    public void onLocation(Location position,Callback callback){
        if(!active||position==null)return;
        latestFix=new Location(position);
        float acc=position.hasAccuracy()?position.getAccuracy():Float.POSITIVE_INFINITY;
        if(acc<=0||acc>25f){
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                "GPS impreciso: límite sin confirmar");
            return;
        }
        double lat=position.getLatitude(),lon=position.getLongitude();
        if(lat<MIN_LAT||lat>MAX_LAT||lon<MIN_LON||lon>MAX_LON){
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                "Fuera de la cobertura local Montevideo y Canelones");
            return;
        }
        int a=(int)Math.floor(lat*100.0),b=(int)Math.floor(lon*100.0);
        if(a==loadedLat&&b==loadedLon){
            RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(cached,lat,lon,acc);
            callback.onLimit(match,explain(match));
            return;
        }
        callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
            pending?"Buscando límite en mapa local…":cacheError.isEmpty()?
            "Leyendo mapa local…":cacheError);
        if(pending)return;
        pending=true;
        final int request=++generation;
        worker.execute(()->{
            List<RoadSpeedMatcher.Road> found=Collections.emptyList();
            String err="";
            try {found=readTile(a,b);}
            catch(Exception e){err="Error al leer base cartográfica local";}
            final List<RoadSpeedMatcher.Road> answer=found;
            final String error=err;
            main.post(()->{
                if(!active||generation!=request)return;
                pending=false;cacheError=error;
                if(error.isEmpty()){
                    loadedLat=a;loadedLon=b;cached=answer;
                }
                Location current=latestFix;
                if(current==null)return;
                int c=(int)Math.floor(current.getLatitude()*100.0);
                int d=(int)Math.floor(current.getLongitude()*100.0);
                if(c!=a||d!=b){
                    // If the vehicle moved into another tile mid-read, let next fix trigger it.
                    callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                        "Cambiando de zona cartográfica…");
                    return;
                }
                if(!error.isEmpty()){
                    callback.onLimit(RoadSpeedMatcher.Match.unavailable(),error);
                    return;
                }
                RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(cached,
                    current.getLatitude(),current.getLongitude(),
                    current.hasAccuracy()?current.getAccuracy():Float.POSITIVE_INFINITY);
                callback.onLimit(match,explain(match));
            });
        });
    }

    private List<RoadSpeedMatcher.Road> readTile(int a,int b)throws Exception{
        if(zip==null){
            File file=new File(context.getCacheDir(),LOCAL);
            if(!file.exists()||file.length()<5000){
                File temp=new File(context.getCacheDir(),LOCAL+".tmp");
                try(InputStream in=context.getAssets().open(ASSET);
                    OutputStream out=new FileOutputStream(temp)){
                    byte[] buf=new byte[32768];int n;
                    while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                if(!temp.renameTo(file))throw new IllegalStateException("No se pudo copiar índice local");
            }
            zip=new ZipFile(file);
        }
        ZipEntry entry=zip.getEntry(a+"_"+b+".json");
        if(entry==null)return Collections.emptyList();
        if(entry.getSize()>6000000)throw new IllegalStateException("Tile unexpectedly large");
        try(InputStream in=zip.getInputStream(entry);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n,total=0;
            while((n=in.read(buf))!=-1){
                total+=n;
                if(total>6000000)throw new IllegalStateException("Tile too large");
                bytes.write(buf,0,n);
            }
            return OfflineRoadDecoder.parse(bytes.toString("UTF-8"));
        }
    }
}
