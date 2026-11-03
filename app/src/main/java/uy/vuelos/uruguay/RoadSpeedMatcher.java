package uy.vuelos.uruguay;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline road-segment matching of OpenStreetMap way geometry.
 * No guess from road class; unknown or ambiguous speed limits are never invented.
 */
public final class RoadSpeedMatcher {
    public static final class Road {
        public final long id;
        public final String name;
        public final int maxKmh;
        public final double[][] geometry;
        public Road(long wayId,String n,int speed,double[][] geom){
            id=wayId;name=n;maxKmh=speed;geometry=geom;
        }
    }
    public static final class Match {
        public final int limitKmh;
        public final String name;
        public final double distanceMeters;
        public final boolean confident;
        private Match(int limit,String n,double distance,boolean ok){
            limitKmh=limit;name=n;distanceMeters=distance;confident=ok;
        }
        public static Match unavailable(){return new Match(0,"",Double.POSITIVE_INFINITY,false);}
    }

    public static int parseMaxspeed(String value){
        if(value==null)return 0;
        String v=value.trim().toLowerCase(Locale.ROOT);
        Matcher m=Pattern.compile("^([0-9]{1,3})(?:\\s*(km/h|kmh|kph|mph))?$").matcher(v);
        if(!m.matches())return 0;
        int n=Integer.parseInt(m.group(1));
        if(n<5 || n>150)return 0;
        if("mph".equals(m.group(2)))n=(int)Math.round(n*1.609344);
        return n<=160 ? n:0;
    }

    public static List<Road> parseOverpass(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        JSONArray elements=root.optJSONArray("elements");
        if(elements==null)throw new IllegalArgumentException("No hay geometrías de calles");
        ArrayList<Road> result=new ArrayList<>();
        for(int i=0;i<elements.length();i++){
            JSONObject el=elements.optJSONObject(i);
            if(el==null || !"way".equals(el.optString("type")))continue;
            JSONObject tags=el.optJSONObject("tags");
            JSONArray pts=el.optJSONArray("geometry");
            if(tags==null || pts==null || pts.length()<2)continue;
            String type=tags.optString("highway","");
            if(type.isEmpty() || type.equals("footway")||type.equals("pedestrian")||
               type.equals("path")||type.equals("steps")||type.equals("cycleway"))continue;
            int speed=parseMaxspeed(tags.optString("maxspeed",""));
            if(speed==0)continue;
            double[][] geom=new double[pts.length()][2];
            int valid=0;
            for(int j=0;j<pts.length();j++){
                JSONObject p=pts.optJSONObject(j);
                if(p==null)continue;
                double lat=p.optDouble("lat",Double.NaN),lon=p.optDouble("lon",Double.NaN);
                if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
                geom[valid][0]=lat;geom[valid][1]=lon;valid++;
            }
            if(valid<2)continue;
            if(valid!=geom.length)geom=java.util.Arrays.copyOf(geom,valid);
            result.add(new Road(el.optLong("id"),tags.optString("name",""),speed,geom));
        }
        return result;
    }

    /** Require <=22m lateral distance and an accurate GPS fix; resolve parallel-road ambiguity. */
    public static Match nearest(List<Road> roads,double lat,double lon,float accuracyMeters){
        if(roads==null || accuracyMeters<=0 || accuracyMeters>25 || !Double.isFinite(lat)
           || !Double.isFinite(lon))return Match.unavailable();
        Road first=null,second=null;
        double best=Double.POSITIVE_INFINITY,next=Double.POSITIVE_INFINITY;
        for(Road r:roads){
            if(r.geometry==null||r.geometry.length<2)continue;
            double distance=Double.POSITIVE_INFINITY;
            for(int i=1;i<r.geometry.length;i++){
                distance=Math.min(distance,distanceToSegment(lat,lon,
                    r.geometry[i-1][0],r.geometry[i-1][1],
                    r.geometry[i][0],r.geometry[i][1]));
            }
            if(distance<best){
                next=best;second=first;
                best=distance;first=r;
            }else if(distance<next){
                next=distance;second=r;
            }
        }
        if(first==null || best>22.0)return Match.unavailable();
        // Parallel carriageways with different limits can be indistinguishable on phone GPS.
        if(second!=null && second.maxKmh!=first.maxKmh && next-best<9.0 && next<30.0)
            return Match.unavailable();
        return new Match(first.maxKmh,first.name,best,true);
    }

    private static double distanceToSegment(double lat,double lon,
        double lat1,double lon1,double lat2,double lon2){
        double cos=Math.cos(Math.toRadians(lat));
        double x1=(lon1-lon)*111320.0*cos,y1=(lat1-lat)*111195.0;
        double x2=(lon2-lon)*111320.0*cos,y2=(lat2-lat)*111195.0;
        double vx=x2-x1,vy=y2-y1;
        double denominator=vx*vx+vy*vy;
        if(denominator<0.00001)return Math.hypot(x1,y1);
        double t=Math.max(0,Math.min(1,(-x1*vx-y1*vy)/denominator));
        return Math.hypot(x1+t*vx,y1+t*vy);
    }
    private RoadSpeedMatcher(){}
}
