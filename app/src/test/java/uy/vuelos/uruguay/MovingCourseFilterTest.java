package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
public final class MovingCourseFilterTest {
    @Test public void knownBearingAccuracyAllowsReportedGpsHeading(){
        MovingCourseFilter f=new MovingCourseFilter();
        double course=f.sample(-34.89,-56.14,5f,15f,1000,89,5);
        assertEquals(89,course,0.01);
    }
    @Test public void movingThroughCrossingDerivesHeadingWhenGpsLacksBearingAccuracy(){
        MovingCourseFilter f=new MovingCourseFilter();
        assertTrue(Double.isNaN(f.sample(-34.89,-56.14,5f,14f,1000,90,Double.NaN)));
        double derived=f.sample(-34.89,-56.1394,5f,14f,5000,90,Double.NaN);
        assertEquals(90,derived,2.0);
        // A short intervening sample is allowed to use the recent 40m+ course.
        assertEquals(90,f.sample(-34.89,-56.13925,5f,14f,6000,90,Double.NaN),2.0);
    }
    @Test public void stationaryGpsJitterNeverBecomesAHeading(){
        MovingCourseFilter f=new MovingCourseFilter();
        assertTrue(Double.isNaN(f.sample(-34.89,-56.14,4f,0f,1000,0,1)));
        assertTrue(Double.isNaN(f.sample(-34.8897,-56.14,4f,0f,5000,0,1)));
    }
    @Test public void inaccurateOrImplausibleTravelCannotSelectRoad(){
        MovingCourseFilter f=new MovingCourseFilter();
        assertTrue(Double.isNaN(f.sample(-34.89,-56.14,30f,14f,1000,90,Double.NaN)));
        f.sample(-34.89,-56.14,4f,14f,2000,90,Double.NaN);
        assertTrue(Double.isNaN(f.sample(-34.89,-56.139,4f,14f,2100,90,Double.NaN)));
    }
    @Test public void resetDiscardsPreviousHeading(){
        MovingCourseFilter f=new MovingCourseFilter();
        f.sample(-34.89,-56.14,5f,14f,1000,90,Double.NaN);
        assertFalse(Double.isNaN(f.sample(-34.89,-56.1394,5f,14f,5000,90,Double.NaN)));
        f.reset();
        assertTrue(Double.isNaN(f.sample(-34.89,-56.1394,5f,14f,6000,90,Double.NaN)));
    }
}
