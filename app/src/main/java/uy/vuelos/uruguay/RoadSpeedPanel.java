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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A small optional speed/lawful-limit indicator for Vuelos Uruguay.
 * Precise GPS consent, no background location, no route recording or navigation.
 */
public final class RoadSpeedPanel extends LinearLayout implements LocationListener {
    public static final int REQUEST_LOCATION=407;
    private static final int NAVY=0xFF142C40, MUTED=0xFF718597, MINT=0xFFCCF4E5;
    private final Activity activity;
    private final OfflineRoadLookup lookup;
    private final StreetNameLookup streetLookup;
    private LocationManager locationManager;
    private final TextView speedValue, limitValue, stateValue;
    private LinearLayout speedTile, limitTile, details, capsuleHeader;
    private TextView speedCaption, limitCaption, streetValue, streetSummary, chevron;
    private boolean expanded=false;
    private String nearbyStreet="", mapStreet="";
    private String lastVisibleStreet="";
    private long lastStreetConfirmedMs=0L;
    private Location lastStreetFix, lastSpeedFix;
    private String roadStatus = "Límite no disponible";
    private boolean resumed=false, tracking=false, enabled;
    // Excess-speed vibration remains active, with no separate GPS/VIB toggles.
    private final boolean warnings=true;
    private boolean hasUsableSpeed=false;
    private int currentKmh=-1, currentLimit=0;
    private long overSince=0L, lastVibrate=0L;

    public RoadSpeedPanel(Activity a){
        super(a);
        activity=a;
        lookup=new OfflineRoadLookup(a);
        streetLookup=new StreetNameLookup(a);
        enabled=hasPermission();
        setOrientation(VERTICAL);
        setBackgroundColor(0xFFF7FAFC);
        setPadding(dp(11),dp(5),dp(11),dp(5));

        // One compact header is always visible; tap to expand the existing
        // speed, road-limit and attribution details. No GPS/VIB switches.
        capsuleHeader=new LinearLayout(a);
        capsuleHeader.setGravity(Gravity.CENTER_VERTICAL);
        capsuleHeader.setPadding(dp(11),dp(7),dp(9),dp(7));
        capsuleHeader.setMinimumHeight(dp(44));
        capsuleHeader.setBackground(pill(0xFFF0F5F8,0xFFDCE7ED));
        streetValue=text("Buscando calle…",15,NAVY,false);
        streetValue.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        streetValue.setSingleLine(true);
        streetValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        streetValue.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        streetValue.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        capsuleHeader.addView(streetValue,new LinearLayout.LayoutParams(
            0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        streetSummary=text("— / —",13,NAVY,true);
        streetSummary.setSingleLine(true);
        streetSummary.setPadding(dp(8),0,dp(5),0);
        streetSummary.setContentDescription("Velocidad GPS y límite cartográfico en kilómetros por hora");
        capsuleHeader.addView(streetSummary);
        chevron=text("⌄",23,0xFF538399,true);
        chevron.setGravity(Gravity.CENTER);
        capsuleHeader.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(30)));
        capsuleHeader.setClickable(true);
        capsuleHeader.setFocusable(true);
        capsuleHeader.setOnClickListener(v->setExpanded(!expanded));
        addView(capsuleHeader,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        details=new LinearLayout(a);
        details.setOrientation(VERTICAL);
        details.setPadding(0,dp(5),0,0);
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
        limitValue=text("—",29,NAVY,true);
        limitValue.setIncludeFontPadding(false);
        limitLine.addView(limitValue);
        TextView limitUnit=text("km/h",11,MUTED,false);
        limitUnit.setPadding(dp(4),0,0,dp(4));
        limitLine.addView(limitUnit);
        limitTile.addView(limitLine);
        limitTile.setBackground(pill(Color.WHITE,0xFFE1EAF0));
        limitTile.setOnClickListener(v->new AlertDialog.Builder(activity)
            .setTitle("Límite de velocidad del tramo")
            .setMessage((currentLimit>0?currentLimit+" km/h · dato OSM guardado en la APK":
                "Dato no disponible")+"\n\n"+roadStatus+
                "\n\nLa aplicación solo muestra un número si puede identificar el tramo y un límite registrado para esa calle. No reemplaza las señales reales.")
            .setPositiveButton("Entendido",(d,w)->{})
            .show());

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        lp.rightMargin=dp(7);
        meters.addView(limitTile,lp);

        // Both tiles take the whole available width when expanded.
        lp.rightMargin=0;
        details.addView(meters);
        LinearLayout footer=new LinearLayout(a);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(2),dp(2),dp(2),0);
        stateValue=text("Esperando permiso de ubicación",10,MUTED,false);
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
        details.addView(footer);
        addView(details,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setExpanded(false);
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
    public void permissionResult(int[] grants){
        // Called by startup permission onboarding, not by an in-app switch.
        enabled=hasPermission();
        if(enabled){
            if(resumed)start();
        }else{
            stop();
            roadStatus="Ubicación precisa pendiente · habilitala en permisos";
            renderStreetName();
            renderSpeedColors();
        }
    }
    public void resume(){
        resumed=true;
        enabled=hasPermission();
        if(enabled)start();
        else {
            roadStatus="Ubicación precisa pendiente · habilitala en permisos";
            renderStreetName();
            renderSpeedColors();
        }
    }
    public void pause(){
        resumed=false;
        stop();
    }
    public void close(){
        stop();
        lookup.close();
        streetLookup.close();
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
            streetLookup.resume();
            // Only one foreground GPS subscription; no background updates or map navigation.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,0f,
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
        streetLookup.pause();
        nearbyStreet="";
        mapStreet="";
        lastVisibleStreet="";
        lastStreetConfirmedMs=0L;
        lastStreetFix=null;
        lastSpeedFix=null;
        renderStreetName();
        currentKmh=-1;
        currentLimit=0;
        hasUsableSpeed=false;
        overSince=0;
        speedValue.setText("—");
        limitValue.setText("—");
        roadStatus=enabled?"GPS en pausa":"Permiso de ubicación pendiente";
        renderSpeedColors();
    }
    @Override public void onLocationChanged(Location location){
        if(!tracking || !resumed || !enabled || location==null)return;
        lastSpeedFix=new Location(location);
        streetLookup.onLocation(location,name->{
            if(!tracking||!resumed||!enabled)return;
            nearbyStreet=name;
            renderStreetName();
        });
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
            // A sufficiently near mapped street can give an approximate NAME
            // even when a competing legal speed limit cannot be distinguished.
            // This does NOT authorize or preserve any numeric speed limit.
            mapStreet=(match.name!=null && !match.name.isEmpty() &&
              (match.confident || RoadSpeedMatcher.NO_LIMIT.equals(match.reason) ||
               (RoadSpeedMatcher.AMBIGUOUS.equals(match.reason) &&
                match.distanceMeters<=12.0)))
                ? StreetNamePolicy.clean(match.name):"";
            renderStreetName();
            currentLimit=match.confident?match.limitKmh:0;
            limitValue.setText(match.confident?String.valueOf(match.limitKmh):"—");
            roadStatus=match.confident
                ?(match.name.isEmpty()?"Límite cartográfico · señal prevalece":
                    match.name+" · límite local OSM")
                :state+" · límite no disponible";
            updateWarning();
        });
        updateWarning();
    }

