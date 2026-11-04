package uy.vuelos.uruguay;

/** Pure policy: explicit manual date mode pauses refresh. A provider date rollover cannot. */
public final class RefreshModePolicy {
    public static boolean shouldReload(boolean foreground, boolean autoEnabled,
                                       boolean manualMode, boolean selectedFutureDate) {
        // selectedFutureDate is provider metadata; it must never force manual mode.
        return foreground && autoEnabled && !manualMode;
    }

    public static boolean shouldAutoFocus(boolean liveMode, boolean priorityVisible,
                                          long elapsedNow, long lastUserInteraction) {
        if (!liveMode || !priorityVisible) return false;
        if (lastUserInteraction <= 0L) return true;
        return elapsedNow - lastUserInteraction > 45000L;
    }
    private RefreshModePolicy() {}
}
