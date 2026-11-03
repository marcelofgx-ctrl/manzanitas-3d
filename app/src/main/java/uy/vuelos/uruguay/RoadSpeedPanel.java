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
        setBackgroundColor(0xFFF8FAFC);
        setPadding(dp(13),dp(7),dp(13),dp(7));
        LinearLayout row=new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout speedGroup=new LinearLayout(a);
        speedGroup.setOrientation(VERTICAL);
        speedGroup.addView(text("VELOCIDAD GPS",9,MUTED,true));
        speedValue=text("—",24,NAVY,true);
        speedGroup.addView(speedValue);
        row.addView(speedGroup,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        LinearLayout limitGroup=new LinearLayout(a);
        limitGroup.setOrientation(VERTICAL);
        limitGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        limitGroup.addView(text("LÍMITE CALLE",9,MUTED,true));
        limitValue=text("—",23,NAVY,true);
        limitValue.setGravity(Gravity.CENTER);
        limitValue.setMinWidth(dp(48));
        limitValue.setPadding(dp(7),dp(2),dp(7),dp(2));
        limitGroup.addView(limitValue);
        LayoutParams limitParams=new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        row.addView(limitGroup,limitParams);

        LinearLayout options=new LinearLayout(a);
        options.setOrientation(VERTICAL);
        options.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        gpsToggle=text("",11,NAVY,true);
        gpsToggle.setGravity(Gravity.CENTER);
        gpsToggle.setPadding(dp(10),dp(7),dp(10),dp(7));
        gpsToggle.setOnClickListener(v->toggleGPS());
        options.addView(gpsToggle);
        warningToggle=text("",10,MUTED,true);
        warningToggle.setGravity(Gravity.CENTER);
        warningToggle.setPadding(dp(7),dp(4),dp(7),dp(4));
        warningToggle.setOnClickListener(v->{
            warnings=!warnings;
            activity.getPreferences(Context.MODE_PRIVATE).edit()
                .putBoolean("roadSpeedWarning",warnings).apply();
            overSince=0;renderToggle();
        });
        options.addView(warningToggle);
        row.addView(options,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.06f));
        addView(row);

        LinearLayout sourceRow=new LinearLayout(a);
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        stateValue=text("Activá GPS para ver tu velocidad",10,MUTED,false);
        sourceRow.addView(stateValue,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView credit=text("© OpenStreetMap",9,0xFF337A91,false);
        credit.setContentDescription("Información vial de OpenStreetMap");
        credit.setOnClickListener(v->{
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/copyright")));}
            catch(Exception ignored){}
        });
        sourceRow.addView(credit);
        addView(sourceRow);
        renderToggle();
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
        limitValue.setBackground(pill(Color.WHITE,0xFFE1E8EE));
        if(!enabled)stateValue.setText("GPS apagado · consulta de vuelos normal");
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
            limitValue.setBackground(pill(Color.WHITE,match.confident?0xFFD44140:0xFFE1E8EE));
            stateValue.setText(match.confident
                ?(match.name.isEmpty()?"Límite OSM orientativo · señal prevalece":
                    match.name+" · límite cartográfico orientativo")
                :state+" · límite no disponible");
            updateWarning();
        });
        updateWarning();
    }

    private void updateWarning(){
        if(!hasUsableSpeed || currentLimit<=0 || !enabled){
            overSince=0;speedValue.setTextColor(NAVY);return;
        }
        boolean excess=currentKmh>currentLimit+7;
        speedValue.setTextColor(excess?0xFFBB3A33:NAVY);
        if(!excess){overSince=0;return;}
        long elapsed=SystemClock.elapsedRealtime();
        if(overSince==0)overSince=elapsed;
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
        stateValue.setText("GPS desactivado en Android");
        speedValue.setText("—");limitValue.setText("—");
    }
    private void renderToggle(){
        gpsToggle.setText(enabled?"● GPS ON":"○ ACTIVAR GPS");
        gpsToggle.setTextColor(enabled?0xFF117657:NAVY);
        gpsToggle.setBackground(pill(enabled?MINT:0xFFE6EDF2,0));
        warningToggle.setText(warnings?"⚠ AVISO VIB. ON":"⚠ AVISO OFF");
        warningToggle.setTextColor(warnings?0xFFB17B22:MUTED);
        warningToggle.setContentDescription(warnings?
            "Vibración si la velocidad GPS supera el límite cartográfico en más de siete kilómetros por hora durante cinco segundos":
            "Activar aviso vibratorio opcional por exceso de velocidad");
    }
}
