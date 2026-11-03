package uy.vuelos.uruguay;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent, best-effort name of the nearby street. This is NOT an OSM speed limit
 * provider and must never be used to infer traffic regulations.
 *
 * No continuous background processing, route history or direct external HTTP requests.
 * The Android geocoder implementation can consult its provider's network service.
 */
public final class StreetNameLookup {
    public interface Listener { void onStreet(String name); }

    private final Geocoder geocoder;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService legacyWorker = Executors.newSingleThreadExecutor();
    private boolean active=false, pending=false;
    private int sequence=0;
    private long lastAttempt=0L;
    private Location lastRequest, latestFix;
    private String lastStreet="";
    private static final long MOVE_QUERY_MS=25000L;
    private static final long RETRY_MS=60000L;
    private static final long REFRESH_MS=180000L;

    public StreetNameLookup(Context context){
        geocoder = Geocoder.isPresent() ? new Geocoder(context,new Locale("es","UY")) : null;
    }

    public void resume(){ active=true; }
    public void pause(){
        active=false;pending=false;sequence++;
        lastRequest=null;latestFix=null;
        lastStreet="";lastAttempt=0L;
    }
    public void close(){ pause();legacyWorker.shutdownNow(); }

    public void onLocation(Location fix, Listener listener){
        if(!active||fix==null||listener==null)return;
        float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.POSITIVE_INFINITY;
        if(accuracy>35f){
            listener.onStreet("Ubicación imprecisa");
            return;
        }
        latestFix=new Location(fix);
        if(geocoder==null){
            listener.onStreet("Nombre de calle no disponible");
            return;
        }
        float distance=Float.POSITIVE_INFINITY;
        if(lastRequest!=null)distance=lastRequest.distanceTo(fix);
        // A name is only displayed close to the fix that supplied it.
        if(!lastStreet.isEmpty() && distance<=100f)listener.onStreet(lastStreet);
        else listener.onStreet(pending?"Identificando calle…":"Buscando nombre de calle…");

        long now=SystemClock.elapsedRealtime();
        boolean moved=distance>75f && (lastAttempt==0L||now-lastAttempt>=MOVE_QUERY_MS);
        boolean retry=lastStreet.isEmpty() && (lastAttempt==0L||now-lastAttempt>=RETRY_MS);
        boolean refresh=lastAttempt>0 && now-lastAttempt>=REFRESH_MS;
        if(pending||!(moved||retry||refresh))return;

        pending=true;
        lastAttempt=now;
        lastRequest=new Location(fix);
        final int request=++sequence;
        final double lat=fix.getLatitude(), lon=fix.getLongitude();

        // Guard against services which never call back.
        ui.postDelayed(()->{
            if(active && request==sequence && pending){
                pending=false;sequence++;
                lastStreet="";
                listener.onStreet("Nombre de calle no disponible");
            }
        },17000L);

        if(Build.VERSION.SDK_INT>=33){
            try{
                geocoder.getFromLocation(lat,lon,3,new Geocoder.GeocodeListener(){
                    @Override public void onGeocode(List<Address> addresses){
                        ui.post(()->accept(request,lat,lon,addresses,listener));
                    }
                    @Override public void onError(String errorMessage){
                        ui.post(()->accept(request,lat,lon,null,listener));
                    }
                });
            }catch(Exception ignored){
                accept(request,lat,lon,null,listener);
            }
        }else{
            legacyWorker.execute(()->{
                List<Address> addresses=null;
                try { addresses=geocoder.getFromLocation(lat,lon,3); }
                catch(Exception ignored){}
                final List<Address> result=addresses;
                ui.post(()->accept(request,lat,lon,result,listener));
            });
        }
    }

    private void accept(int request,double lat,double lon,List<Address> addresses,Listener listener){
        if(!active || request!=sequence || !pending)return;
        pending=false;
        String name="";
        if(addresses!=null){
            for(Address address:addresses){
                if(address==null)continue;
                name=StreetNamePolicy.clean(address.getThoroughfare());
                if(!name.isEmpty())break;
            }
        }
        Location latest=latestFix;
        if(latest==null || !latest.hasAccuracy() || latest.getAccuracy()>35f)return;
        float[] meters=new float[1];
        Location.distanceBetween(latest.getLatitude(),latest.getLongitude(),lat,lon,meters);
        if(meters[0]>100f)return;
        lastStreet=name;
        listener.onStreet(name.isEmpty()?"Nombre de calle no disponible":name);
    }
}
