package uy.vuelos.uruguay;

import static org.junit.Assert.*;
import org.junit.Test;

public class FooterSheetPolicyTest {
    @Test public void upSwipeOpensFromCollapsed(){
        assertTrue(FooterSheetPolicy.isExpandedAfterGesture(false,-60f,25f));
    }
    @Test public void downSwipeClosesExpanded(){
        assertFalse(FooterSheetPolicy.isExpandedAfterGesture(true,60f,25f));
    }
    @Test public void smallMovementsDoNotAccidentallyChangeState(){
        assertFalse(FooterSheetPolicy.isExpandedAfterGesture(false,-12f,25f));
        assertTrue(FooterSheetPolicy.isExpandedAfterGesture(true,10f,25f));
    }
    @Test public void swipingTowardCurrentStateDoesNotReverseIt(){
        assertTrue(FooterSheetPolicy.isExpandedAfterGesture(true,-40f,25f));
        assertFalse(FooterSheetPolicy.isExpandedAfterGesture(false,40f,25f));
    }
}
