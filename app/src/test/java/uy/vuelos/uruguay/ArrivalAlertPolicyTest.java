package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class ArrivalAlertPolicyTest {
    private final long now=LocalDate.of(2026,11,3).atTime(8,0)
        .atZone(FlightData.UY).toInstant().toEpochMilli();
    private FlightData.Flight flight(long minutesFromNow,String number,String city){
        FlightData.Flight f=new FlightData.Flight();
        f.flightNo=number;f.origin=city;f.scheduledMillis=now+minutesFromNow*60000L;
        return f;
    }
    @Test public void twoAlertsAtFortyAndTwenty(){
        List<ArrivalAlertPolicy.Alert> p=ArrivalAlertPolicy.plan(
            Arrays.asList(flight(60,"CM368","Panamá")),now);
        assertEquals(2,p.size());
        assertEquals(40,p.get(0).stage);assertEquals(20,p.get(1).stage);
        assertEquals(now+20*60000L,p.get(0).deliveryAt);
        assertEquals(now+40*60000L,p.get(1).deliveryAt);
    }
    @Test public void skipExpiredPrepareAndDoNotInventLandings(){
        List<ArrivalAlertPolicy.Alert> p=ArrivalAlertPolicy.plan(
            Arrays.asList(flight(24,"CM368","Panamá")),now);
        assertEquals(1,p.size());assertEquals(20,p.get(0).stage);
        assertTrue(p.get(0).deliveryAt>now);
    }
    @Test public void groupCodesharesIntoSinglePair(){
        List<ArrivalAlertPolicy.Alert> p=ArrivalAlertPolicy.plan(
            Arrays.asList(flight(52,"LA102","Lima"),flight(54,"DL988","Lima")),now);
        assertEquals(2,p.size());assertEquals(2,p.get(0).flights);
        assertEquals(p.get(0).key.replace("_40",""),p.get(1).key.replace("_20",""));
    }
    @Test public void noNotificationsForCanceledOrAlreadyLanded(){
        FlightData.Flight f=flight(50,"AA44","Bogotá"); f.canceled=true;
        FlightData.Flight g=flight(50,"CM55","Panamá"); g.landed=true;
        assertTrue(ArrivalAlertPolicy.plan(Arrays.asList(f,g),now).isEmpty());
    }
    @Test public void noTomorrowOrOldFlights(){
        assertTrue(ArrivalAlertPolicy.plan(Arrays.asList(
            flight(26*60,"AA1","Lima"),flight(-5,"AA2","Lima")),now).isEmpty());
    }
    @Test public void stalenessSuppressesAndNoPostLandingWarnings(){
        assertTrue(ArrivalAlertPolicy.validDelivery(40,now+39*60000L,now,now));
        assertFalse(ArrivalAlertPolicy.validDelivery(40,now+20*60000L,now,now));
        assertTrue(ArrivalAlertPolicy.validDelivery(20,now+12*60000L,now,now));
        assertFalse(ArrivalAlertPolicy.validDelivery(20,now-60000L,now,now));
        assertFalse(ArrivalAlertPolicy.validDelivery(20,now+12*60000L,now-76*60000L,now));
    }
    @Test public void roundedMinutesLegibleAndCorrect(){
        assertEquals("horario vencido",ArrivalAlertPolicy.remaining(now-1000L,now));
        assertEquals("7 min",ArrivalAlertPolicy.remaining(now+6*60000L+1000L,now));
        assertEquals("1 h 1 min",ArrivalAlertPolicy.remaining(now+61*60000L,now));
    }
}