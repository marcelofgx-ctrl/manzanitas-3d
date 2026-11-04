package uy.vuelos.uruguay;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Strict parser for prebuilt Geofabrik OSM segment tiles. */
public final class OfflineRoadDecoder {
    public static List<RoadSpeedMatcher.Road> parse(String json)throws Exception {
        JSONArray arr=new JSONArray(json);
        ArrayList<RoadSpeedMatcher.Road> ways=new ArrayList<>();
        for(int i=0;i<arr.length();i++){
            JSONArray r=arr.optJSONArray(i);
            if(r==null||r.length()<7)continue;
            double a=r.optDouble(3,Double.NaN),b=r.optDouble(4,Double.NaN),
                   c=r.optDouble(5,Double.NaN),d=r.optDouble(6,Double.NaN);
            if(!Double.isFinite(a)||!Double.isFinite(b)||
               !Double.isFinite(c)||!Double.isFinite(d)||
               a<-90||a>90||c<-90||c>90||b<-180||b>180||d<-180||d>180)continue;
            int limit=r.optInt(2,0);
            if(limit<0||limit>160)limit=0;
            ways.add(new RoadSpeedMatcher.Road(r.optLong(0),
                r.optString(1,""),limit,new double[][]{{a,b},{c,d}}));
        }
        return ways;
    }
    private OfflineRoadDecoder(){}
}
