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
import android.os.Build;
import android.os.Bundle;

public final class FlightAlertReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "vuelos_uy_aviso_aproximacion_v1";
    public static void createChannel(Context context) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm.getNotificationChannel(CHANNEL)==null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Avisos de vuelos próximos",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avisos según la última cartelera consultada, no notificaciones en vivo del aeropuerto");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0,250,160,250});
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());
            nm.createNotificationChannel(channel);
        }
    }

    @Override public void onReceive(Context context,Intent intent){
        if(!"uy.vuelos.uruguay.ACTION_FLIGHT_ALERT".equals(intent.getAction())) return;
        if(!context.getSharedPreferences("flight_alert_settings",Context.MODE_PRIVATE).getBoolean("enabled",false)) return;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        long fetched=intent.getLongExtra("fetched",0), eta=intent.getLongExtra("eta",0), now=System.currentTimeMillis();
        // Fail closed on stale data and outdated flights, since the app does not monitor live while minimized.
        if(fetched<=0 || now-fetched>75*60000L || eta < now-25*60000L) return;
        String body=intent.getStringExtra("body");
        if(body==null || body.isEmpty()) return;
        createChannel(context);
        Intent open=new Intent(context,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(context,999,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_plane)
            .setContentTitle("✈ Vuelos próximos a Carrasco")
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body+" · Horarios orientativos de la última consulta"))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();
        NotificationManager mgr=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.notify(intent.getIntExtra("id",1000),notification);
    }
}
