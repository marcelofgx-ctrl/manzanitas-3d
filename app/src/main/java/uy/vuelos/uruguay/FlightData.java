package uy.vuelos.uruguay;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

/** Arrival rows read from the publicly embeddable Avionio widget in our own WebView.
 * Parsing fails closed: it never invents an arrival or time when the source structure changes.
 */
public final class FlightData {
    public static final ZoneId UY = ZoneId.of("America/Montevideo");
    public static final long MINUTE = 60000L;
    private static final String[] MONTHS = {"ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"};

    public static final String EXTRACT_JS = String.join("\n",
        "(function () {",
        "  function norm(t) { return (t || '').replace(/\\s+/g,' ').trim(); }",
        "  var out = [];",
        "  var rows = Array.from(document.querySelectorAll('table tr, [role=\"row\"]'));",
        "  for (var i = 0; i < rows.length; i++) {",
        "    var cells = Array.from(rows[i].querySelectorAll('td,[role=\"cell\"]')).map(function(x){return norm(x.innerText || x.textContent);});",
        "    if (cells.length >= 7 && /^\\d{1,2}:\\d{2}$/.test(cells[0])) out.push(cells.slice(0,7));",
        "  }",
        "  if (out.length === 0) {",
        "    var fallback = Array.from(document.querySelectorAll('[class*=\"flight-row\"], [class*=\"flight-item\"], [class*=\"board-row\"]'));",
        "    for(var j=0;j<fallback.length;j++){",
        "      var bits=Array.from(fallback[j].children).map(function(x){return norm(x.innerText || x.textContent);});",
        "      if(bits.length >= 7 && /^\\d{1,2}:\\d{2}$/.test(bits[0])) out.push(bits.slice(0,7));",
        "    }",
        "  }",
        "  return JSON.stringify(out.slice(0,300));",
        "})();");

    public static final class Flight {
        public String origin, flightNo, airline, status;
        public long scheduledMillis, expectedMillis;
        public boolean landed, canceled;
        public int codeshares = 1;

        public long landingTime() { return expectedMillis > 0 ? expectedMillis : scheduledMillis; }
        public boolean upcoming(long now) {
            return !landed && !canceled && landingTime() >= now - 20 * MINUTE;
        }
        public String etaLabel() {
            return DateTimeFormatter.ofPattern("HH:mm").format(
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(landingTime()), UY));
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }

    private static long flightEpoch(String dayMonth, String hhmm, LocalDate now) {
        try {
            String[] day = norm(dayMonth).split("[\\s\\-]+");
            if (day.length < 2) return -1;
            int d = Integer.parseInt(day[0]);
            int m = -1;
            for (int i=0;i<MONTHS.length;i++) if (day[1].startsWith(MONTHS[i])) { m=i+1; break; }
            if (m < 1 || !hhmm.matches("\\d{1,2}:\\d{2}")) return -1;
            String[] hm=hhmm.split(":");
            LocalDate date = LocalDate.of(now.getYear(),m,d);
            if (date.isAfter(now.plusDays(200))) date=date.minusYears(1);
            else if (date.isBefore(now.minusDays(200))) date=date.plusYears(1);
            return date.atTime(Integer.parseInt(hm[0]),Integer.parseInt(hm[1]))
                .atZone(UY).toInstant().toEpochMilli();
        } catch (Exception ignored) { return -1; }
    }

    private static String inferredEstimate(String status) {
        String s = norm(status);
        if (!(s.contains("estimad") || s.contains("previst") || s.contains("estimated"))) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(status);
        return m.find()?m.group(1):null;
    }

    /** Android evaluateJavascript wraps the returned JS string in a JSON string. */
    public static List<Flight> parseJsResult(String wrapped, long nowMillis) {
        try {
            Object parsed = new JSONTokener(wrapped).nextValue();
            String raw = parsed instanceof String ? (String) parsed : wrapped;
            return parseRows(new JSONArray(raw),nowMillis);
        } catch (Exception ignored) { return Collections.emptyList(); }
    }

    public static List<Flight> parseRows(JSONArray rows,long nowMillis) {
        LinkedHashMap<String,Flight> physicalFlights = new LinkedHashMap<>();
        LocalDate today = java.time.Instant.ofEpochMilli(nowMillis).atZone(UY).toLocalDate();
        for (int i=0; i<rows.length(); i++) {
            try {
                JSONArray r=rows.optJSONArray(i);
                if (r==null || r.length()<7) continue;
                String scheduledTime=r.optString(0).trim();
                String day=r.optString(1).trim();
                String origin=r.optString(3).trim();
                String number=r.optString(4).trim();
                String airline=r.optString(5).trim();
                String state=r.optString(6).trim();
                if (origin.length()<2 || number.length()<2) continue;
                long schedule=flightEpoch(day,scheduledTime,today);
                if(schedule<=0) continue; // Without a reliable date/time, do not alert.
                String stateNorm=norm(state);
                boolean landed=stateNorm.contains("aterriz") || stateNorm.contains("landed");
                boolean canceled=stateNorm.contains("cancel");
                long expected=0;
                String estimate=inferredEstimate(state);
                if (estimate!=null) expected=flightEpoch(day,estimate,today);
                String key=schedule+"|"+norm(origin);
                Flight found=physicalFlights.get(key);
                if(found==null){
                    found=new Flight();
                    found.origin=origin; found.flightNo=number; found.airline=airline;
                    found.status=state; found.scheduledMillis=schedule;
                    found.expectedMillis=expected; found.landed=landed;
                    found.canceled=canceled;
                    physicalFlights.put(key,found);
                } else {
                    found.codeshares++;
                    if(expected>0 && found.expectedMillis<=0){
                        found.expectedMillis=expected; found.status=state;
                    }
                    if(landed){found.landed=true; found.status=state;}
                    if(canceled){found.canceled=true; found.status=state;}
                }
            } catch (Exception ignored) {}
        }
        ArrayList<Flight> result=new ArrayList<>(physicalFlights.values());
        result.sort(Comparator.comparingLong(Flight::landingTime));
        return result;
    }

    public static String countdown(long flightTime,long now) {
        long mins=Math.max(0,(flightTime-now+MINUTE-1)/MINUTE);
        if(mins<60) return "EN " + mins + " MIN";
        return "EN "+(mins/60)+" H "+(mins%60)+" MIN";
    }

    private FlightData(){}
}
