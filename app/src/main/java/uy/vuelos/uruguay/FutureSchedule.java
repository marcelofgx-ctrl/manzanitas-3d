package uy.vuelos.uruguay;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** User-initiated, read-only supplementary published flight-schedule lookup.
 * Fail closed unless the provider confirms the selected date and an updated result board.
 * Future schedules are NOT live arrival estimates and must never trigger landing alerts.
 */
public final class FutureSchedule {
    public static final String SOURCE_URL = "https://www.flight.info/MVD/arrivals";
    public static String applyDateJs(LocalDate day) {
        String iso=day.toString(); // LocalDate formatter guarantees safe fixed ISO characters.
        return String.join("\n",
            "(function(){",
            " var date='"+iso+"';",
            " var inputs=Array.from(document.querySelectorAll('input'));",
            " var dateEl=inputs.find(function(i){var d=(i.name+' '+i.id+' '+i.placeholder+' '+i.type).toLowerCase();return i.type==='date'||d.indexOf('date')>=0||d.indexOf('fecha')>=0;});",
            " if(!dateEl)return JSON.stringify({applied:false,reason:'date_input_missing'});",
            " window.__vuInitialDate=String(dateEl.value||'');",
            " window.__vuInitialBoard=Array.from(document.querySelectorAll('table tr,[role=\"row\"]')).map(function(x){return x.innerText||'';}).join('|').slice(0,5000);",
            " window.__vuTargetDate=date;",
            " var nativeSetter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');",
            " try{nativeSetter.set.call(dateEl,date);}catch(e){dateEl.value=date;}",
            " dateEl.dispatchEvent(new Event('input',{bubbles:true}));",
            " dateEl.dispatchEvent(new Event('change',{bubbles:true}));",
            " var form=dateEl.closest('form');",
            " if(form){",
            "   var submit=form.querySelector('button[type=submit],input[type=submit]');",
            "   if(submit){try{submit.click();}catch(e){}}",
            " }",
            " return JSON.stringify({applied:true});",
            "})();");
    }

    public static final String EXTRACT_JS = String.join("\n",
        "(function(){",
        " function norm(s){return String(s||'').replace(/\\s+/g,' ').trim();}",
        " var candidates=Array.from(document.querySelectorAll('input'));",
        " var dateEl=candidates.find(function(i){var d=(i.name+' '+i.id+' '+i.placeholder+' '+i.type).toLowerCase();return i.type==='date'||d.indexOf('date')>=0||d.indexOf('fecha')>=0;});",
        " var current=dateEl?norm(dateEl.value):'';",
        " var requested=window.__vuTargetDate||'';",
        " var dateVerified=!!requested && (current===requested || current===requested.split('-').reverse().join('/'));",
        " var tr=Array.from(document.querySelectorAll('table tr,[role=\"row\"]'));",
        " var currentBoard=tr.map(function(x){return x.innerText||'';}).join('|').slice(0,5000);",
        " var initial=window.__vuInitialBoard||'';",
        " var initialDate=window.__vuInitialDate||'';",
        " var changed = initialDate===requested || (!!initial && currentBoard!==initial) || (!initial && !!currentBoard);",
        " var found=[],seen={};",
        " function take(time,flight,origin,airline){",
        "   time=norm(time);flight=norm(flight);origin=norm(origin);airline=norm(airline);",
        "   if(!/^([01]?\\d|2[0-3]):[0-5]\\d$/.test(time) || !/^[A-Z0-9]{2,3}\\s?\\d{2,5}$/i.test(flight))return;",
        "   var key=time+'|'+flight; if(seen[key])return;seen[key]=true;",
        "   found.push([time,flight,origin,airline]);",
        " }",
        " tr.forEach(function(row){",
        "   var cells=Array.from(row.querySelectorAll('td,[role=\"cell\"]')).map(function(el){return norm(el.innerText||el.textContent);});",
        "   if(cells.length>=3){",
        "     var t=cells.find(function(x){return /^([01]?\\d|2[0-3]):[0-5]\\d$/.test(x);});",
        "     var codes=cells.map(function(x){return x.match(/\\b[A-Z0-9]{2,3}\\s?\\d{2,5}\\b/i);}).filter(Boolean);",
        "     var flight=codes.length?codes[codes.length-1][0]:'';",
        "     var city=cells.find(function(x){return /\\([A-Z]{3}\\)/.test(x);})||'';",
        "     take(t,flight,city,cells.length>1?cells[1]:'');",
        "   }",
        " });",
        " if(!found.length){",
        "   Array.from(document.querySelectorAll('a')).forEach(function(a){",
        "     var flight=norm(a.innerText);if(!/^[A-Z0-9]{2,3}\\s?\\d{2,5}$/i.test(flight))return;",
        "     var p=a;for(var j=0;j<5 && p.parentElement;j++){",
        "       p=p.parentElement;",
        "       var body=norm(p.innerText);if(body.length>400)break;",
        "       var m=body.match(/\\b([01]?\\d|2[0-3]):[0-5]\\d\\b/);",
        "       var city=body.match(/([A-Za-zÁÉÍÓÚÜÑáéíóúüñ\\s\\-]{3,})\\s*\\(([A-Z]{3})\\)/);",
        "       if(m&&city){take(m[0],flight,city[0],'');break;}",
        "     }",
        "   });",
        " }",
        " return JSON.stringify({date:requested,dateVerified:dateVerified,refreshed:changed,rows:found.slice(0,180)});",
        "})();");

