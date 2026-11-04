package uy.vuelos.uruguay;

/** Foreground-only heading from movement over a sufficiently long GPS baseline.
 * Never use compass direction while stationary; never assume location accuracy.
 */
public final class MovingCourseFilter {
    private boolean anchored=false;
    private double lat0,lon0;
    private long time0;
    private double course=Double.NaN;
    private long courseTime=0;

    public void reset(){anchored=false;course=Double.NaN;time0=0;courseTime=0;}

    public double sample(double lat,double lon,float accuracyMeters,float speedMetersPerSecond,
                         long elapsedMillis,double rawBearingDegrees,double bearingAccuracyDegrees){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||accuracyMeters<=0||
           accuracyMeters>12||speedMetersPerSecond<7||speedMetersPerSecond>75||
           elapsedMillis<=0){
            reset();return Double.NaN;
        }
        // GPS-bearing metadata can be absent on real phones; estimate heading
        // only from a 40m+ course (not from one noisy point).
        if(!anchored||elapsedMillis<=time0||elapsedMillis-time0>12000L){
            lat0=lat;lon0=lon;time0=elapsedMillis;anchored=true;
            course=Double.NaN;courseTime=0;
        }else{
            double distance=haversine(lat0,lon0,lat,lon);
            if(distance>=42&&distance<=220){
                double measured=bearing(lat0,lon0,lat,lon);
                // Reject large jumps suggesting bad fixes rather than smooth a false path.
                long elapsed=elapsedMillis-time0;
                double impliedSpeed=distance*1000.0/elapsed;
                if(impliedSpeed>=speedMetersPerSecond*0.35 &&
                   impliedSpeed<=speedMetersPerSecond*2.25){
                    course=measured;
                    courseTime=elapsedMillis;
                }else{
                    course=Double.NaN;courseTime=0;
                }
                lat0=lat;lon0=lon;time0=elapsedMillis;
            }
        }
        boolean rawReliable=Double.isFinite(rawBearingDegrees) &&
            Double.isFinite(bearingAccuracyDegrees) &&
            bearingAccuracyDegrees>=0&&bearingAccuracyDegrees<=20;
        if(rawReliable)return normalized(rawBearingDegrees);
        if(Double.isFinite(course)&&elapsedMillis-courseTime<=5500L)
            return course;
        return Double.NaN;
    }

    private static double normalized(double deg){return ((deg%360)+360)%360;}

    private static double bearing(double lat1,double lon1,double lat2,double lon2){
        double a=Math.toRadians(lat1),b=Math.toRadians(lat2);
        double dLon=Math.toRadians(lon2-lon1);
        double y=Math.sin(dLon)*Math.cos(b);
        double x=Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(dLon);
        return normalized(Math.toDegrees(Math.atan2(y,x)));
    }
    private static double haversine(double lat1,double lon1,double lat2,double lon2){
        final double r=6371000.0;
        double a=Math.toRadians(lat1),b=Math.toRadians(lat2);
        double dLat=b-a,dLon=Math.toRadians(lon2-lon1);
        double h=Math.sin(dLat/2)*Math.sin(dLat/2)+
            Math.cos(a)*Math.cos(b)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*r*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))));
    }
}
