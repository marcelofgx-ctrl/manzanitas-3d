package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
public final class OfflineTilePolicyTest {
    @Test public void minorBoundaryHandoffCanUseOldMapToAvoidBlink(){
        int latCell=(int)Math.floor(-34.895*100), lonCell=(int)Math.floor(-56.145*100);
        assertTrue(OfflineTilePolicy.withinSafeLoadedMargin(latCell,lonCell,-34.895,-56.145));
        assertTrue(OfflineTilePolicy.withinSafeLoadedMargin(latCell,lonCell,
            latCell/100.0-0.00007,-56.145));
    }
    @Test public void movingFarIntoNextCellCannotKeepOldRoadLimit(){
        int latCell=(int)Math.floor(-34.895*100),lonCell=(int)Math.floor(-56.145*100);
        assertFalse(OfflineTilePolicy.withinSafeLoadedMargin(latCell,lonCell,
            latCell/100.0-0.00035,-56.145));
        assertFalse(OfflineTilePolicy.withinSafeLoadedMargin(latCell,lonCell,
            -34.895,lonCell/100.0+0.0104));
    }
    @Test public void noTileCannotConfirmSpeed(){
        assertFalse(OfflineTilePolicy.withinSafeLoadedMargin(Integer.MAX_VALUE,
            Integer.MAX_VALUE,-34.895,-56.145));
    }
}
