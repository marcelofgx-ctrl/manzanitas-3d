package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
public final class OfflineRoadDecoderTest {
    @Test public void parsesKnownAndUnknownRoads()throws Exception{
        List<RoadSpeedMatcher.Road> roads=OfflineRoadDecoder.parse("["+
            "[1,\"Av. Italia\",60,-34.9,-56.15,-34.91,-56.15],"+
            "[2,\"Camino\",0,-34.91,-56.151,-34.92,-56.151]]");
        assertEquals(2,roads.size());
        assertEquals(60,roads.get(0).maxKmh);
        assertEquals(0,roads.get(1).maxKmh);
        assertEquals("Av. Italia",roads.get(0).name);
    }
    @Test public void rejectsBadDataAndNeverMakesUpSpeed()throws Exception{
        List<RoadSpeedMatcher.Road> roads=OfflineRoadDecoder.parse(
            "[[1,\"\",999,-34.90,-56.15,-34.91,-56.15],"+
            "[2,\"\",45,999,-56.15,-34.91,-56.15]]");
        assertEquals(1,roads.size());
        assertEquals(0,roads.get(0).maxKmh);
    }
    @Test public void nearbyUntaggedStreetPreventsWrongParallelLimit()throws Exception{
        List<RoadSpeedMatcher.Road> roads=OfflineRoadDecoder.parse(
          "[[1,\"Calle local\",0,-34.911,-56.160,-34.909,-56.160],"+
          "[2,\"Otra calle\",60,-34.911,-56.1603,-34.909,-56.1603]]");
        RoadSpeedMatcher.Match m=RoadSpeedMatcher.nearest(roads,-34.91,-56.16001,5f);
        assertFalse(m.confident);
        assertEquals(RoadSpeedMatcher.NO_LIMIT,m.reason);
    }
}