    private static boolean isActualStreetName(String value){
        return value!=null && !value.isEmpty() &&
            !value.contains("…") && !value.startsWith("Nombre de calle") &&
            !value.startsWith("Ubicación imprecisa");
    }

    private void renderStreetName(){
        if(streetValue==null)return;
        String name="";
        if(!enabled){
            name="Ubicación pendiente";
        }else if(!tracking){
            name="Esperando GPS…";
        }else{
            if(isActualStreetName(mapStreet))name=mapStreet;
            else if(isActualStreetName(nearbyStreet))name=nearbyStreet;

            long now=SystemClock.elapsedRealtime();
            if(!name.isEmpty()){
                lastVisibleStreet=name;
                lastStreetConfirmedMs=now;
                if(lastSpeedFix!=null)lastStreetFix=new Location(lastSpeedFix);
            }else if(lastStreetFix!=null && !lastVisibleStreet.isEmpty() &&
              now-lastStreetConfirmedMs<4500L &&
              lastSpeedFix!=null && lastStreetFix.distanceTo(lastSpeedFix)<55f){
                // Avoid a one-frame "Buscando…" flash while a new map tile is read.
                // This is a street NAME only; the limit resets immediately when uncertain.
                name=lastVisibleStreet;
            }else{
                name=nearbyStreet.isEmpty()?"Buscando calle…":nearbyStreet;
            }
        }
        streetValue.setText(name);
        renderSummary();
        streetValue.setContentDescription("Nombre aproximado de la calle: "+name+
            ". El límite se comprueba por separado con el mapa local.");
    }

    private void setExpanded(boolean expand){
        expanded=expand;
        if(details!=null)details.setVisibility(expand?View.VISIBLE:View.GONE);
        if(chevron!=null)chevron.setText(expand?"⌃":"⌄");
        if(capsuleHeader!=null)
            capsuleHeader.setContentDescription((streetValue==null?"Calle":
                String.valueOf(streetValue.getText()))+
                ". Velocidad y límite en kilómetros por hora. Tocá para "+
                (expand?"contraer":"expandir")+" el velocímetro");
    }
    private void renderSummary(){
        if(streetSummary==null)return;
        String speed=currentKmh>=0?String.valueOf(currentKmh):"—";
        String limit=currentLimit>0?String.valueOf(currentLimit):"—";
        streetSummary.setText(speed+" / "+limit);
        streetSummary.setContentDescription("Velocidad GPS "+speed+
            " kilómetros por hora; límite cartográfico "+limit+
            " kilómetros por hora");
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
        streetSummary.setTextColor(visual==SpeedColorPolicy.RED?0xFFBB2829:
            visual==SpeedColorPolicy.AMBER?0xFF96631F:NAVY);
        renderSummary();
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
        // Solo presentación: ocultar el renglón que repite la calle/límite
        // cuando tenemos un límite identificado. Las alertas de exceso,
        // errores y tramos sin datos siguen visibles y funcionando igual.
        boolean duplicateMappedCaption=currentLimit>0 &&
            (visual==SpeedColorPolicy.NORMAL || visual==SpeedColorPolicy.UNKNOWN);
        stateValue.setVisibility(duplicateMappedCaption?View.GONE:View.VISIBLE);
        String limitDescription=currentLimit>0 ? currentLimit+" kilómetros por hora registrados":
            "límite no disponible. Tocá el panel para saber por qué";
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

}
