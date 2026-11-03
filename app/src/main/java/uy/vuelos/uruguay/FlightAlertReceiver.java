package uy.vuelos.uruguay;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.content.SharedPreferences;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Standard Android notification outside the app; a rounded white in-app card is separate. */
public final class FlightAlertReceiver extends BroadcastReceiver {
    public static final String ACTION_UI="uy.vuelos.uruguay.ACTION_ALERT_CARD";
    private static final String ACTION="uy.vuelos.uruguay.ACTION_FLIGHT_ALERT";
    public static final String CHANNEL_SOUND="vuelos_uy_sonido_vibracion_v19";
    public static final String CHANNEL_VIBRATE="vuelos_uy_solo_vibracion_v19";
    public static final String CHANNEL_VISUAL="vuelos_uy_solo_visual_v19";

    public static void createChannel(Context ctx){
        createChannels(ctx);
    }
    public static void createChannels(Context ctx){
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(nm.getNotificationChannel(CHANNEL_SOUND)==null){
            NotificationChannel c=new NotificationChannel(CHANNEL_SOUND,
                "Vuelos · Sonido y vibración",NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Avisos locales calculados con la última información consultada");
            c.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0,260,160,230});
            nm.createNotificationChannel(c);
        }
        if(nm.getNotificationChannel(CHANNEL_VIBRATE)==null){
            NotificationChannel c=new NotificationChannel(CHANNEL_VIBRATE,
                "Vuelos · Solo vibración",NotificationManager.IMPORTANCE_HIGH);
            c.setSound(null,null);
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0,220,170,220});
            nm.createNotificationChannel(c);
        }
        if(nm.getNotificationChannel(CHANNEL_VISUAL)==null){
            NotificationChannel c=new NotificationChannel(CHANNEL_VISUAL,
                "Vuelos · Solo visual",NotificationManager.IMPORTANCE_HIGH);
            c.setSound(null,null);
            c.enableVibration(false);
            nm.createNotificationChannel(c);
        }
    }

    private static String channelFor(Context ctx,boolean landed){
        int mode=FlightAlerts.mode(ctx);
        if(mode==FlightAlerts.VISUAL)return CHANNEL_VISUAL;
        if(landed || mode==FlightAlerts.VIBRATE)return CHANNEL_VIBRATE;
        return CHANNEL_SOUND;
    }

    /** Called only for source-confirmed landings observed as previously in flight. */
    public static boolean notifyConfirmed(Context ctx,String key,String body){
        if(!FlightAlerts.enabled(ctx) || !FlightAlerts.permitted(ctx))return false;
        return post(ctx, "✈ Aterrizaje confirmado",body,key,true);
    }

    @Override public void onReceive(Context ctx,Intent intent){
        if(intent==null || !ACTION.equals(intent.getAction()))return;
        if(!FlightAlerts.enabled(ctx) || !FlightAlerts.permitted(ctx))return;
        long fetched=intent.getLongExtra("fetched",0L);
        long eta=intent.getLongExtra("eta",0L);
        int stage=intent.getIntExtra("stage",-1);
        long now=System.currentTimeMillis();
        if(!ArrivalAlertPolicy.validDelivery(stage,eta,fetched,now))return;
        String key=intent.getStringExtra("group_key");
        if(key==null || !key.matches("\\d{5,14}_(40|20)"))return;
        SharedPreferences prefs=ctx.getSharedPreferences(FlightAlerts.SETTINGS,Context.MODE_PRIVATE);
        if(prefs.getLong("notified_"+key,0L)>0L)return;
        int count=intent.getIntExtra("count",1);
        String origin=intent.getStringExtra("origin");
        String flight=intent.getStringExtra("flight");
        if(origin==null)origin="Procedencia por confirmar";
        if(flight==null)flight="";
        String remaining=ArrivalAlertPolicy.remaining(eta,now);
        String localTime=DateTimeFormatter.ofPattern("HH:mm").withZone(FlightData.UY)
            .format(Instant.ofEpochMilli(eta));
        String body=count>1?
            count+" vuelos previstos · el próximo en "+remaining+" (aprox. "+localTime+").":
            flight+" · "+origin+" · en "+remaining+" (aprox. "+localTime+").";
        String title=stage==ArrivalAlertPolicy.PREPARE?
            "✈ Prepará tu traslado":"✈ Revisá tu salida a Carrasco";
        if(post(ctx,title,body,key,false))prefs.edit().putLong("notified_"+key,now).apply();
    }

    private static boolean post(Context ctx,String title,String body,String key,boolean landed){
        if(!FlightAlerts.enabled(ctx)||!FlightAlerts.permitted(ctx))return false;
        try{
            createChannels(ctx);
            NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return false;
            Intent open=new Intent(ctx,MainActivity.class).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent destination=PendingIntent.getActivity(ctx,919,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification note=new Notification.Builder(ctx,channelFor(ctx,landed))
                .setSmallIcon(R.drawable.ic_notification_plane)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body+
                    " · Horarios orientativos; comprobá el estado antes de salir."))
                .setContentIntent(destination)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setColor(0xFF177E65)
                .setColorized(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
            nm.notify(4900+Math.floorMod(key.hashCode(),5000),note);
            // Explicit same-package broadcast for foreground card only; never launch an activity.
            Intent inApp=new Intent(ACTION_UI).setPackage(ctx.getPackageName())
                .putExtra("title",title)
                .putExtra("body",body)
                .putExtra("landed",landed);
            ctx.sendBroadcast(inApp);
            return true;
        }catch(SecurityException ignored){return false;}
        catch(Exception ignored){return false;}
    }
}
