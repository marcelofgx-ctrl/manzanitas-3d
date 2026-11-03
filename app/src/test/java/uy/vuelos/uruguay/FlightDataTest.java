package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;

public final class FlightDataTest {
    private final long now=LocalDate.of(2026,11,3).atTime(6,33)
        .atZone(FlightData.UY).toInstant().toEpochMilli();

    private FlightData.BoardSnapshot read(String json) {
        return FlightData.parseBoardJs(JSONObject.quote(json),now);
    }

    @Test public void tomorrowDateDoesNotGetRemovedByTodayFilter() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"sourceEmpty\":false,\"rows\":["+
            "[\"06:10\",\"04 nov\",\"BOG\",\"Bogotá\",\"AV109\",\"Avianca\",\"Programado\"],"+
            "[\"06:40\",\"04 nov\",\"LIM\",\"Lima\",\"LA2398\",\"LATAM\",\"Estimado 06:06\"],"+
            "[\"06:40\",\"04 nov\",\"LIM\",\"Lima\",\"DL6201\",\"Delta\",\"Estimado 06:06\"],"+
            "[\"07:21\",\"04 nov\",\"PTY\",\"Panamá\",\"CM368\",\"Copa\",\"Programado\"]]}");
        assertEquals(LocalDate.of(2026,11,4),b.selectedDate);
        assertTrue(b.parsed);
        assertEquals(3,b.flights.size()); // two codeshares from Lima => one physical flight
        assertEquals("Lima",b.flights.get(0).origin); // estimated 06:06, before Bogotá 06:10
        assertEquals("Bogotá",b.flights.get(1).origin);
        assertEquals(2,b.flights.get(0).codeshares);
        assertEquals("06:06",b.flights.get(0).etaLabel());
        assertEquals(LocalDate.of(2026,11,4),b.flights.get(0).day());
    }

    @Test public void emptySourceIsNotAClaimOfNoScheduledFlights() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"rows\":[],\"sourceEmpty\":true}");
        assertTrue(b.sourceEmpty);
        assertTrue(b.parsed);
        assertTrue(b.flights.isEmpty());
        assertEquals(LocalDate.of(2026,11,4),b.selectedDate);
    }

    @Test public void unrecognizedSourceMustNotBeRepresentedAsZeroFlights() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"rows\":[],\"sourceEmpty\":false}");
        assertFalse(b.parsed);
        assertTrue(b.sourceUnavailable());
    }

    @Test public void oldCachedRowsCannotBeLabelledAsTomorrow() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"rows\":["+
            "[\"06:10\",\"03 nov\",\"BOG\",\"Bogotá\",\"AV109\",\"Avianca\",\"Programado\"]],\"sourceEmpty\":false}");
        assertFalse(b.parsed);
        assertTrue(b.flights.isEmpty());
        assertEquals(LocalDate.of(2026,11,4),b.selectedDate);
    }

    @Test public void dateInRowsIsUsedWhenWidgetDoesNotExposeDateInput() {
        FlightData.BoardSnapshot b=read("{\"rows\":["+
            "[\"06:10\",\"04 nov\",\"BOG\",\"Bogotá\",\"AV109\",\"Avianca\",\"Programado\"]],\"sourceEmpty\":false}");
        assertEquals(LocalDate.of(2026,11,4),b.selectedDate);
        assertEquals(1,b.flights.size());
    }

    @Test public void estimatesAfterMidnightAreNotMovedToYesterday() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"rows\":["+
            "[\"23:55\",\"04 nov\",\"EZE\",\"Buenos Aires\",\"IB2604\",\"Iberia\",\"Estimado 00:44\"]],\"sourceEmpty\":false}");
        assertEquals(1,b.flights.size());
        assertEquals("00:44",b.flights.get(0).etaLabel());
        assertEquals(LocalDate.of(2026,11,4),b.flights.get(0).day());
        assertEquals(LocalDate.of(2026,11,5),
            Instant.ofEpochMilli(b.flights.get(0).landingTime()).atZone(FlightData.UY).toLocalDate());
    }


    @Test public void overdueEstimateDoesNotMeanLanded() {
        long scheduled=LocalDate.of(2026,11,3).atTime(7,7).atZone(FlightData.UY).toInstant().toEpochMilli();
        long clock=LocalDate.of(2026,11,3).atTime(7,14).atZone(FlightData.UY).toInstant().toEpochMilli();
        assertEquals("SIN CONFIRMAR",FlightData.countdown(scheduled,clock));
        FlightData.BoardSnapshot b=FlightData.parseBoardJs(JSONObject.quote(
            "{\"selectedDate\":\"2026-11-03\",\"rows\":["+
            "[\"07:07\",\"03 nov\",\"PTY\",\"Ciudad de Panamá\",\"CM368\",\"Copa\",\"Estimado 07:07\"]],\"sourceEmpty\":false}"),clock);
        assertEquals(1,b.flights.size());
        assertFalse(b.flights.get(0).landed);
    }
    @Test public void countdownDoesNotRoundPastToZero() {
        long now=LocalDate.of(2026,11,3).atTime(7,14).atZone(FlightData.UY).toInstant().toEpochMilli();
        assertEquals("AHORA",FlightData.countdown(now,now));
        assertEquals("EN 7 MIN",FlightData.countdown(now+6*60000L+1000L,now));
        assertEquals("SIN CONFIRMAR",FlightData.countdown(now-1000L,now));
    }

    @Test public void canceledFlightsAreFlaggedAndDoNotCountAsUpcoming() {
        FlightData.BoardSnapshot b=read("{\"selectedDate\":\"2026-11-04\",\"rows\":["+
            "[\"06:10\",\"04 nov\",\"BOG\",\"Bogotá\",\"AV109\",\"Avianca\",\"Cancelado\"]],\"sourceEmpty\":false}");
        assertTrue(b.flights.get(0).canceled);
        assertFalse(b.flights.get(0).upcoming(now));
    }
}
