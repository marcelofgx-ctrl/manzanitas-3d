package uy.vuelos.uruguay;
/** Only use the old tile for a fresh fix if all roads within matching distance
 * would be covered by the dataset's 48e-5 degree segment-overlap margin.
 */
public final class OfflineTilePolicy {
    public static boolean withinSafeLoadedMargin(int latCell,int lonCell,
                                                  double lat,double lon){
        if(latCell==Integer.MAX_VALUE||lonCell==Integer.MAX_VALUE ||
           !Double.isFinite(lat)||!Double.isFinite(lon))return false;
        double south=latCell/100.0,west=lonCell/100.0;
        double inset=0.00011; // ~10-12m; leaves >30m of builder's OSM margin
        return lat>=south-inset&&lat<south+0.01+inset&&
               lon>=west-inset&&lon<west+0.01+inset;
    }
    private OfflineTilePolicy(){}
}