    public static final class Result {
        public final boolean verified;
        public final List<FlightData.Flight> flights;
        public final int sourceRows;
        public final String error;
        private Result(boolean valid,List<FlightData.Flight> f,int rows,String why){
            verified=valid;flights=f;sourceRows=rows;error=why;
        }
    }
    public static Result parse(String wrapped,LocalDate day,LocalTime from) {
        try {
            Object v=new JSONTokener(wrapped).nextValue();
            JSONObject json=v instanceof JSONObject?(JSONObject)v:
                new JSONObject(v instanceof String?(String)v:wrapped);
            boolean dateOk=day.toString().equals(json.optString("date","")) &&
                json.optBoolean("dateVerified",false) &&
                json.optBoolean("refreshed",false);
            if(!dateOk)return new Result(false,Collections.emptyList(),0,"No se pudo verificar la fecha en la fuente de programación.");
            JSONArray rows=json.optJSONArray("rows");
            if(rows==null)return new Result(false,Collections.emptyList(),0,"La fuente no devolvió una tabla legible.");
            ArrayList<FlightData.Flight> result=new ArrayList<>();
            LinkedHashSet<String> seen=new LinkedHashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONArray r=rows.optJSONArray(i);
                if(r==null||r.length()<3)continue;
                String t=r.optString(0),code=r.optString(1),origin=r.optString(2);
                try{
                    LocalTime time=LocalTime.parse(t,DateTimeFormatter.ofPattern("H:mm"));
                    if(time.isBefore(from)||!code.matches("(?i)[A-Z0-9]{2,3}\\s?\\d{2,5}"))continue;
                    if(!seen.add(time+"|"+code))continue;
                    FlightData.Flight f=new FlightData.Flight();
                    f.scheduledMillis=day.atTime(time).atZone(FlightData.UY).toInstant().toEpochMilli();
                    f.flightNo=code; f.origin=origin.isEmpty()?"Procedencia por confirmar":origin;
                    f.airline=r.optString(3,"");f.status="Programado · Fuente externa";
                    result.add(f);
                }catch(Exception ignored){}
            }
            result.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
            return new Result(true,result,rows.length(),rows.length()==0?
               "La fuente no devolvió horarios verificables.":"");
        }catch(Exception e){
            return new Result(false,Collections.emptyList(),0,"No se pudo interpretar la programación.");
        }
    }
    private FutureSchedule(){}
}
