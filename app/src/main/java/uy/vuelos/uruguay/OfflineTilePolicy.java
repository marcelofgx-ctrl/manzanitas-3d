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
    /** Signed integers survive negative Uruguay coordinates without collisions. */
    public static int cell(double degrees){
        return (int)Math.floor(degrees*100.0);
    }
    public static long key(int latitudeCell,int longitudeCell){
        return (((long)latitudeCell)<<32)|(longitudeCell&0xffffffffL);
    }
    public static int latitudeOf(long key){return (int)(key>>32);}
    public static int longitudeOf(long key){return (int)key;}

    private OfflineTilePolicy(){}
}
