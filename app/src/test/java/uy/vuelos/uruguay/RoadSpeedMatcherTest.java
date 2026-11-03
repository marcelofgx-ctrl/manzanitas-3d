package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RoadSpeedMatcherTest {
    @Test public void parsesCommonLimitsAndRejectsUnreliableValues(){
        assertEquals(45,RoadSpeedMatcher.parseMaxspeed("45"));
        assertEquals(60,RoadSpeedMatcher.parseMaxspeed("60 km/h"));
        assertEquals(48,RoadSpeedMatcher.parseMaxspeed("30 mph"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("UY:urban"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("signals"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed("45;60"));
        assertEquals(0,RoadSpeedMatcher.parseMaxspeed(""));
    }
    @Test public void selectsClosestStreetWithoutInferringMissingLimits(){
        RoadSpeedMatcher.Road a=new RoadSpeedMatcher.Road(1,"Calle 1",45,
            new double[][]{{-34.9100,-56.1600},{-34.9090,-56.1600}});
        RoadSpeedMatcher.Match match=RoadSpeedMatcher.nearest(Arrays.asList(a),
            -34.9095,-56.16001,6);
        assertTrue(match.confident);
        assertEquals(45,match.limitKmh);
        assertFalse(RoadSpeedMatcher.nearest(Collections.emptyList(),
            -34.9095,-56.16001,6).confident);
    }
    @Test public void noGuessIfFarFromMatchedRoadOrGpsImprecise(){
        RoadSpeedMatcher.Road a=new RoadSpeedMatcher.Road(1,"Ruta",60,
            new double[][]{{-34.91,-56.16},{-34.90,-56.16}});
        assertFalse(RoadSpeedMatcher.nearest(Arrays.asList(a),-34.905,-56.161,5).confident);
        assertFalse(RoadSpeedMatcher.nearest(Arrays.asList(a),-34.905,-56.16001,80).confident);
    }
    @Test public void parallelRoadsWithConflictingLimitsStayUnknown(){
        RoadSpeedMatcher.Road a=new RoadSpeedMatcher.Road(1,"A",45,
            new double[][]{{-34.911,-56.16},{-34.909,-56.16}});
        RoadSpeedMatcher.Road b=new RoadSpeedMatcher.Road(2,"B",60,
            new double[][]{{-34.911,-56.16005},{-34.909,-56.16005}});
        assertFalse(RoadSpeedMatcher.nearest(Arrays.asList(a,b),
            -34.91,-56.16002,5).confident);
    }
    @Test public void extractsOnlyTaggedMotorableWays() throws Exception {
        String json="{\"elements\":["+
        "{\"type\":\"way\",\"id\":111,\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"45\",\"name\":\"Test\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.16},{\"lat\":-34.90,\"lon\":-56.16}]},"+
        "{\"type\":\"way\",\"id\":222,\"tags\":{\"highway\":\"residential\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.17},{\"lat\":-34.90,\"lon\":-56.17}]}, "+
        "{\"type\":\"way\",\"id\":333,\"tags\":{\"highway\":\"footway\",\"maxspeed\":\"10\"},\"geometry\":["+
        "{\"lat\":-34.91,\"lon\":-56.18},{\"lat\":-34.90,\"lon\":-56.18}]}]}";
        List<RoadSpeedMatcher.Road> roads=RoadSpeedMatcher.parseOverpass(json);
        assertEquals(1,roads.size());assertEquals(111,roads.get(0).id);
    }
    @Test public void emptyResultProducesNoInventedSpeed()throws Exception{
        assertTrue(RoadSpeedMatcher.parseOverpass("{\"elements\":[]}").isEmpty());
    }
}
