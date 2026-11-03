package uy.vuelos.uruguay;

/** Pure gesture policy for the compact flight-dashboard status drawer. */
public final class FooterSheetPolicy {
    public static boolean isExpandedAfterGesture(boolean expanded,float deltaY,float threshold){
        float min=Math.max(0f,threshold);
        if(deltaY < -min)return true;   // swipe up to open
        if(deltaY > min)return false;   // swipe down to close
        return expanded;                // tap is handled by the accessible handle
    }
    private FooterSheetPolicy(){}
}
