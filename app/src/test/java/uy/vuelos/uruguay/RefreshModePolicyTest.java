package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;

public class RefreshModePolicyTest {
    @Test public void liveForegroundCanRefresh() {
        assertTrue(RefreshModePolicy.shouldReload(true,true,false,false));
    }
    @Test public void manualDateNeverRefreshesWhenAutoOn() {
        assertFalse(RefreshModePolicy.shouldReload(true,true,true,false));
    }
    @Test public void unknownDateStillPreservedByManualLock() {
        assertFalse(RefreshModePolicy.shouldReload(true,true,true,false));
    }
    @Test public void providerNextDayCannotStopLiveRefresh() {
        assertTrue(RefreshModePolicy.shouldReload(true,true,false,true));
    }
    @Test public void explicitManualTomorrowStillPausesAuto() {
        assertFalse(RefreshModePolicy.shouldReload(true,true,true,true));
    }
    @Test public void providerDateOnlyChangesSourceMetadataNotControlPolicy() {
        assertEquals(RefreshModePolicy.shouldReload(true,true,false,false),
                     RefreshModePolicy.shouldReload(true,true,false,true));
    }
    @Test public void pauseAppAndUserDisabledAutoStopRefresh() {
        assertFalse(RefreshModePolicy.shouldReload(false,true,false,false));
        assertFalse(RefreshModePolicy.shouldReload(true,false,false,false));
    }
    @Test public void scrollingNotInterruptedUntilIdle() {
        assertFalse(RefreshModePolicy.shouldAutoFocus(true,true,100000L,85000L));
        assertFalse(RefreshModePolicy.shouldAutoFocus(true,true,120000L,85000L));
        assertTrue(RefreshModePolicy.shouldAutoFocus(true,true,131000L,85000L));
    }
    @Test public void manualModeNeverAutoScrolls() {
        assertFalse(RefreshModePolicy.shouldAutoFocus(false,true,131000L,85000L));
        assertFalse(RefreshModePolicy.shouldAutoFocus(true,false,131000L,85000L));
    }
}
