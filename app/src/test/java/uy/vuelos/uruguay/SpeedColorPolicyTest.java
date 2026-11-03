package uy.vuelos.uruguay;
import static org.junit.Assert.*;
import org.junit.Test;
public final class SpeedColorPolicyTest {
    @Test public void unknownGpsOrLimitNeverShowsViolation(){
        assertEquals(SpeedColorPolicy.UNKNOWN,SpeedColorPolicy.state(-1,45));
        assertEquals(SpeedColorPolicy.UNKNOWN,SpeedColorPolicy.state(51,0));
    }
    @Test public void atOrBelowLimitStaysNormal(){
        assertEquals(SpeedColorPolicy.NORMAL,SpeedColorPolicy.state(45,45));
        assertEquals(SpeedColorPolicy.NORMAL,SpeedColorPolicy.state(0,45));
        assertEquals(SpeedColorPolicy.NORMAL,SpeedColorPolicy.state(30,60));
    }
    @Test public void oneToSixOverLimitAmber(){
        assertEquals(SpeedColorPolicy.AMBER,SpeedColorPolicy.state(46,45));
        assertEquals(SpeedColorPolicy.AMBER,SpeedColorPolicy.state(51,45));
    }
    @Test public void sevenOrMoreOverLimitRed(){
        assertEquals(SpeedColorPolicy.RED,SpeedColorPolicy.state(52,45));
        assertEquals(SpeedColorPolicy.RED,SpeedColorPolicy.state(79,60));
    }
    @Test public void limitsCanChangeWithoutStickyState(){
        assertEquals(SpeedColorPolicy.RED,SpeedColorPolicy.state(52,45));
        assertEquals(SpeedColorPolicy.NORMAL,SpeedColorPolicy.state(52,60));
    }
    @Test public void noToleranceImpliedForLegalRules(){
        assertEquals(1,SpeedColorPolicy.excess(46,45));
        assertEquals(0,SpeedColorPolicy.excess(45,45));
        assertEquals(0,SpeedColorPolicy.excess(65,0));
    }
}
