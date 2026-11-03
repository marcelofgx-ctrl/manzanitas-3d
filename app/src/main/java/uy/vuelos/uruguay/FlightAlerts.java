package uy.vuelos.uruguay;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in, cached local alerts only. No background location collection or third-party push. */
public final class FlightAlerts {
    public static final String SETTINGS="flight_alert_settings";
    public static final int SOUND_VIBRATE=0, VIBRATE=1, VISUAL=2;
    private static final int MAX_EVENTS=12;
    private static final int REQUEST_START=2800;
    private static final String ACTION="uy.vuelos.uruguay.ACTION_FLIGHT_ALERT";

    public static boolean enabled(Context ctx){
        return ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    public static int mode(Context ctx){
        return ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).getInt("mode",SOUND_VIBRATE);
    }
    public static void setMode(Context ctx,int value){
        if(value<SOUND_VIBRATE || value>VISUAL)return;
        ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).edit().putInt("mode",value).apply();
    }
    public static void setEnabled(Context ctx,boolean value){
        ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).edit().putBoolean("enabled",value).apply();
        if(!value)cancelAll(ctx);
    }
    public static boolean permitted(Context ctx){
        return Build.VERSION.SDK_INT<33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent token(Context ctx,int index,ArrivalAlertPolicy.Alert alert,long fetched){
        Intent intent=new Intent(ctx,FlightAlertReceiver.class).setAction(ACTION)
            .putExtra("id",REQUEST_START+index)
            .putExtra("stage",alert.stage)
            .putExtra("eta",alert.arrivalAt)
            .putExtra("fetched",fetched)
            .putExtra("group_key",alert.key)
            .putExtra("origin",alert.origin)
            .putExtra("flight",alert.flightNumber)
            .putExtra("count",alert.flights);
        return PendingIntent.getBroadcast(ctx,REQUEST_START+index,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancelAll(Context ctx){
        AlarmManager manager=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        for(int start:new int[]{1800,REQUEST_START}){
            int total=start==1800?6:MAX_EVENTS;
            for(int i=0;i<total;i++){
                PendingIntent old=PendingIntent.getBroadcast(ctx,start+i,
                    new Intent(ctx,FlightAlertReceiver.class).setAction(ACTION),
                    PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(old!=null){manager.cancel(old);old.cancel();}
            }
        }
    }

    public static int schedule(Context ctx,List<FlightData.Flight> flights,long fetched){
        cancelAll(ctx);
        if(!enabled(ctx)||!permitted(ctx))return 0;
        final long now=System.currentTimeMillis();
        if(fetched<=0 || now-fetched>ArrivalAlertPolicy.MAX_AGE)return 0;
        AlarmManager alarms=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        if(alarms==null)return 0;
        SharedPreferences preferences=ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        List<ArrivalAlertPolicy.Alert> plan=ArrivalAlertPolicy.plan(flights,now);
        int count=0;
        for(ArrivalAlertPolicy.Alert alert:plan){
            if(count>=MAX_EVENTS)break;
            if(preferences.getLong("notified_"+alert.key,0L)>0L)continue;
            // Stable indices are safe because every reload cancels and reschedules all pending alerts.
            PendingIntent ticket=token(ctx,count,alert,fetched);
            try{
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alert.deliveryAt,ticket);
                count++;
            }catch(SecurityException ignored){}
        }
        return count;
    }

    /** Only a transition from observed-in-flight to source-confirmed landed can alert. */
    public static void observeConfirmedLandings(Context ctx,List<FlightData.Flight> flights,long fetched){
        if(!enabled(ctx)||!permitted(ctx)||flights==null)return;
        long now=System.currentTimeMillis();
        if(now-fetched>ArrivalAlertPolicy.MAX_AGE || fetched<=0)return;
        LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
        SharedPreferences prefs=ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        SharedPreferences.Editor edit=prefs.edit();
        Map<Long,List<FlightData.Flight>> confirmations=new LinkedHashMap<>();
        for(FlightData.Flight f:flights){
            if(f.scheduledMillis<=0 || !today.equals(f.day()) || f.canceled)continue;
            // Scheduled time is not an actual touchdown time; the age limit prevents historical alerts.
            if(Math.abs(now-f.scheduledMillis)>4*60*ArrivalAlertPolicy.MINUTE)continue;
            String id="seen_"+(f.scheduledMillis/ArrivalAlertPolicy.BAND)+"_"+
                f.origin.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
            int was=prefs.getInt(id,0);
            if(f.landed){
                if(was==1){
                    long band=f.scheduledMillis/ArrivalAlertPolicy.BAND;
                    List<FlightData.Flight> group=confirmations.get(band);
                    if(group==null){group=new ArrayList<>();confirmations.put(band,group);}
                    group.add(f);
                }
                edit.putInt(id,2);
            }else if(was==0){
                edit.putInt(id,1);
            }
        }
        edit.apply();
        for(Map.Entry<Long,List<FlightData.Flight>> entry:confirmations.entrySet()){
            String dedup="landed_"+entry.getKey();
            if(prefs.getLong(dedup,0L)>0L)continue;
            List<FlightData.Flight> group=entry.getValue();
            FlightData.Flight first=group.get(0);
            String body=group.size()>1?
                group.size()+" vuelos confirmados como aterrizados en Carrasco.":
                first.flightNo+" · "+first.origin+" · aterrizaje confirmado por la fuente.";
            if(FlightAlertReceiver.notifyConfirmed(ctx,dedup,body)){
                prefs.edit().putLong(dedup,now).apply();
            }
        }
    }
    private FlightAlerts(){}
}
