package uy.vuelos.uruguay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Compact, source-labelled arrivals view. Only confirmed statuses are marked landed. */
public final class DriverBoard extends ScrollView {
    private static final int TEXT=0xFF152C40, GREY=0xFF687D90, TEAL=0xFF0F7A62;
    private static final int AMBER=0xFF966222;
    private final LinearLayout body;
    private LocalDate lastRenderedDate;

    public DriverBoard(Context context){
        super(context);
        setFillViewport(true);
        setBackgroundColor(Color.WHITE);
        body=new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12),dp(1),dp(12),dp(12));
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
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(13));d.setStroke(dp(1),stroke);return d;
    }
    private void section(String title,String detail){
        TextView t=text(title,12,TEXT,true);
        t.setLetterSpacing(0.045f);
        t.setPadding(dp(2),dp(12),dp(2),dp(2));
        body.addView(t);
        if(detail!=null && !detail.isEmpty()){
            TextView d=text(detail,10,GREY,false);
            d.setPadding(dp(2),0,dp(2),dp(7));
            body.addView(d);
        }
    }
    private void flight(FlightData.Flight f,boolean first,long now,boolean today){
        boolean uncertain = !f.landed && !f.canceled && f.landingTime()<now;
        boolean neutral = f.landed || f.canceled;
        int tint=uncertain?0xFFFFF7E9:first?0xFFE8F8F1:0xFFF8FAFC;
        int outline=uncertain?0xFFF1D5A6:first?0xFF9EDCC7:0xFFE3E9EE;
        LinearLayout card=new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(tint,outline));
        card.setPadding(dp(11),dp(9),dp(11),dp(8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin=dp(6);
        body.addView(card,lp);

        LinearLayout firstRow=new LinearLayout(getContext());
        firstRow.setGravity(Gravity.CENTER_VERTICAL);
        firstRow.addView(text(f.etaLabel(),21,TEXT,true));
        String desc=f.landed?"ATERRIZADO":f.canceled?"CANCELADO":
            uncertain?"SIN CONFIRMAR":today?FlightData.countdown(f.landingTime(),now):"PROGRAMADO";
        TextView pill=text(desc,10,uncertain?AMBER:neutral?GREY:TEAL,true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(9),dp(5),dp(9),dp(5));
        pill.setBackground(bg(uncertain?0xFFFBE8C8:neutral?0xFFE8EDF2:0xFFCCF0E2,
            uncertain?0xFFFBE8C8:neutral?0xFFE8EDF2:0xFFCCF0E2));
        LinearLayout.LayoutParams pillParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.leftMargin=dp(10);
        firstRow.addView(pill,pillParams);
        card.addView(firstRow);

        TextView city=text(f.origin,15,TEXT,true);
        city.setPadding(0,dp(5),0,dp(2));
        card.addView(city);

        String detail=f.flightNo+" · "+f.airline+(f.codeshares>1?" · +"+(f.codeshares-1)+" códigos":"");
        card.addView(text(detail,10,GREY,false));
        if(uncertain){
            TextView pending=text("Horario vencido; no hay confirmación de aterrizaje",10,AMBER,false);
            pending.setPadding(0,dp(3),0,0);
            card.addView(pending);
        }else if(f.status!=null && !f.status.isEmpty() && !"programado".equalsIgnoreCase(f.status.trim())){
            TextView status=text(f.status,10,first?TEAL:GREY,false);
            status.setPadding(0,dp(3),0,0);
            card.addView(status);
        }
    }
    public void render(List<FlightData.Flight> flights,LocalDate selected,boolean emptyInSource){
        int previousY=getScrollY();
        final long now=System.currentTimeMillis();
        LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
        if(selected==null)selected=today;
        boolean sameDay=selected.equals(lastRenderedDate);
        lastRenderedDate=selected;
        body.removeAllViews();
        String title=DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","UY")).format(selected);
        section("FECHA · "+title.toUpperCase(new Locale("es","UY")),
            selected.equals(today)?"Próximos aterrizajes · Montevideo":"Consulta del día seleccionado · horario local");

        if(flights==null||flights.isEmpty()){
            section(emptyInSource?"FUENTE SIN RESULTADOS":"DATOS NO DISPONIBLES",
                emptyInSource
                    ?"La fuente no muestra registros para la fecha consultada. Eso no confirma que no haya vuelos."
                    :"No se pudieron verificar vuelos. Consultá el panel original o probá de nuevo.");
            officialFallback();
            note();
            restoreScroll(sameDay,previousY);
            return;
        }

        ArrayList<FlightData.Flight> soon=new ArrayList<>(),later=new ArrayList<>(),unconfirmed=new ArrayList<>(),
            landed=new ArrayList<>(),cancelled=new ArrayList<>();
        for(FlightData.Flight f:flights){
            if(!selected.equals(f.day()))continue;
            if(f.canceled){cancelled.add(f);continue;}
            if(f.landed){landed.add(f);continue;}
            if(selected.isAfter(today)){later.add(f);continue;}
            if(selected.isBefore(today)){unconfirmed.add(f);continue;}
            if(f.landingTime()<now){unconfirmed.add(f);continue;}
            if(f.landingTime()<=now+90*60000L)soon.add(f);
            else later.add(f);
        }
        soon.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        later.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        unconfirmed.sort((a,b)->Long.compare(b.landingTime(),a.landingTime()));
        landed.sort((a,b)->Long.compare(b.landingTime(),a.landingTime()));
        cancelled.sort(Comparator.comparingLong(FlightData.Flight::landingTime));

        if(selected.isAfter(today)){
            section("LLEGADAS PROGRAMADAS","Por hora prevista, desde la primera llegada");
            for(int i=0;i<later.size();i++)flight(later.get(i),i==0,now,false);
        }else if(selected.equals(today)){
            if(!soon.isEmpty()){
                section("PRÓXIMOS 90 MIN","Primero el próximo avión · estimación si está disponible");
                for(int i=0;i<soon.size();i++)flight(soon.get(i),i==0,now,true);
            }
            if(!later.isEmpty()){
                section("MÁS TARDE","Llegadas previstas hoy");
                for(int i=0;i<later.size();i++)flight(later.get(i),soon.isEmpty()&&i==0,now,true);
            }
            if(soon.isEmpty()&&later.isEmpty())
                section("SIN LLEGADAS FUTURAS IDENTIFICADAS","Solo según los datos recibidos de la fuente.");
        }
        if(!unconfirmed.isEmpty()){
            section("HORARIO VENCIDO · SIN CONFIRMACIÓN","No se presupone que estos vuelos hayan aterrizado");
            for(int i=0;i<Math.min(6,unconfirmed.size());i++)flight(unconfirmed.get(i),false,now,false);
        }
        if(!landed.isEmpty()){
            section("ATERRIZAJES CONFIRMADOS","Según el estado informado por la fuente");
            for(int i=0;i<Math.min(selected.equals(today)?4:12,landed.size());i++)
                flight(landed.get(i),false,now,false);
        }
        if(!cancelled.isEmpty()){
            section("CANCELADOS","Sin alertas de aproximación");
            for(FlightData.Flight f:cancelled)flight(f,false,now,false);
        }
        note();
        restoreScroll(sameDay,previousY);
    }
    private void restoreScroll(boolean sameDay,int previousY){
        post(()->scrollTo(0,sameDay?previousY:0));
    }
    private void officialFallback(){
        TextView verify=text("COMPROBAR EN EL PANEL OFICIAL ↗",12,TEAL,true);
        verify.setGravity(Gravity.CENTER);
        verify.setBackground(bg(0xFFE3F7F0,0xFFB6E2D3));
        verify.setPadding(dp(12),dp(12),dp(12),dp(12));
        verify.setOnClickListener(v->{
            try{
                Intent browser=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://aeropuertodecarrasco.com.uy/panel-de-vuelos/"));
                getContext().startActivity(browser);
            }catch(Exception ignored){}
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(8);p.bottomMargin=dp(8);
        body.addView(verify,p);
    }
    private void note(){
        TextView caption=text("Horarios orientativos de fuente externa. Una estimación vencida no confirma aterrizaje. Códigos compartidos agrupados por origen y hora. Para elegir fecha: «Ver filtro».",10,GREY,false);
        caption.setPadding(dp(2),dp(10),dp(2),dp(11));
        body.addView(caption);
    }
}
