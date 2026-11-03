package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;

public final class StreetNamePolicyTest {
    @Test public void actualStreetNamesRetained(){
        assertEquals("Av. Italia",StreetNamePolicy.clean(" Av. Italia "));
        assertEquals("Avenida 18 de Julio",StreetNamePolicy.clean("Avenida 18 de Julio"));
    }
    @Test public void noHouseNumberIsShownAsStreet(){
        assertEquals("",StreetNamePolicy.clean("1234"));
        assertEquals("",StreetNamePolicy.clean(" 25 - "));
        assertEquals("",StreetNamePolicy.clean(null));
    }
    @Test public void controlsWhitespaceAndLongValuesAreSafe(){
        assertEquals("Camino Carrasco",StreetNamePolicy.clean("\nCamino    Carrasco\t"));
        assertTrue(StreetNamePolicy.clean("A".repeat(100)).length()<=70);
    }
}
