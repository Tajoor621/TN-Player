package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.ui.*
import org.junit.Assert.*
import org.junit.Test

class GesturePolicyTest {
    private fun mapping(slot: GestureSlot, default: GestureAction) {
        assertEquals(default, GesturePolicy.resolve(slot, null, null, false))
        for (action in GestureAction.values()) assertEquals(action, GesturePolicy.resolve(slot, action.name, null, false))
        assertEquals(default, GesturePolicy.resolve(slot, "invalid", null, false))
    }
    @Test fun singleTapMapping() = mapping(GestureSlot.SINGLE_TAP, GestureAction.CONTROLS)
    @Test fun leftDoubleTapMapping() = mapping(GestureSlot.DOUBLE_LEFT, GestureAction.SEEK_BACK)
    @Test fun rightDoubleTapMapping() = mapping(GestureSlot.DOUBLE_RIGHT, GestureAction.SEEK_FORWARD)
    @Test fun leftSwipeMapping() = mapping(GestureSlot.VERTICAL_LEFT, GestureAction.BRIGHTNESS)
    @Test fun rightSwipeMapping() = mapping(GestureSlot.VERTICAL_RIGHT, GestureAction.VOLUME)
    @Test fun explicitNewMappingWinsOverLegacyPresets() {
        assertEquals(GestureAction.NONE, GesturePolicy.resolve(GestureSlot.DOUBLE_LEFT, "NONE", true, true))
        assertEquals(GestureAction.PLAY_PAUSE, GesturePolicy.resolve(GestureSlot.DOUBLE_RIGHT, null, null, true))
        assertEquals(GestureAction.VOLUME, GesturePolicy.resolve(GestureSlot.VERTICAL_LEFT, null, false, false))
        assertEquals(GestureAction.VOLUME, GesturePolicy.resolve(GestureSlot.VERTICAL_RIGHT, null, true, false))
    }
    @Test fun seekClampsBothEndsAndCannotOverflow() {
        assertEquals(0L, GesturePolicy.seek(3000, -10000, 60000))
        assertEquals(60000L, GesturePolicy.seek(59000, 10000, 60000))
        assertEquals(20000L, GesturePolicy.seek(10000, 10000, -1))
        assertEquals(Long.MAX_VALUE, GesturePolicy.seek(Long.MAX_VALUE, 10000, -1))
    }
    @Test fun tapsMustStayOnTheSameHalfAndWithinTimeAndDistance() {
        assertTrue(GesturePolicy.doubleTap(100f, 100f, 110f, 100f, 150, 1f, 400))
        assertFalse(GesturePolicy.doubleTap(190f, 100f, 210f, 100f, 150, 1f, 400))
        assertFalse(GesturePolicy.doubleTap(100f, 100f, 110f, 100f, 301, 1f, 400))
        assertFalse(GesturePolicy.doubleTap(10f, 100f, 100f, 100f, 150, 1f, 400))
    }
    @Test fun navigationEdgesAreReserved() {
        assertFalse(GesturePolicy.safeStart(10f, 200f, 400, 800, 24f, 24f, 24f, 60f))
        assertFalse(GesturePolicy.safeStart(200f, 760f, 400, 800, 24f, 24f, 24f, 60f))
        assertTrue(GesturePolicy.safeStart(100f, 200f, 400, 800, 24f, 24f, 24f, 60f))
    }
    @Test fun zoomAndPanAreBounded() {
        assertEquals(3f, GesturePolicy.zoom(99f), .001f)
        assertEquals(1f, GesturePolicy.zoom(.1f), .001f)
        assertEquals(100f, GesturePolicy.pan(999f, 2f, 200), .001f)
        assertEquals(0f, GesturePolicy.pan(999f, 1f, 200), .001f)
    }
    @Test fun heldSpeedRestoresOriginalOnReleaseOrCancellation() {
        val state = HeldSpeedState(); val controller = Any()
        assertEquals(2f, state.begin(controller, 1.25f), .001f)
        assertTrue(state.active); assertNull(state.release(Any()))
        assertEquals(1.25f, state.release(controller)!!, .001f)
        assertFalse(state.active); assertNull(state.release(controller))
        state.begin(controller, .5f)
        assertEquals(.5f, state.release(controller)!!, .001f)
    }
}
