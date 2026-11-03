package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ScheduleApiTest {
    private final LocalDate future=LocalDate.of(2026,11,4);
    private String row(String iso,String code,String city){
        return "{\"flight_number\":\""+code+"\",\"scheduled_time\":\""+iso+"\",\"origin_city\":\""+city+"\",\"airline\":{\"name\":\"Test\"}}";
    }
    private String board(String airport,String date,String flights){
        return "{\"airport\":\""+airport+"\",\"date\":\""+date+"\",\"total_count\":"+flights.split("flight_number",-1).length+",\"flights\":["+flights+"]}";
    }
    @Test public void correctDayTimeInclusive07(){
        String payload="{\"airport\":\"SUMU\",\"date\":\"2026-11-04\",\"total_count\":3,\"flights\":["+
            row("2026-11-04T09:50:00Z","AB101","Lima")+","+
            row("2026-11-04T10:00:00Z","AB102","Buenos Aires")+","+
            row("2026-11-04T11:30:00Z","AB103","Panama")+"]}";
        ScheduleApi.Result result=ScheduleApi.parse(payload,future,LocalTime.of(7,0));
        assertTrue(result.success);
        assertEquals(2,result.flights.size()); // 10:00 UTC = 07:00 Uruguay.
        assertEquals("AB102",result.flights.get(0).flightNo);
    }
    @Test public void noWrongDayEvenIfFlightsProvided(){
        String payload="{\"airport\":\"SUMU\",\"date\":\"2026-11-03\",\"flights\":["+
            row("2026-11-04T10:00:00Z","AB102","Panama")+"]}";
        ScheduleApi.Result result=ScheduleApi.parse(payload,future,LocalTime.of(7,0));
        assertFalse(result.success);assertEquals(0,result.flights.size());
    }
    @Test public void noWrongAirport(){
        String payload="{\"airport\":\"SAEZ\",\"date\":\"2026-11-04\",\"flights\":[]}";
        assertFalse(ScheduleApi.parse(payload,future,LocalTime.of(7,0)).success);
    }
    @Test public void futureOnlyScheduledNoFalseLanded(){
        String payload="{\"airport\":\"SUMU\",\"date\":\"2026-11-04\",\"flights\":["+
            row("2026-11-04T13:00:00Z","AA103","Bogota")+"]}";
        ScheduleApi.Result result=ScheduleApi.parse(payload,future,LocalTime.of(7,0));
        assertEquals(1,result.flights.size());
        assertFalse(result.flights.get(0).landed);
        assertEquals("10:00",result.flights.get(0).etaLabel());
    }
    @Test public void unverifiedEmptyNotMisreported(){
        assertFalse(ScheduleApi.parse("{\"date\":\"2026-11-04\",\"flights\":[]}",
            future,LocalTime.of(7,0)).success);
        assertTrue(ScheduleApi.parse("{\"airport\":\"SUMU\",\"date\":\"2026-11-04\",\"flights\":[]}",
            future,LocalTime.of(7,0)).verifiedEmpty);
    }
    @Test public void noUnknownDateFormat(){
        String payload="{\"airport\":\"SUMU\",\"date\":\"2026-11-04\",\"flights\":[{\"flight_number\":\"AA1\",\"scheduled_time\":\"10:30\"}]}";
        ScheduleApi.Result result=ScheduleApi.parse(payload,future,LocalTime.of(7,0));
        assertTrue(result.success);assertEquals(0,result.flights.size());
    }
}
