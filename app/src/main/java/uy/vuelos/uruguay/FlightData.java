package uy.vuelos.uruguay;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carefully parses flight rows from the publicly embeddable Avionio board.
 * Never converts an empty or unreadable response into an assertion that there are no flights.
 */
public final class FlightData {
    public static final ZoneId UY = ZoneId.of("America/Montevideo");
    public static final long MINUTE = 60000L;
    private static final String[] MONTHS = {"ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"};

    public static final String EXTRACT_JS = String.join("\n",
        "(function(){",
        " function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}",
        " function isTime(t){return /^\\d{1,2}:\\d{2}$/.test(norm(t));}",
        " var rows=[], seen={};",
        " function take(bits){",
        "   if(bits.length<7) return;",
        "   var b=bits.map(norm);",
        "   if(!isTime(b[0]) || !/^\\d{1,2}\\s+[a-záéíóú]{3,}/i.test(b[1]) || !/^[a-z0-9]{2,4}\\d{1,5}$/i.test(b[4]))return;",
        "   var key=b.slice(0,7).join('|');if(seen[key])return;seen[key]=true;rows.push(b.slice(0,7));",
        " }",
        " var nodes=document.querySelectorAll('table tr,[role=\"row\"],[class*=\"flight-row\"],[class*=\"flight-item\"],[class*=\"board-row\"]');",
        " for(var i=0;i<nodes.length;i++){",
        "   var cs=Array.from(nodes[i].querySelectorAll('td,th,[role=\"cell\"]')).map(function(el){return norm(el.innerText||el.textContent);});",
        "   if(cs.length>=7){take(cs);continue;}",
        "   var raw=String(nodes[i].innerText||'').trim();",
        "   if(raw){var lines=raw.split(/[\\n\\t]+/).map(norm).filter(Boolean);if(lines.length>=7)take(lines);}",
        " }",
        " // Some mobile layouts use a grid instead of table rows.",
        " if(rows.length===0){",
        "   var list=document.querySelectorAll('tbody>tr,.arrival-row,.departure-row,.flight-row');",
        "   for(var j=0;j<list.length;j++){",
        "     var bits=Array.from(list[j].children).map(function(c){return norm(c.innerText||c.textContent);});",
        "     if(bits.length>=7)take(bits);",
        "   }",
        " }",
        " function dateVal(v){",
        "   if(!v)return null;",
        "   var m=String(v).match(/\\b(20\\d{2})[-\\/.](\\d{1,2})[-\\/.](\\d{1,2})\\b/);",
        "   if(m)return m[1]+'-'+('0'+m[2]).slice(-2)+'-'+('0'+m[3]).slice(-2);",
        "   var names=['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic'];",
        "   var n=String(v).toLowerCase().match(/\\b(\\d{1,2})\\s+([a-záéíóú]{3,})\\s+(20\\d{2})\\b/);",
        "   if(n){var mo=names.indexOf(n[2].slice(0,3));if(mo>=0)return n[3]+'-'+('0'+(mo+1)).slice(-2)+'-'+('0'+n[1]).slice(-2);}",
        "   return null;",
        " }",
        " var selected=null;",
        " var inp=document.querySelectorAll('input[type=\"date\"],input[aria-label*=\"fecha\"],input,select');",
        " for(var k=0;k<inp.length;k++){selected=dateVal(inp[k].value);if(selected)break;}",
        " if(!selected){",
        "   var active=document.querySelectorAll('[aria-selected=\"true\"],[aria-current=\"date\"],.selected-date,.active-date');",
        "   for(var q=0;q<active.length;q++){selected=dateVal(active[q].innerText);if(selected)break;}",
        " }",
        " var text=String(document.body?document.body.innerText:'').toLowerCase();",
        " return JSON.stringify({rows:rows,selectedDate:selected,sourceEmpty:rows.length===0 && /(sin vuelos|no flights|no hay vuelos)/i.test(text),hasBoard:!!document.querySelector('table,[role=\"table\"],.flight-list,.flight-table')});",
        "})();");

