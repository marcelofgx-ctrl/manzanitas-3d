package uy.vuelos.uruguay;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
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

    /** Do not pick a tagged street just because the true nearest street is untagged.
     * GPS errors >25m, distant streets and parallel roads with different values are unknown.
     */
    public static Match nearest(List<Road> roads,double lat,double lon,float accuracy){
        if(roads==null || !Double.isFinite(lat) || !Double.isFinite(lon)
            || accuracy<=0 || accuracy>25)
            return new Match(0,"",Double.POSITIVE_INFINITY,false,GPS_INACCURATE);
        Road bestRoad=null,secondRoad=null;
        double nearest=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
        for(Road road:roads){
            if(road.geometry==null||road.geometry.length<2)continue;
            double min=Double.POSITIVE_INFINITY;
            for(int j=1;j<road.geometry.length;j++){
                min=Math.min(min,distanceToSegment(lat,lon,
                    road.geometry[j-1][0],road.geometry[j-1][1],
                    road.geometry[j][0],road.geometry[j][1]));
            }
            if(min<nearest){
                secondRoad=bestRoad;second=nearest;
                bestRoad=road;nearest=min;
            }else if(min<second){
                secondRoad=road;second=min;
            }
        }
        double maxDistance=Math.max(22,Math.min(30,12+accuracy));
        if(bestRoad==null||nearest>maxDistance)
            return new Match(0,"",nearest,false,NO_ROAD);
        if(secondRoad!=null && second<30 &&
            second-nearest<Math.max(8,accuracy*0.8) &&
            secondRoad.maxKmh!=bestRoad.maxKmh)
            return new Match(0,bestRoad.name,nearest,false,AMBIGUOUS);
        if(bestRoad.maxKmh==0)
            return new Match(0,bestRoad.name,nearest,false,NO_LIMIT);
        return new Match(bestRoad.maxKmh,bestRoad.name,nearest,true,VERIFIED);
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
