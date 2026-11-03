package uy.vuelos.uruguay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A small optional speed/lawful-limit indicator for Vuelos Uruguay.
 * Precise GPS consent, no background location, no route recording or navigation.
 */
public final class RoadSpeedPanel extends LinearLayout implements LocationListener {
    public static final int REQUEST_LOCATION=407;
    private static final int NAVY=0xFF142C40, MUTED=0xFF718597, MINT=0xFFCCF4E5;
    private final Activity activity;
    private final SpeedLimitLookup lookup=new SpeedLimitLookup();
    private LocationManager locationManager;
    private final TextView speedValue, limitValue, stateValue, gpsToggle, warningToggle;
    private LinearLayout speedTile, limitTile;
    private TextView speedCaption, limitCaption;
    private String roadStatus = "Límite no disponible";
    private boolean resumed=false, tracking=false, enabled, warnings;
    private boolean hasUsableSpeed=false;
    private int currentKmh=-1, currentLimit=0;
    private long overSince=0L, lastVibrate=0L;

    public RoadSpeedPanel(Activity a){
        super(a);
        activity=a;
        enabled=a.getPreferences(Context.MODE_PRIVATE).getBoolean("roadSpeedEnabled",false);
        warnings=a.getPreferences(Context.MODE_PRIVATE).getBoolean("roadSpeedWarning",false);
        setOrientation(VERTICAL);
        setBackgroundColor(0xFFF7FAFC);
        setPadding(dp(11),dp(8),dp(11),dp(6));

        // Phone-width layout: two large readings and one narrow control column.
        LinearLayout meters=new LinearLayout(a);
        meters.setGravity(Gravity.CENTER_VERTICAL);

        speedTile=new LinearLayout(a);
        speedTile.setOrientation(VERTICAL);
        speedTile.setPadding(dp(12),dp(7),dp(9),dp(7));
        speedCaption=text("VELOCIDAD ACTUAL",9,MUTED,true);
        speedTile.addView(speedCaption);
        LinearLayout currentLine=new LinearLayout(a);
        currentLine.setGravity(Gravity.BOTTOM);
        speedValue=text("—",34,NAVY,true);
        speedValue.setIncludeFontPadding(false);
        currentLine.addView(speedValue);
        TextView speedUnit=text("km/h",11,MUTED,false);
        speedUnit.setPadding(dp(5),0,0,dp(4));
        currentLine.addView(speedUnit);
        speedTile.addView(currentLine);
        speedTile.setBackground(pill(Color.WHITE,0xFFE1EAF0));

        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(
            0,ViewGroup.LayoutParams.WRAP_CONTENT,1.05f);
        sp.rightMargin=dp(7);
        meters.addView(speedTile,sp);

        limitTile=new LinearLayout(a);
        limitTile.setOrientation(VERTICAL);
        limitTile.setPadding(dp(10),dp(7),dp(7),dp(7));
        limitCaption=text("LÍMITE DEL TRAMO",9,MUTED,true);
        limitTile.addView(limitCaption);
        LinearLayout limitLine=new LinearLayout(a);
        limitLine.setGravity(Gravity.BOTTOM);
        limitValue=text("—",32,NAVY,true);
        limitValue.setIncludeFontPadding(false);
        limitLine.addView(limitValue);
        TextView limitUnit=text("km/h",11,MUTED,false);
        limitUnit.setPadding(dp(4),0,0,dp(4));
        limitLine.addView(limitUnit);
        limitTile.addView(limitLine);
        limitTile.setBackground(pill(Color.WHITE,0xFFE1EAF0));

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        lp.rightMargin=dp(7);
        meters.addView(limitTile,lp);

        LinearLayout options=new LinearLayout(a);
        options.setOrientation(VERTICAL);
        options.setGravity(Gravity.CENTER);
        gpsToggle=text("",10,NAVY,true);
        gpsToggle.setGravity(Gravity.CENTER);
        gpsToggle.setMinHeight(dp(36));
        gpsToggle.setPadding(dp(5),dp(5),dp(5),dp(5));
        gpsToggle.setOnClickListener(v->toggleGPS());
        options.addView(gpsToggle,new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        warningToggle=text("",9,MUTED,true);
        warningToggle.setGravity(Gravity.CENTER);
        warningToggle.setMinHeight(dp(29));
        warningToggle.setPadding(dp(3),dp(4),dp(3),dp(4));
        warningToggle.setOnClickListener(v->{
            warnings=!warnings;
            activity.getPreferences(Context.MODE_PRIVATE).edit()
                .putBoolean("roadSpeedWarning",warnings).apply();
            overSince=0;
            renderToggle();
        });
        options.addView(warningToggle,new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        meters.addView(options,new LinearLayout.LayoutParams(dp(70),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(meters);

        LinearLayout footer=new LinearLayout(a);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(2),dp(5),dp(2),0);
        stateValue=text("GPS apagado · tocá GPS para activar",10,MUTED,false);
        stateValue.setSingleLine(true);
        stateValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footer.addView(stateValue,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView credit=text("© OpenStreetMap",9,0xFF337A91,false);
        credit.setContentDescription("Datos viales OpenStreetMap. Abrir condiciones de atribución");
        credit.setPadding(dp(5),0,0,0);
        credit.setOnClickListener(v->{
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/copyright")));}
            catch(Exception ignored){}
        });
        footer.addView(credit);
        addView(footer);
        renderToggle();
        renderSpeedColors();
    }

    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(activity);
        t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private GradientDrawable pill(int fill,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(13));
        if(stroke!=0)d.setStroke(dp(2),stroke);
        return d;
    }

    private boolean hasPermission(){
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            ==PackageManager.PERMISSION_GRANTED;
    }
    private void toggleGPS(){
        if(enabled){
            enabled=false;
            activity.getPreferences(Context.MODE_PRIVATE).edit()
                .putBoolean("roadSpeedEnabled",false).apply();
            stop();
            renderToggle();
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("Velocímetro en Vuelos Uruguay")
            .setMessage("Para mostrar tu velocidad usamos el GPS solo mientras Vuelos Uruguay está abierto.\n\n"+
                "Si activás el límite de calle, se envía tu posición actual al servicio público de OpenStreetMap para consultar datos viales. No guardamos recorridos ni pedimos ubicación en segundo plano.\n\n"+
                "Los límites pueden faltar o estar desactualizados: siempre tienen prioridad las señales de tránsito.")
            .setNegativeButton("Cancelar",(d,w)->{})
            .setPositiveButton("Activar GPS",(d,w)->{
                if(!hasPermission()){
                    activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_LOCATION);
                }else enableAndStart();
            }).show();
    }

    private void enableAndStart(){
        if(!hasPermission()){
            stateValue.setText("Se requiere ubicación precisa para identificar la calle.");
            return;
        }
        enabled=true;
        activity.getPreferences(Context.MODE_PRIVATE).edit().putBoolean("roadSpeedEnabled",true).apply();
        renderToggle();
        if(resumed)start();
    }

    public void permissionResult(int[] grants){
        if(hasPermission())enableAndStart();
        else {
            enabled=false;
            renderToggle();
            stateValue.setText("Ubicación no autorizada · GPS apagado");
        }
    }
    public void resume(){
        resumed=true;
        if(enabled && hasPermission())start();
        else if(enabled)stateValue.setText("Activá ubicación precisa en permisos de Android");
    }
    public void pause(){
        resumed=false;
        stop();
    }
    public void close(){
        stop();
        lookup.close();
    }

    private void start(){
        if(!resumed || tracking || !enabled || !hasPermission())return;
        locationManager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        if(locationManager==null)return;
        try{
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                stateValue.setText("GPS del teléfono desactivado");
                return;
            }
            lookup.resume();
            // Only one foreground GPS subscription; no background updates or map navigation.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,3f,
                this,activity.getMainLooper());
            tracking=true;
            stateValue.setText("Esperando señal GPS…");
        }catch(SecurityException e){
            stateValue.setText("Falta autorización GPS");
        }catch(Exception e){
            stateValue.setText("El GPS no está disponible");
        }
    }

    private void stop(){
        if(locationManager!=null && tracking){
            try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        }
        tracking=false;
        lookup.pause();
        currentKmh=-1;
        currentLimit=0;
        hasUsableSpeed=false;
        overSince=0;
        speedValue.setText("—");
        limitValue.setText("—");
        roadStatus=enabled?"GPS en pausa":"GPS apagado · tocá GPS para activar";
        renderSpeedColors();
    }
    @Override public void onLocationChanged(Location location){
        if(!tracking || !resumed || !enabled || location==null)return;
        float acc=location.hasAccuracy()?location.getAccuracy():Float.POSITIVE_INFINITY;
        boolean speedGood=location.hasSpeed() && acc<=25f &&
            (!location.hasSpeedAccuracy() || location.getSpeedAccuracyMetersPerSecond()<=4f);
        if(speedGood){
            currentKmh=Math.max(0,Math.round(location.getSpeed()*3.6f));
            // Remove jitter when stopped, never use the phone accelerometer as an alternate legal speed.
            if(currentKmh<3)currentKmh=0;
            speedValue.setText(String.valueOf(currentKmh));
            hasUsableSpeed=true;
        }else{
            currentKmh=-1;
            hasUsableSpeed=false;
            speedValue.setText("—");
        }
        lookup.onLocation(location,(match,state)->{
            if(!resumed||!enabled||!tracking)return;
            currentLimit=match.confident?match.limitKmh:0;
            limitValue.setText(match.confident?String.valueOf(match.limitKmh):"—");
            roadStatus=match.confident
                ?(match.name.isEmpty()?"Límite cartográfico · señal prevalece":
                    match.name+" · límite cartográfico")
                :state+" · límite no disponible";
            updateWarning();
        });
        updateWarning();
    }

    private void renderSpeedColors(){
        int visual=SpeedColorPolicy.state(currentKmh,currentLimit);
        int speedBackground=0xFFFFFFFF,limitBackground=0xFFFFFFFF;
        int speedBorder=0xFFE1EAF0,limitBorder=0xFFE1EAF0;
        int speedInk=NAVY,captionInk=MUTED;
        if(visual==SpeedColorPolicy.AMBER){
            speedBackground=0xFFFFF4E6;
            limitBackground=0xFFFFFBF5;
            speedBorder=0xFFEAC48B;limitBorder=0xFFEAC48B;
            speedInk=0xFF9E641B;captionInk=0xFF9E641B;
        }else if(visual==SpeedColorPolicy.RED){
            speedBackground=0xFFFFEBE9;
            limitBackground=0xFFFFF5F4;
            speedBorder=0xFFCD3936;limitBorder=0xFFCD3936;
            speedInk=0xFFBB2829;captionInk=0xFFBB2829;
        }
        speedTile.setBackground(pill(speedBackground,speedBorder));
        limitTile.setBackground(pill(limitBackground,limitBorder));
        speedValue.setTextColor(speedInk);
        limitValue.setTextColor(visual==SpeedColorPolicy.RED?0xFFBB2829:NAVY);
        speedCaption.setTextColor(captionInk);
        limitCaption.setTextColor(visual==SpeedColorPolicy.RED?0xFFBB2829:MUTED);
        if(visual==SpeedColorPolicy.RED){
            stateValue.setText("EXCESO +"+SpeedColorPolicy.excess(currentKmh,currentLimit)+
                " km/h · límite cartográfico orientativo");
            stateValue.setTextColor(0xFFB02B2A);
        }else if(visual==SpeedColorPolicy.AMBER){
            stateValue.setText("Sobre límite +"+SpeedColorPolicy.excess(currentKmh,currentLimit)+
                " km/h · precaución");
            stateValue.setTextColor(0xFF96631F);
        }else{
            stateValue.setText(roadStatus);
            stateValue.setTextColor(MUTED);
        }
        String limitDescription=currentLimit>0 ? currentLimit+" kilómetros por hora registrados":
            "límite no disponible";
        speedTile.setContentDescription("Velocidad actual GPS: "+
            (currentKmh<0?"no disponible":currentKmh+" kilómetros por hora")+
            ". "+(visual==SpeedColorPolicy.RED?"Alerta roja de exceso. ":
                visual==SpeedColorPolicy.AMBER?"Advertencia ámbar. ":"")+
            "Límite cartográfico: "+limitDescription);
        limitTile.setContentDescription("Límite del tramo: "+limitDescription+
            ". Datos de OpenStreetMap; la señalización real prevalece.");
    }

    private void updateWarning(){
        renderSpeedColors();
        int visual=SpeedColorPolicy.state(currentKmh,currentLimit);
        if(visual!=SpeedColorPolicy.RED || !enabled || !hasUsableSpeed){
            overSince=0L;return;
        }
        long elapsed=SystemClock.elapsedRealtime();
        if(overSince==0L)overSince=elapsed;
        if(!warnings || elapsed-overSince<5000L || elapsed-lastVibrate<45000L)return;
        lastVibrate=elapsed;
        try{
            Vibrator vib=(Vibrator)activity.getSystemService(Context.VIBRATOR_SERVICE);
            if(vib!=null && vib.hasVibrator()){
                if(Build.VERSION.SDK_INT>=26)
                    vib.vibrate(VibrationEffect.createOneShot(170,VibrationEffect.DEFAULT_AMPLITUDE));
                else vib.vibrate(170);
            }
        }catch(Exception ignored){}
    }
    @Override public void onProviderDisabled(String provider){
        currentKmh=-1;
        currentLimit=0;
        hasUsableSpeed=false;
        roadStatus="GPS desactivado en Android";
        speedValue.setText("—");limitValue.setText("—");
        overSince=0L;
        renderSpeedColors();
    }
    private void renderToggle(){
        gpsToggle.setText(enabled?"● GPS ON":"○ GPS OFF");
        gpsToggle.setTextColor(enabled?0xFF117657:NAVY);
        gpsToggle.setBackground(pill(enabled?MINT:0xFFE6EDF2,0));
        gpsToggle.setContentDescription(enabled?"Desactivar GPS":"Activar GPS para conocer tu velocidad y consultar el límite de calle");
        warningToggle.setText(warnings?"VIB. ON":"VIB. OFF");
        warningToggle.setTextColor(warnings?0xFF9E641B:MUTED);
        warningToggle.setBackground(pill(warnings?0xFFFFF1DF:0xFFF4F7FA,0));
        warningToggle.setContentDescription(warnings?
            "Desactivar vibración al superar el límite cartográfico en 7 kilómetros por hora durante cinco segundos":
            "Activar vibración opcional de exceso: siete kilómetros por hora sobre el límite cartográfico durante cinco segundos");
    }
}