    public static final class Flight {
        public String origin="", flightNo="", airline="", status="";
        public long scheduledMillis, expectedMillis;
        public boolean landed, canceled;
        public int codeshares=1;
        public long landingTime(){return expectedMillis>0?expectedMillis:scheduledMillis;}
        public boolean upcoming(long now){return !landed && !canceled && landingTime()>=now-20*MINUTE;}
        public String etaLabel(){
            return DateTimeFormatter.ofPattern("HH:mm").format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(landingTime()),UY));
        }
        public LocalDate day(){
            return Instant.ofEpochMilli(scheduledMillis).atZone(UY).toLocalDate();
        }
    }
    public static final class BoardSnapshot {
        public final List<Flight> flights;
        public final LocalDate selectedDate;
        public final boolean sourceEmpty;
        public final boolean parsed;
        public final int rawRows;
        private BoardSnapshot(List<Flight> f,LocalDate d,boolean empty,boolean ok,int count){
            flights=f;selectedDate=d;sourceEmpty=empty;parsed=ok;rawRows=count;
        }
        public boolean sourceUnavailable(){return !parsed && !sourceEmpty;}
    }

    private static String normalize(String s){
        if(s==null)return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("\\s+"," ");
    }
    private static long epoch(String dayMonth,String time,LocalDate reference){
        try{
            Matcher m=Pattern.compile("^(\\d{1,2})\\s+([\\p{L}]{3,})").matcher(normalize(dayMonth));
            if(!m.find()||!time.matches("\\d{1,2}:\\d{2}"))return -1;
            int day=Integer.parseInt(m.group(1)), month=-1;
            for(int i=0;i<MONTHS.length;i++)if(m.group(2).startsWith(MONTHS[i])){month=i+1;break;}
            if(month<1)return -1;
            LocalDate date=LocalDate.of(reference.getYear(),month,day);
            if(date.isAfter(reference.plusDays(200)))date=date.minusYears(1);
            else if(date.isBefore(reference.minusDays(200)))date=date.plusYears(1);
            String[] parts=time.split(":");
            int hh=Integer.parseInt(parts[0]),mm=Integer.parseInt(parts[1]);
            return date.atTime(hh,mm).atZone(UY).toInstant().toEpochMilli();
        }catch(Exception e){return -1;}
    }
    private static long estimate(String status,String day,LocalDate ref,long schedule){
        String s=normalize(status);
        if(!(s.contains("estimad")||s.contains("previst")||s.contains("estimated")))return -1;
        Matcher m=Pattern.compile("(\\d{1,2}:\\d{2})").matcher(status);
        if(!m.find())return -1;
        long result=epoch(day,m.group(1),ref);
        if(result<0)return -1;
        if(result<schedule-12*60*MINUTE)result+=24*60*MINUTE;
        else if(result>schedule+12*60*MINUTE)result-=24*60*MINUTE;
        return result;
    }

    /** Parse WebView.evaluateJavascript return (JSON escaped string wrapping a JSON object).
     * Date from the site takes precedence over the phone date when the user selects tomorrow.
     */
    public static BoardSnapshot parseBoardJs(String wrapped,long nowMillis){
        LocalDate phoneDay=Instant.ofEpochMilli(nowMillis).atZone(UY).toLocalDate();
        try{
            Object decoded=new JSONTokener(wrapped).nextValue();
            JSONObject json=decoded instanceof JSONObject?(JSONObject)decoded
                :new JSONObject(decoded instanceof String?(String)decoded:wrapped);
            JSONArray rows=json.optJSONArray("rows");
            if(rows==null)return new BoardSnapshot(Collections.emptyList(),phoneDay,false,false,0);
            LocalDate selected=phoneDay;
            String date=json.optString("selectedDate","");
            if(!date.isEmpty()){
                try{selected=LocalDate.parse(date);}catch(Exception ignored){}
            }
            List<Flight> all=parseRows(rows,nowMillis,selected);
            // When the provider supplies a selected date, never label older cached rows as tomorrow's arrivals.
            ArrayList<Flight> filtered=new ArrayList<>();
            for(Flight f:all)if(f.day().equals(selected))filtered.add(f);
            if(date.isEmpty() && !all.isEmpty()){
                // If date is not readable, use the most common date in the returned rows,
                // not always the phone's current date.
                java.util.HashMap<LocalDate,Integer> counts=new java.util.HashMap<>();
                for(Flight f:all)counts.put(f.day(),counts.getOrDefault(f.day(),0)+1);
                LocalDate best=selected;int max=-1;
                for(java.util.Map.Entry<LocalDate,Integer> e:counts.entrySet())
                    if(e.getValue()>max){best=e.getKey();max=e.getValue();}
                selected=best;
                filtered.clear();
                for(Flight f:all)if(f.day().equals(selected))filtered.add(f);
            }
            boolean sourceEmpty=json.optBoolean("sourceEmpty",false) && rows.length()==0;
            // A 0-row parse without the source explicitly saying empty is NOT evidence of no flights.
            boolean parsed=!filtered.isEmpty()||sourceEmpty;
            return new BoardSnapshot(filtered,selected,sourceEmpty,parsed,rows.length());
        }catch(Exception e){
            return new BoardSnapshot(Collections.emptyList(),phoneDay,false,false,0);
        }
    }
    public static List<Flight> parseJsResult(String wrapped,long nowMillis){
        return parseBoardJs(wrapped,nowMillis).flights;
    }
    public static List<Flight> parseRows(JSONArray rows,long nowMillis,LocalDate reference){
        LinkedHashMap<String,Flight> physical=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONArray r=rows.optJSONArray(i);
            if(r==null||r.length()<7)continue;
            String time=r.optString(0).trim(), day=r.optString(1).trim();
            String iata=r.optString(2).trim(),origin=r.optString(3).trim();
            String flightNo=r.optString(4).trim(), airline=r.optString(5).trim(),state=r.optString(6).trim();
            if(origin.length()<2||flightNo.length()<2)continue;
            long scheduled=epoch(day,time,reference);
            if(scheduled<0)continue;
            long estimated=estimate(state,day,reference,scheduled);
            String normalized=normalize(state);
            String key=scheduled+"|"+normalize(iata)+"|"+normalize(origin);
            Flight found=physical.get(key);
            boolean landed=normalized.contains("aterriz")||normalized.contains("landed");
            boolean canceled=normalized.contains("cancel");
            if(found==null){
                found=new Flight();found.origin=origin;found.flightNo=flightNo;
                found.airline=airline;found.status=state;found.scheduledMillis=scheduled;
                found.expectedMillis=estimated;found.landed=landed;found.canceled=canceled;
                physical.put(key,found);
            }else{
                found.codeshares++;
                if(estimated>0 && found.expectedMillis<=0){found.expectedMillis=estimated;found.status=state;}
                if(landed){found.landed=true;found.status=state;}
                if(canceled){found.canceled=true;found.status=state;}
            }
        }
        ArrayList<Flight> out=new ArrayList<>(physical.values());
        out.sort(Comparator.comparingLong(Flight::landingTime));
        return out;
    }
    public static String countdown(long arrival,long now){
        long minutes=Math.max(0,(arrival-now+MINUTE-1)/MINUTE);
        if(minutes<60)return "EN "+minutes+" MIN";
        return "EN "+minutes/60+" H "+minutes%60+" MIN";
    }
    private FlightData(){}
}
