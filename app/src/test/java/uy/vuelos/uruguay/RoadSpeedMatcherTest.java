package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RoadSpeedMatcherTest {
    private RoadSpeedMatcher.Road road(long id,String name,int speed,double lon){
        return new RoadSpeedMatcher.Road(id,name,speed,
            new double[][]{{-34.911,lon},{-34.909,lon}});
    }
    @Test public void parsesNumericAndDirectionalWithoutGuessing(){
        assertEquals(45,RoadSpeedMatcher.parseMaxspeed("45"));
        assertEquals(60,RoadSpeedMatcher.parseMaxspeed("60 km/h"));
        assertEquals(48,RoadSpeedMatcher.parseMaxspeed("30 mph"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("UY:urban"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("signals"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("45;60"));
    }
    @Test public void nearestTaggedRoadHasLimit(){
        RoadSpeedMatcher.Match result=RoadSpeedMatcher.nearest(
            Arrays.asList(road(1,"Calle 1",45,-56.160)), -34.910,-56.16001,5);
        assertTrue(result.confident);
        assertEquals(45,result.limitKmh);
        assertEquals("LIMITE_VERIFICADO",result.reason);
    }
    @Test public void unknownGpsOrRoadNeverInventsSpeed(){
        List<RoadSpeedMatcher.Road> ways=Arrays.asList(road(1,"Calle",60,-56.160));
        assertEquals(RoadSpeedMatcher.GPS_INACCURATE,
            RoadSpeedMatcher.nearest(ways,-34.910,-56.160,80).reason);
        assertEquals(RoadSpeedMatcher.NO_ROAD,
            RoadSpeedMatcher.nearest(Collections.emptyList(),-34.910,-56.160,5).reason);
        assertFalse(RoadSpeedMatcher.nearest(ways,-34.910,-56.161,5).confident);
    }
    @Test public void untaggedNearestRoadBlocksNearbyTaggedSpeed(){
        List<RoadSpeedMatcher.Road> ways=Arrays.asList(
            road(1,"Calle sin registrar",0,-56.1600),
            road(2,"Paralela",60,-56.1603));
        RoadSpeedMatcher.Match result=RoadSpeedMatcher.nearest(ways,-34.910,-56.16001,5);
        assertFalse(result.confident);
        assertEquals(RoadSpeedMatcher.NO_LIMIT,result.reason);
        assertEquals("Calle sin registrar",result.name);
    }
    @Test public void parallelRoadsDifferentLimitsAreAmbiguous(){
        List<RoadSpeedMatcher.Road> ways=Arrays.asList(
            road(1,"A",45,-56.160),
            road(2,"B",60,-56.16005));
        RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(ways,-34.910,-56.16002,5);
        assertFalse(match.confident);
        assertEquals(RoadSpeedMatcher.AMBIGUOUS,match.reason);
    }
    @Test public void parsesUntaggedRoadToExplainWhyLimitIsUnavailable()throws Exception{
        String json="{\"elements\":["+
        "{\"type\":\"way\",\"id\":111,\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"45\",\"name\":\"A\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.16},{\"lat\":-34.90,\"lon\":-56.16}]},"+
        "{\"type\":\"way\",\"id\":222,\"tags\":{\"highway\":\"residential\",\"name\":\"B\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.17},{\"lat\":-34.90,\"lon\":-56.17}]},"+
        "{\"type\":\"way\",\"id\":333,\"tags\":{\"highway\":\"footway\",\"maxspeed\":\"10\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.18},{\"lat\":-34.90,\"lon\":-56.18}]}]}";
        List<RoadSpeedMatcher.Road> ways=RoadSpeedMatcher.parseOverpass(json);
        assertEquals(2,ways.size());
        assertEquals(45,ways.get(0).maxKmh);
        assertEquals(0,ways.get(1).maxKmh);
    }
    @Test public void equalDirectionalTagsAreAllowedButConflictsUnknown()throws Exception{
        String json="{\"elements\":["+
        "{\"type\":\"way\",\"id\":1,\"tags\":{\"highway\":\"residential\",\"maxspeed:forward\":\"45\",\"maxspeed:backward\":\"45\"},\"geometry\":[{\"lat\":-34.91,\"lon\":-56.16},{\"lat\":-34.90,\"lon\":-56.16}]},"+
        "{\"type\":\"way\",\"id\":2,\"tags\":{\"highway\":\"residential\",\"maxspeed:forward\":\"45\",\"maxspeed:backward\":\"60\"},\"geometry\":[{\"lat\":-34.91,\"lon\":-56.17},{\"lat\":-34.90,\"lon\":-56.17}]}]}";
        List<RoadSpeedMatcher.Road> ways=RoadSpeedMatcher.parseOverpass(json);
        assertEquals(45,ways.get(0).maxKmh);
        assertEquals(0,ways.get(1).maxKmh);
    }
    @Test public void emptyReturnsUnknown()throws Exception{
        assertTrue(RoadSpeedMatcher.parseOverpass("{\"elements\":[]}").isEmpty());
    }
    @Test public void avenueKeepsRecordedLimitAcrossPerpendicularCrossingWhileMoving(){
        RoadSpeedMatcher.Road avenue=new RoadSpeedMatcher.Road(1001,"Avenida Italia",60,
            new double[][]{{-34.911,-56.16},{-34.909,-56.16}});
        RoadSpeedMatcher.Road cross=new RoadSpeedMatcher.Road(2002,"Calle transversal",45,
            new double[][]{{-34.910,-56.161},{-34.910,-56.159}});
        List<RoadSpeedMatcher.Road> ways=Arrays.asList(avenue,cross);
        // Same GPS location: too ambiguous without a direction.
        assertEquals(RoadSpeedMatcher.AMBIGUOUS,
            RoadSpeedMatcher.nearest(ways,-34.91001,-56.16001,5f).reason);
        // GPS north/south on Avenida Italia makes the perpendicular avenue ineligible.
        RoadSpeedMatcher.Match north=RoadSpeedMatcher.nearest(ways,
            -34.91001,-56.16001,5f,0.0);
        assertTrue(north.confident);
        assertEquals(60,north.limitKmh);
        assertEquals("Avenida Italia",north.name);
        // After crossing, position varies but remains on the same known avenue.
        assertEquals(60,RoadSpeedMatcher.nearest(ways,
            -34.90993,-56.160015,5f,178.0).limitKmh);
        // Direction across the street supports the OTHER recorded speed, not a guess.
        RoadSpeedMatcher.Match east=RoadSpeedMatcher.nearest(ways,
            -34.91001,-56.16001,5f,90.0);
        assertTrue(east.confident);
        assertEquals(45,east.limitKmh);
    }

    @Test public void bearingMustNotGuessBetweenParallelRoads(){
        List<RoadSpeedMatcher.Road> ways=Arrays.asList(
            road(1,"Carril principal",60,-56.1600),
            road(2,"Calzada lateral",45,-56.16005));
        RoadSpeedMatcher.Match m=RoadSpeedMatcher.nearest(ways,
            -34.91,-56.16002,5f,0.0);
        assertEquals(RoadSpeedMatcher.AMBIGUOUS,m.reason);
        assertFalse(m.confident);
    }

    @Test public void realSpeedChangeAlongSameAvenueStillRequiresDisambiguation(){
        RoadSpeedMatcher.Road a=new RoadSpeedMatcher.Road(1,"Avenida Italia",60,
            new double[][]{{-34.911,-56.16},{-34.910,-56.16}});
        RoadSpeedMatcher.Road b=new RoadSpeedMatcher.Road(2,"Avenida Italia",45,
            new double[][]{{-34.910,-56.16},{-34.909,-56.16}});
        RoadSpeedMatcher.Match m=RoadSpeedMatcher.nearest(Arrays.asList(a,b),
            -34.910,-56.16,5f,0.0);
        assertEquals(RoadSpeedMatcher.AMBIGUOUS,m.reason);
    }

    @Test public void duplicatePiecesFromSameOsmWayCannotCreateConflictingLimit(){
        RoadSpeedMatcher.Road a=new RoadSpeedMatcher.Road(77,"Avenida Italia",60,
            new double[][]{{-34.912,-56.16},{-34.910,-56.16}});
        RoadSpeedMatcher.Road b=new RoadSpeedMatcher.Road(77,"Avenida Italia",60,
            new double[][]{{-34.910,-56.16},{-34.908,-56.16}});
        RoadSpeedMatcher.Match m=RoadSpeedMatcher.nearest(Arrays.asList(a,b),
            -34.909,-56.160005,6f);
        assertTrue(m.confident);
        assertEquals(60,m.limitKmh);
    }

}
