package uy.vuelos.uruguay;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps the actual nearest drivable OSM road first, then checks whether it has
 * a verified numeric maxspeed tag. A nearby faster road cannot supply a false limit.
 */
public final class RoadSpeedMatcher {
    public static final String GPS_INACCURATE="GPS_IMPRECISO";
    public static final String NO_ROAD="CALLE_NO_IDENTIFICADA";
    public static final String NO_LIMIT="SIN_LIMITE_CARTOGRAFICO";
    public static final String AMBIGUOUS="CALLES_AMBIGUAS";
    public static final String VERIFIED="LIMITE_VERIFICADO";

    public static final class Road {
        public final long id;
        public final String name;
        public final int maxKmh; // 0 means no trustworthy numeric maxspeed, NEVER default limit.
        public final double[][] geometry;
        public Road(long id,String name,int speed,double[][] geom){
            this.id=id;this.name=name;this.maxKmh=speed;this.geometry=geom;
        }
    }
    public static final class Match {
        public final int limitKmh;
        public final String name;
        public final double distanceMeters;
        public final boolean confident;
        public final String reason;
        private Match(int kmh,String road,double distance,boolean ok,String reason){
            this.limitKmh=kmh;name=road;distanceMeters=distance;
            confident=ok;this.reason=reason;
        }
        public static Match unavailable(){
            return new Match(0,"",Double.POSITIVE_INFINITY,false,NO_ROAD);
        }
    }
    public static int parseMaxspeed(String text){
        if(text==null)return 0;
        String value=text.trim().toLowerCase(Locale.ROOT);
        Matcher m=Pattern.compile("^([0-9]{1,3})(?:\\s*(km/h|kmh|kph|mph))?$").matcher(value);
        if(!m.matches())return 0;
        int speed=Integer.parseInt(m.group(1));
        if(speed<5||speed>150)return 0;
        if("mph".equals(m.group(2)))speed=(int)Math.round(speed*1.609344);
        return speed<=160?speed:0;
    }

    private static boolean drivable(String t) {
        switch(t){
            case "motorway":case "motorway_link":case "trunk":case "trunk_link":
            case "primary":case "primary_link":case "secondary":case "secondary_link":
            case "tertiary":case "tertiary_link":case "residential":case "unclassified":
            case "service":case "living_street":case "road":
                return true;
            default:return false;
        }
    }

    public static List<Road> parseOverpass(String raw) throws Exception {
        JSONArray ways=new JSONObject(raw).optJSONArray("elements");
        if(ways==null)throw new IllegalArgumentException("Respuesta cartográfica ilegible");
        ArrayList<Road> roads=new ArrayList<>();
        for(int i=0;i<ways.length();i++){
            JSONObject way=ways.optJSONObject(i);
            if(way==null || !"way".equals(way.optString("type")))continue;
            JSONObject tags=way.optJSONObject("tags");
            JSONArray nodes=way.optJSONArray("geometry");
            if(tags==null||nodes==null||nodes.length()<2)continue;
            if(!drivable(tags.optString("highway","")))continue;
            int speed=parseMaxspeed(tags.optString("maxspeed",""));
            // Accept directional tags only when both directions explicitly agree.
            // A lone forward/backward tag would need the car's heading to interpret.
            if(speed==0){
                int fwd=parseMaxspeed(tags.optString("maxspeed:forward",""));
                int back=parseMaxspeed(tags.optString("maxspeed:backward",""));
                if(fwd>0&&fwd==back)speed=fwd;
            }
            double[][] geom=new double[nodes.length()][2];
            int valid=0;
            for(int n=0;n<nodes.length();n++){
                JSONObject node=nodes.optJSONObject(n);
                if(node==null)continue;
                double lat=node.optDouble("lat",Double.NaN);
                double lon=node.optDouble("lon",Double.NaN);
                if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
                geom[valid][0]=lat;geom[valid][1]=lon;valid++;
            }
            if(valid<2)continue;
            if(valid!=geom.length)geom=java.util.Arrays.copyOf(geom,valid);
            roads.add(new Road(way.optLong("id"),tags.optString("name",""),speed,geom));
        }
        return roads;
    }

    /** Keep separate OSM ways separate, but combine their many component segments
     * before determining whether another STREET truly creates ambiguity.
     * Calling every segment a separate road causes false N/D next to intersections.
     */
    private static final class Candidate {
        final Road road;
        final double distance;
        final double alignmentDegrees;
        Candidate(Road r,double distance,double alignment){
            this.road=r;this.distance=distance;this.alignmentDegrees=alignment;
        }
    }

    public static Match nearest(List<Road> roads,double lat,double lon,float accuracy){
        return nearest(roads,lat,lon,accuracy,Double.NaN);
    }

