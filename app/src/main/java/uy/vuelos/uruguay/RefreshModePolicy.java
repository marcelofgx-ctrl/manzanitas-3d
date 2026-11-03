package uy.vuelos.uruguay;

/** Pure policy: live today can refresh and focus; manual date selection never silently reloads or jumps. */
public final class RefreshModePolicy {
    public static boolean shouldReload(boolean foreground, boolean autoEnabled,
                                       boolean manualMode, boolean selectedFutureDate) {
        return foreground && autoEnabled && !manualMode && !selectedFutureDate;
    }

    public static boolean shouldAutoFocus(boolean liveMode, boolean priorityVisible,
                                          long elapsedNow, long lastUserInteraction) {
        if (!liveMode || !priorityVisible) return false;
        if (lastUserInteraction <= 0L) return true;
        return elapsedNow - lastUserInteraction > 45000L;
    }
    private RefreshModePolicy() {}
}
