package uy.vuelos.uruguay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DriverBoard extends ScrollView {
    private static final int TEXT=0xFF152C40, GREY=0xFF687D90, TEAL=0xFF0F7A62;
    private final LinearLayout body;
    public DriverBoard(Context context){
        super(context);
        setFillViewport(true);
        setBackgroundColor(Color.WHITE);
        body=new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(4),dp(14),dp(15));
        addView(body,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(getContext());
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private GradientDrawable bg(int color,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1),stroke);
        return d;
    }
    private void section(String title,String detail){
        TextView t=text(title,12,TEXT,true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(3),dp(18),dp(3),dp(4));
        body.addView(t);
        TextView d=text(detail,11,GREY,false);
        d.setPadding(dp(3),0,dp(3),dp(10));
        body.addView(d);
    }
    private void flight(FlightData.Flight f, boolean first,long now){
        LinearLayout card=new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(first?0xFFE4F8EF:0xFFF8FAFC,first?0xFF9CDCC9:0xFFE2E9EE));
        card.setPadding(dp(12),dp(12),dp(12),dp(11));
        LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        param.bottomMargin=dp(9);
        body.addView(card,param);

        LinearLayout top=new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=text(f.etaLabel(),23,TEXT,true);
        top.addView(time);
        String desc=f.landed?"ATERRIZADO":FlightData.countdown(f.landingTime(),now);
        TextView pill=text(desc,10,f.landed?GREY:TEAL,true);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(bg(f.landed?0xFFE8EDF2:0xFFC9F0E0,f.landed?0xFFE8EDF2:0xFFC9F0E0));
        pill.setPadding(dp(9),dp(6),dp(9),dp(6));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin=dp(10);
        top.addView(pill,pp);
        card.addView(top);
        TextView city=text(f.origin,16,TEXT,true);
        city.setPadding(0,dp(7),0,dp(3));
        card.addView(city);
        String detail=f.flightNo+" · "+f.airline+(f.codeshares>1?" · +"+(f.codeshares-1)+" códigos":"");
        card.addView(text(detail,11,GREY,false));
        TextView status=text(f.status,11,first?TEAL:GREY,false);
        status.setPadding(0,dp(4),0,0);
        card.addView(status);
    }
    public void render(List<FlightData.Flight> flights){
        body.removeAllViews();
        final long now=System.currentTimeMillis();
        LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
        ArrayList<FlightData.Flight> soon=new ArrayList<>(),later=new ArrayList<>(),landed=new ArrayList<>();
        for(FlightData.Flight f:flights){
            LocalDate arrivalDay=Instant.ofEpochMilli(f.landingTime()).atZone(FlightData.UY).toLocalDate();
            if(!today.equals(arrivalDay)) continue;
            if(f.landed)landed.add(f);
            else if(f.upcoming(now)){
                if(f.landingTime()<=now+90*60000L)soon.add(f);else later.add(f);
            }
        }
        soon.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        later.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        landed.sort((a,b)->Long.compare(b.landingTime(),a.landingTime()));
        if(soon.isEmpty() && later.isEmpty()){
            section("SIN LLEGADAS PRÓXIMAS","No se identificaron próximos aterrizajes hoy en la fuente.");
        }else{
            section("AHORA Y PRÓXIMOS 90 MIN","Ordenados por hora estimada, más próximos primero");
            for(int i=0;i<soon.size();i++)flight(soon.get(i),i==0,now);
            if(!later.isEmpty()){
                section("MÁS TARDE","Resto de los vuelos previstos para hoy");
                for(FlightData.Flight f:later)flight(f,false,now);
            }
        }
        if(!landed.isEmpty()){
            section("YA ATERRIZARON","Últimos aterrizajes registrados");
            for(int i=0;i<Math.min(3,landed.size());i++)flight(landed.get(i),false,now);
        }
        TextView caption=text("Fuente: Avionio · Los horarios pueden cambiar. Códigos de un mismo avión agrupados por origen y horario. Tocá 'Ver panel' para otras fechas.",11,GREY,false);
        caption.setPadding(dp(2),dp(11),dp(2),dp(14));
        body.addView(caption);
    }
}