    /** With an accurate moving GPS bearing, a crossing street can be rejected by
     * its perpendicular orientation; parallel roads remain ambiguous.
     * headingDegrees is NaN when stationary, unavailable, or too imprecise.
     */
    public static Match nearest(List<Road> roads,double lat,double lon,float accuracy,
                                double headingDegrees){
        if(roads==null||!Double.isFinite(lat)||!Double.isFinite(lon)||
            accuracy<=0||accuracy>25)
            return new Match(0,"",Double.POSITIVE_INFINITY,false,GPS_INACCURATE);
        Map<Long,Candidate> byWay=new HashMap<>();
        ArrayList<Candidate> distinct=new ArrayList<>();
        boolean hasHeading=Double.isFinite(headingDegrees);
        for(int index=0;index<roads.size();index++){
            Road road=roads.get(index);
            if(road.geometry==null||road.geometry.length<2)continue;
            double closest=Double.POSITIVE_INFINITY;
            double alignment=90;
            for(int j=1;j<road.geometry.length;j++){
                double[] p=road.geometry[j-1],q=road.geometry[j];
                double distance=distanceToSegment(lat,lon,p[0],p[1],q[0],q[1]);
                if(distance<closest){
                    closest=distance;
                    alignment=hasHeading?
                        axisMisalignment(p[0],p[1],q[0],q[1],headingDegrees):90;
                }
            }
            Candidate c=new Candidate(road,closest,alignment);
            if(road.id<=0){
                distinct.add(c);
            }else{
                Candidate old=byWay.get(road.id);
                if(old==null||c.distance<old.distance)byWay.put(road.id,c);
            }
        }
        distinct.addAll(byWay.values());
        distinct.sort(Comparator.comparingDouble(c->c.distance));
        double maxDistance=Math.max(22,Math.min(30,12+accuracy));
        if(distinct.isEmpty()||distinct.get(0).distance>maxDistance)
            return new Match(0,"",Double.POSITIVE_INFINITY,false,NO_ROAD);

        Candidate best=distinct.get(0);
        double ambiguousRange=Math.max(8,accuracy*0.8);
        List<Candidate> competitors=new ArrayList<>();
        for(Candidate candidate:distinct){
            if(candidate.distance>30||candidate.distance-best.distance>=ambiguousRange)
                break;
            competitors.add(candidate);
        }

        boolean hasConflict=false;
        for(Candidate other:competitors){
            if(other.road.maxKmh!=best.road.maxKmh){
                hasConflict=true;
                break;
            }
        }
        if(hasConflict && hasHeading && accuracy<=15){
            // A distinct road is selected only when its direction closely matches
            // moving GPS and EVERY road with a different limit is nearly crosswise.
            // This NEVER disambiguates parallel carriageways sharing the same heading.
            Candidate directionWinner=null;
            for(Candidate c:competitors){
                if(c.alignmentDegrees>20||c.distance>maxDistance)continue;
                boolean allOtherLimitsClearlyCrosswise=true;
                for(Candidate other:competitors){
                    if(other.road.maxKmh!=c.road.maxKmh &&
                       other.alignmentDegrees<55){
                        allOtherLimitsClearlyCrosswise=false;
                        break;
                    }
                }
                if(allOtherLimitsClearlyCrosswise){
                    if(directionWinner==null||c.distance<directionWinner.distance)
                        directionWinner=c;
                }
            }
            if(directionWinner!=null)best=directionWinner;
            else return new Match(0,best.road.name,best.distance,false,AMBIGUOUS);
        }else if(hasConflict){
            return new Match(0,best.road.name,best.distance,false,AMBIGUOUS);
        }
        if(best.road.maxKmh==0)
            return new Match(0,best.road.name,best.distance,false,NO_LIMIT);
        return new Match(best.road.maxKmh,best.road.name,best.distance,true,VERIFIED);
    }

    private static double axisMisalignment(double lat1,double lon1,
                                            double lat2,double lon2,double heading){
        // Bearing modulo 180°: road geometry is allowed in both directions.
        double north=lat2-lat1;
        double east=(lon2-lon1)*Math.cos(Math.toRadians((lat1+lat2)*0.5));
        if(Math.hypot(north,east)<0.000005)return 90;
        double axis=(Math.toDegrees(Math.atan2(east,north))+360)%180;
        double course=((heading%180)+180)%180;
        double difference=Math.abs(axis-course);
        return Math.min(difference,180-difference);
    }

    private static double distanceToSegment(double lat,double lon,
        double lat1,double lon1,double lat2,double lon2){
        double cos=Math.cos(Math.toRadians(lat));
        double x1=(lon1-lon)*111320.0*cos,y1=(lat1-lat)*111195.0;
        double x2=(lon2-lon)*111320.0*cos,y2=(lat2-lat)*111195.0;
        double vx=x2-x1,vy=y2-y1,denom=vx*vx+vy*vy;
        if(denom<0.00001)return Math.hypot(x1,y1);
        double t=Math.max(0,Math.min(1,(-x1*vx-y1*vy)/denom));
        return Math.hypot(x1+t*vx,y1+t*vy);
    }
    private RoadSpeedMatcher(){}
}
