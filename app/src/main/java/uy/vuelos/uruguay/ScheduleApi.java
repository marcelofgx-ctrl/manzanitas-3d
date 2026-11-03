package uy.vuelos.uruguay;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated HTTPS request to a documented free-tier API. No background polling.
 * Requires a user-created RapidAPI key. Never writes keys/response details to logs.
 * Validates day & airport; refuses to claim "no flights" when coverage is unknown.
 */
public final class ScheduleApi {
    private static final String BASE="https://skylink-api.p.rapidapi.com/v3/schedules/arrivals";
    private static final String HOST="skylink-api.p.rapidapi.com";
    private static final ZoneId UY=ZoneId.of("America/Montevideo");
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());

    public interface Callback {
        void complete(Result result);
    }
    public static final class Result {
        public final boolean success;
        public final boolean verifiedEmpty;
        public final String message;
        public final List<FlightData.Flight> flights;
        private Result(boolean ok,boolean empty,String msg,List<FlightData.Flight> rows) {
            success=ok;verifiedEmpty=empty;message=msg;flights=rows;
        }
        static Result failure(String msg){return new Result(false,false,msg,new ArrayList<>());}
    }

    public void fetch(String key,LocalDate date,LocalTime from,Callback callback) {
        if(key==null || key.trim().isEmpty()){
            main.post(()->callback.complete(Result.failure("Falta conectar la fuente gratuita.")));return;
        }
        executor.execute(()->{
            Result result=download(key,date,from);
            main.post(()->callback.complete(result));
        });
    }
    private static Result download(String key,LocalDate day,LocalTime from) {
        String dayParam=day.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        try{
            String url=BASE+"?icao=SUMU&date="+dayParam+"&page=1";
            HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-rapidapi-host",HOST);
                conn.setRequestProperty("x-rapidapi-key",key);
                conn.setRequestProperty("Accept","application/json");
                conn.setConnectTimeout(12000);conn.setReadTimeout(15000);
                conn.setUseCaches(false);
                int status=conn.getResponseCode();
                if(status==401||status==403)
                    return Result.failure("La clave no está autorizada para consultar horarios. Revisá el plan gratuito.");
                if(status==429)
                    return Result.failure("Se alcanzó el límite de consultas de la fuente gratuita.");
                if(status<200||status>=300)
                    return Result.failure("La fuente de programación no está disponible (HTTP "+status+").");
                String json=readUpTo(conn.getInputStream(),1500000);
                return parse(json,day,from);
            }finally{conn.disconnect();}
        }catch(Exception ignored){
            return Result.failure("No se pudo conectar o interpretar la fuente de programación.");
        }
    }
    private static String readUpTo(InputStream stream,int max) throws Exception {
        try(InputStream in=stream){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];
            int total=0,n;
            while((n=in.read(buffer))!=-1){
                total+=n;
                if(total>max)throw new IllegalStateException("Respuesta demasiado grande");
                out.write(buffer,0,n);
            }
            return out.toString("UTF-8");
        }
    }

    public static Result parse(String json,LocalDate requested,LocalTime earliest) {
        try {
            JSONObject root=new JSONObject(json);
            // Fail closed when response has no date/airport or does not match requested.
            if(!"SUMU".equalsIgnoreCase(root.optString("airport","")))
                return Result.failure("No se pudo comprobar que la programación pertenezca a Carrasco.");
            if(!requested.toString().equals(root.optString("date","")))
                return Result.failure("La fuente devolvió vuelos de otra fecha.");
            JSONArray list=root.optJSONArray("flights");
            if(list==null)return Result.failure("La fuente no devolvió una lista de horarios reconocible.");
            ArrayList<FlightData.Flight> flights=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            int sourceCount=list.length();
            for(int i=0;i<list.length();i++){
                JSONObject item=list.optJSONObject(i);
                if(item==null)continue;
                String flightNo=item.optString("flight_number","").trim();
                String timeIso=item.optString("scheduled_time","");
                if(!flightNo.matches("(?i)[A-Z0-9]{2,4}\\s?\\d{1,5}"))continue;
                Instant instant;
                try{instant=Instant.parse(timeIso);}
                catch(DateTimeParseException ignored){
                    try{instant=OffsetDateTime.parse(timeIso).toInstant();}
                    catch(Exception e){continue;}
                }
                ZonedDateTime local=instant.atZone(UY);
                if(!requested.equals(local.toLocalDate()) || local.toLocalTime().isBefore(earliest))continue;
                String dedup=flightNo.replace(" ","").toUpperCase(Locale.ROOT)+"|"+instant.toEpochMilli();
                if(!seen.add(dedup))continue;
                FlightData.Flight f=new FlightData.Flight();
                f.scheduledMillis=instant.toEpochMilli();
                f.origin=item.optString("origin_city","");
                if(f.origin.isEmpty())f.origin=item.optString("origin_airport","Procedencia sin informar");
                f.flightNo=flightNo;
                JSONObject airline=item.optJSONObject("airline");
                f.airline=airline!=null?airline.optString("name",""):"";
                f.status="Programado";
                // Any "estimated" or "actual" status is ignored in FUTURE planning, never trigger alerts.
                f.expectedMillis=0;
                f.landed=false;f.canceled=false;
                flights.add(f);
            }
            flights.sort(Comparator.comparingLong(FlightData.Flight::landingTime));
            int total=root.optInt("total_count",sourceCount);
            if(total>sourceCount)
                return Result.failure("La fuente tiene resultados paginados. No se mostrará una lista incompleta.");
            if(sourceCount>0 && flights.isEmpty()){
                return new Result(true,false,
                    "No se identificaron arribos desde la hora indicada dentro de la respuesta verificada.",flights);
            }
            if(sourceCount==0)return new Result(true,true,
                "La fuente no informó vuelos para esa fecha. Comprobá también el panel oficial.",flights);
            return new Result(true,false,"",flights);
        }catch(Exception ignored){
            return Result.failure("El formato de la fuente de programación no es compatible.");
        }
    }
    public void close(){executor.shutdownNow();}
}
