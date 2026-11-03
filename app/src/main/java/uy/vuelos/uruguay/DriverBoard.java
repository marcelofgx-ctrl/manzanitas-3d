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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native read-only driver view, including tomorrow and manual source date selection. */
public final class DriverBoard extends ScrollView {
    private static final int TEXT=0xFF152C40, GREY=0xFF687D90, TEAL=0xFF0F7A62;
    private final LinearLayout body;
    public DriverBoard(Context context){
        super(context);
        setFillViewport(true);setBackgroundColor(Color.WHITE);
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(4),dp(14),dp(15));
        addView(body,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private GradientDrawable bg(int color,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(16));d.setStroke(dp(1),stroke);return d;
    }
    private void section(String title,String detail){
        TextView t=text(title,12,TEXT,true);
        t.setLetterSpacing(0.08f);t.setPadding(dp(3),dp(18),dp(3),dp(4));body.addView(t);
        TextView d=text(detail,11,GREY,false);
        d.setPadding(dp(3),0,dp(3),dp(10));body.addView(d);
    }
    private void flight(FlightData.Flight f,boolean first,long now,boolean today){
        LinearLayout card=new LinearLayout(getContext());card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(first?0xFFE4F8EF:0xFFF8FAFC,first?0xFF9CDCC9:0xFFE2E9EE));
        card.setPadding(dp(12),dp(12),dp(12),dp(11));
        LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        param.bottomMargin=dp(9);body.addView(card,param);
        LinearLayout top=new LinearLayout(getContext());top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(f.etaLabel(),23,TEXT,true));
        String desc=f.landed?"ATERRIZADO":f.canceled?"CANCELADO":
            today&&f.upcoming(now)?FlightData.countdown(f.landingTime(),now):"PROGRAMADO";
        TextView pill=text(desc,10,f.landed||f.canceled?GREY:TEAL,true);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(bg(f.landed||f.canceled?0xFFE8EDF2:0xFFC9F0E0,f.landed||f.canceled?0xFFE8EDF2:0xFFC9F0E0));
        pill.setPadding(dp(9),dp(6),dp(9),dp(6));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin=dp(10);top.addView(pill,pp);card.addView(top);
        TextView city=text(f.origin,16,TEXT,true);city.setPadding(0,dp(7),0,dp(3));card.addView(city);
        card.addView(text(f.flightNo+" · "+f.airline+(f.codeshares>1?" · +"+(f.codeshares-1)+" códigos":""),
            11,GREY,false));
        if(f.status!=null&&!f.status.isEmpty()){
            TextView status=text(f.status,11,first?TEAL:GREY,false);
            status.setPadding(0,dp(4),0,0);card.addView(status);
        }
    }
    public void render(List<FlightData.Flight> flights,LocalDate selected,boolean emptyInSource){
        body.removeAllViews();
        final long now=System.currentTimeMillis();
        LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
        if(selected==null)selected=today;
        String dayLabel=DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","UY")).format(selected);
        section("FECHA · "+dayLabel.toUpperCase(new Locale("es","UY")),
            selected.equals(today)?"Llegadas del día · horario de Montevideo":"Consulta de fecha seleccionada · horario local");

        if(flights==null||flights.isEmpty()){
            section(emptyInSource?"SIN RESULTADOS DE AVIONIO":"DATOS NO DISPONIBLES",
                emptyInSource
                    ?"La fuente no devuelve registros para esa fecha. NO significa que no haya vuelos programados."
                    :"No se pudieron comprobar vuelos de esta fecha. Probá de nuevo o consultá el panel original.");
            note();return;
        }
        ArrayList<FlightData.Flight> soon=new ArrayList<>(),later=new ArrayList<>(),landed=new ArrayList<>(),cancelled=new ArrayList<>();
        for(FlightData.Flight f:flights) {
            if(!selected.equals(f.day()))continue;
            if(f.canceled){cancelled.add(f);continue;}
            if(f.landed){landed.add(f);continue;}
            if(selected.isAfter(today)){later.add(f);continue;}
            if(selected.isBefore(today)){landed.add(f);continue;}
            if(f.upcoming(now)){
                if(f.landingTime()<=now+90*60000L)soon.add(f);else later.add(f);
            }else landed.add(f);
        }
        soon.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        later.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
        landed.sort((a,b)->Long.compare(b.landingTime(),a.landingTime()));
        cancelled.sort(Comparator.comparingLong(FlightData.Flight::landingTime));

        if(selected.isAfter(today)){
            section("LLEGADAS PROGRAMADAS","Desde la primera hora del día · orden cronológico");
            for(int i=0;i<later.size();i++)flight(later.get(i),i==0,now,false);
        }else if(selected.equals(today)){
            if(!soon.isEmpty()){
                section("PRÓXIMOS 90 MIN", "Más cercanos primero · hora estimada si existe");
                for(int i=0;i<soon.size();i++)flight(soon.get(i),i==0,now,true);
            }
            if(!later.isEmpty()){
                section("MÁS TARDE","Resto de llegadas previstas para hoy");
                for(int i=0;i<later.size();i++)flight(later.get(i),soon.isEmpty()&&i==0,now,true);
            }
            if(soon.isEmpty()&&later.isEmpty())section("SIN PRÓXIMAS IDENTIFICADAS",
                "La fuente no muestra más arribos futuros en la fecha consultada.");
        }
        if(!landed.isEmpty()){
            section(selected.isBefore(today)?"REGISTROS HISTÓRICOS":"YA PASARON","Vuelos anteriores o registrados");
            for(int i=0;i<Math.min(selected.equals(today)?5:20,landed.size());i++)
                flight(landed.get(i),false,now,false);
        }
        if(!cancelled.isEmpty()){
            section("CANCELADOS","No generan alertas de llegada");
            for(FlightData.Flight f:cancelled)flight(f,false,now,false);
        }
        note();
    }
    private void note(){
        TextView caption=text("Fuente: Avionio. Los horarios están sujetos a cambios y un horario estimado no confirma el aterrizaje. Se agrupan códigos compartidos cuando coinciden hora y origen. Para cambiar fecha, tocá «Ver panel».",11,GREY,false);
        caption.setPadding(dp(2),dp(12),dp(2),dp(14));body.addView(caption);
    }
}
