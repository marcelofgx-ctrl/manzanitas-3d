package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;

public class FutureScheduleTest {
  private final LocalDate day=LocalDate.of(2026,11,4);
  private String payload(boolean ok,String rows){
    String src="{\"date\":\"2026-11-04\",\"dateVerified\":"+ok+
       ",\"refreshed\":"+ok+",\"rows\":"+rows+"}";
    return JSONObject.quote(src);
  }
  @Test public void inclusiveSevenOClockShowsAllLaterFlights(){
    FutureSchedule.Result r=FutureSchedule.parse(payload(true,
      "[[\"06:40\",\"LA2398\",\"Lima\",\"LATAM\"],"+
      "[\"07:21\",\"CM368\",\"Panamá\",\"Copa\"],"+
      "[\"08:50\",\"AR1382\",\"Buenos Aires\",\"Aerolíneas\"],"+
      "[\"10:53\",\"LA406\",\"Santiago\",\"LATAM\"]]"),day,LocalTime.of(7,0));
    assertTrue(r.verified);assertEquals(3,r.flights.size());
    assertEquals("CM368",r.flights.get(0).flightNo);
    assertEquals("LA406",r.flights.get(2).flightNo);
  }
  @Test public void exactSevenIncludedAndPreviousExcluded(){
    FutureSchedule.Result r=FutureSchedule.parse(payload(true,
      "[[\"06:59\",\"AA100\",\"Lima\",\"\"],[\"07:00\",\"AA101\",\"Lima\",\"\"],[\"07:01\",\"AA102\",\"Lima\",\"\"]]"),day,LocalTime.of(7,0));
    assertEquals(2,r.flights.size());assertEquals("AA101",r.flights.get(0).flightNo);
  }
  @Test public void unverifiedDateCannotShowOtherDaysSchedule(){
    FutureSchedule.Result r=FutureSchedule.parse(payload(false,
      "[[\"07:21\",\"CM368\",\"Panamá\",\"Copa\"]]"),day,LocalTime.of(7,0));
    assertFalse(r.verified);assertEquals(0,r.flights.size());
  }
  @Test public void sameDateEmptyRowsDoesNotClaimAirportIsEmpty(){
    FutureSchedule.Result r=FutureSchedule.parse(payload(true,"[]"),day,LocalTime.of(7,0));
    assertTrue(r.verified);assertEquals(0,r.flights.size());
    assertTrue(r.error.contains("fuente"));
  }
  @Test public void sortAndRemoveDuplicateFlightNumbers(){
    FutureSchedule.Result r=FutureSchedule.parse(payload(true,
      "[[\"10:20\",\"AR1382\",\"Buenos Aires\",\"Aerolíneas\"],"+
      "[\"07:21\",\"CM368\",\"Panamá\",\"Copa\"],"+
      "[\"07:21\",\"CM368\",\"Panamá\",\"Copa\"]]"),day,LocalTime.of(7,0));
    assertEquals(2,r.flights.size());assertEquals("CM368",r.flights.get(0).flightNo);
  }
}