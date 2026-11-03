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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class FlightAlerts {
    public static final String SETTINGS="flight_alert_settings";
    private static final int MAX_EVENTS=6;
    private static final long MINUTE=60000L;
    private static final long FIFTEEN=15*MINUTE;

    public static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    public static void setEnabled(Context ctx,boolean value){
        ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).edit().putBoolean("enabled",value).apply();
        if(!value)cancelAll(ctx);
    }
    public static boolean permitted(Context ctx){
        return Build.VERSION.SDK_INT<33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent token(Context ctx,int index,String body,long eta,long fetched,String groupKey){
        Intent i=new Intent(ctx,FlightAlertReceiver.class)
            .setAction("uy.vuelos.uruguay.ACTION_FLIGHT_ALERT")
            .putExtra("id",1800+index)
            .putExtra("body",body)
            .putExtra("eta",eta)
            .putExtra("fetched",fetched)
            .putExtra("group_key",groupKey);
        return PendingIntent.getBroadcast(ctx,1800+index,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void cancelAll(Context ctx){
        AlarmManager alarm=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        for(int i=0;i<MAX_EVENTS;i++){
            PendingIntent old=PendingIntent.getBroadcast(ctx,1800+i,
                new Intent(ctx,FlightAlertReceiver.class).setAction("uy.vuelos.uruguay.ACTION_FLIGHT_ALERT"),
                PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(old!=null){alarm.cancel(old);old.cancel();}
        }
    }
    private static final class Group {
        final ArrayList<FlightData.Flight> flights=new ArrayList<>();
        long earliest=Long.MAX_VALUE;
        void add(FlightData.Flight f){ flights.add(f);earliest=Math.min(earliest,f.landingTime()); }
    }
    public static int schedule(Context ctx,List<FlightData.Flight> flights,long fetched) {
        cancelAll(ctx);
        if(!enabled(ctx)||!permitted(ctx)||flights==null||flights.isEmpty())return 0;
        long now=System.currentTimeMillis();
        LinkedHashMap<Long,Group> groups=new LinkedHashMap<>();
        for(FlightData.Flight f:flights) {
            if(!f.upcoming(now) || f.landingTime()<now+5*MINUTE || f.landingTime()>now+85*MINUTE)continue;
            long band=(f.landingTime()/FIFTEEN)*FIFTEEN;
            Group group=groups.get(band);
            if(group==null){group=new Group();groups.put(band,group);}
            group.add(f);
        }
        AlarmManager alarms=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences history=ctx.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        int count=0;
        for(Group group:groups.values()){
            if(count>=MAX_EVENTS)break;
            FlightData.Flight first=group.flights.get(0);
            long arrival=group.earliest;
            String key=String.valueOf((arrival/FIFTEEN)*FIFTEEN);
            if(history.getLong("notified_"+key,0L)>0L)continue;
            long notifyAt=Math.max(now+10000L,arrival-25*MINUTE);
            if(notifyAt >= arrival)continue;
            DateTimeFormatter time=DateTimeFormatter.ofPattern("HH:mm").withZone(FlightData.UY);
            String label=time.format(Instant.ofEpochMilli(arrival));
            String body=group.flights.size()==1
                ?first.origin+" · "+first.flightNo+" · previsto "+label
                :group.flights.size()+" vuelos previstos alrededor de "+label;
            PendingIntent ticket=token(ctx,count,body,arrival,fetched,key);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,notifyAt,ticket);
            count++;
        }
        return count;
    }

    private FlightAlerts(){}
}
