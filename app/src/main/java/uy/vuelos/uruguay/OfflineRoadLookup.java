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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read a locally bundled OSM snapshot. No network, no accounts, no GPS history.
 * Foreground 3x3 neighborhood prefetch + bounded main-thread tile LRU.
 * Every numeric limit is computed against CURRENT GPS and road geometry.
 */
public final class OfflineRoadLookup {
    public interface Callback { void onLimit(RoadSpeedMatcher.Match match,String description); }
    private static final String ASSET="uy_roads_offline.zip";
    private static final String LOCAL="uy_roads_offline_v123.zip";
    private static final double MIN_LAT=-35.15,MAX_LAT=-34.05,MIN_LON=-57.3,MAX_LON=-55.2;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ZipFile zip;
    private boolean active=false;
    private int generation=0;
    // All cache and in-flight bookkeeping stays on the main thread.
    private static final int MAX_CACHED_TILES=24;
    private final LinkedHashMap<Long,List<RoadSpeedMatcher.Road>> tileCache =
        new LinkedHashMap<>(32,0.75f,true);
    private final Set<Long> loading=new HashSet<>();
    private long lastServedKey=Long.MIN_VALUE;
    private long warmedKey=Long.MIN_VALUE;
    private Location latestFix;
    private Callback latestCallback;
    private String cacheError="";
    private final MovingCourseFilter courseFilter=new MovingCourseFilter();
    private double heading=Double.NaN;

    public OfflineRoadLookup(Context ctx){context=ctx.getApplicationContext();}
    public void resume(){active=true;}
    public void pause(){
        active=false;generation++;
        tileCache.clear();loading.clear();
        lastServedKey=Long.MIN_VALUE;warmedKey=Long.MIN_VALUE;
        latestFix=null;latestCallback=null;cacheError="";
        courseFilter.reset();heading=Double.NaN;
    }
    public void close(){
        pause();worker.shutdownNow();
        try { if(zip!=null)zip.close(); }catch(Exception ignored){}
        zip=null;
    }
    private RoadSpeedMatcher.Match match(List<RoadSpeedMatcher.Road> roads,
                                          Location fix){
        return RoadSpeedMatcher.nearest(roads,fix.getLatitude(),fix.getLongitude(),
            fix.hasAccuracy()?fix.getAccuracy():Float.POSITIVE_INFINITY,heading);
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
        latestCallback=callback;
        // GPS heading is used only with bearing accuracy, or a measured
        // displacement: never infer a road limit from the street name alone.
        heading=courseFilter.sample(position.getLatitude(),position.getLongitude(),
            position.hasAccuracy()?position.getAccuracy():Float.POSITIVE_INFINITY,
            position.hasSpeed()?position.getSpeed():0f,
            position.getElapsedRealtimeNanos()/1000000L,
            position.hasBearing()?position.getBearing():Double.NaN,
            android.os.Build.VERSION.SDK_INT>=26 && position.hasBearingAccuracy()
                ?position.getBearingAccuracyDegrees():Double.NaN);
        float accuracy=position.hasAccuracy()?position.getAccuracy():Float.POSITIVE_INFINITY;
        if(accuracy<=0||accuracy>25f){
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                "GPS impreciso · no se confirma límite");
            return;
        }
        double lat=position.getLatitude(),lon=position.getLongitude();
        if(lat<MIN_LAT||lat>MAX_LAT||lon<MIN_LON||lon>MAX_LON){
            callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
                "Fuera de la cobertura cartográfica local");
            return;
        }
        int a=OfflineTilePolicy.cell(lat),b=OfflineTilePolicy.cell(lon);
        long key=OfflineTilePolicy.key(a,b);
        List<RoadSpeedMatcher.Road> current=tileCache.get(key);
        if(current!=null){
            lastServedKey=key;
            RoadSpeedMatcher.Match result=match(current,position);
            callback.onLimit(result,explain(result));
            warmAround(a,b);
            return;
        }
        // At a boundary, only geometry from the prior tile may justify a
        // numeric handoff; never reuse a previously displayed speed by itself.
        RoadSpeedMatcher.Match previous=RoadSpeedMatcher.Match.unavailable();
        if(lastServedKey!=Long.MIN_VALUE){
            int oldA=OfflineTilePolicy.latitudeOf(lastServedKey);
            int oldB=OfflineTilePolicy.longitudeOf(lastServedKey);
            List<RoadSpeedMatcher.Road> old=tileCache.get(lastServedKey);
            if(old!=null && OfflineTilePolicy.withinSafeLoadedMargin(oldA,oldB,lat,lon))
                previous=match(old,position);
        }
        if(previous.confident && previous.distanceMeters<=12f)
            callback.onLimit(previous,"Límite cartográfico · transición entre zonas");
        else callback.onLimit(RoadSpeedMatcher.Match.unavailable(),
            "Leyendo tramo cartográfico…");
        scheduleRead(a,b);
    }

    private void warmAround(int a,int b){
        long center=OfflineTilePolicy.key(a,b);
        if(warmedKey==center)return;
        warmedKey=center;
        // Preload all eight adjacent 1km cells. No GPS/network service
        // is used: the local OSM archive is already bundled in the APK.
        // Read one at a time on the worker; keep cache bounded to 24 tiles.
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){
            if(x==0 && y==0)continue;
            int aa=a+x,bb=b+y;
            double latitude=(aa+0.5)/100.0,longitude=(bb+0.5)/100.0;
            if(latitude>=MIN_LAT && latitude<=MAX_LAT &&
               longitude>=MIN_LON && longitude<=MAX_LON)
                scheduleRead(aa,bb);
        }
    }

    private void scheduleRead(int a,int b){
        long key=OfflineTilePolicy.key(a,b);
        if(tileCache.containsKey(key)||loading.contains(key)||!active)return;
        loading.add(key);
        final int requestGeneration=generation;
        worker.execute(()->{
            List<RoadSpeedMatcher.Road> found=Collections.emptyList();
            String error="";
            try{found=readTile(a,b);}
            catch(Exception e){error="No se pudo leer el mapa local";}
            final List<RoadSpeedMatcher.Road> answer=found;
            final String problem=error;
            main.post(()->{
                if(!active||generation!=requestGeneration)return;
                loading.remove(key);
                if(!problem.isEmpty()){
                    cacheError=problem;
                    if(latestFix!=null &&
                      OfflineTilePolicy.key(OfflineTilePolicy.cell(latestFix.getLatitude()),
                          OfflineTilePolicy.cell(latestFix.getLongitude()))==key &&
                      latestCallback!=null)
                        latestCallback.onLimit(RoadSpeedMatcher.Match.unavailable(),problem);
                    return;
                }
                tileCache.put(key,answer);
                while(tileCache.size()>MAX_CACHED_TILES){
                    Iterator<Map.Entry<Long,List<RoadSpeedMatcher.Road>>> it=
                        tileCache.entrySet().iterator();
                    if(it.hasNext()){it.next();it.remove();}
                }
                // Re-evaluate the latest GPS fix, never the stale fix that
                // triggered a background read and never the last displayed limit.
                Location current=latestFix;
                if(current==null || latestCallback==null)return;
                long actualKey=OfflineTilePolicy.key(
                    OfflineTilePolicy.cell(current.getLatitude()),
                    OfflineTilePolicy.cell(current.getLongitude()));
                if(actualKey!=key)return;
                RoadSpeedMatcher.Match result=match(answer,current);
                lastServedKey=key;
                latestCallback.onLimit(result,explain(result));
                warmAround(a,b);
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
