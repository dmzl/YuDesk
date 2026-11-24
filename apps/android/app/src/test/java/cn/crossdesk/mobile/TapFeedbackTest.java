package cn.crossdesk.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class TapFeedbackTest {
    @Test public void onlyLatestClickOnSameDisplayCanOpenImeOnce() {
        TapFeedback feedback = new TapFeedback();
        int stale = feedback.begin(1, 100);
        int latest = feedback.begin(1, 110);
        assertFalse(feedback.accept(stale, 1, 1, 200));
        assertFalse(feedback.accept(latest, 0, 1, 201));
        assertTrue(feedback.accept(latest, 1, 1, 250));
        assertFalse(feedback.accept(latest, 1, 1, 251));
    }
    @Test public void lateAndCancelledRepliesNeverReopen() {
        TapFeedback feedback = new TapFeedback();
        assertFalse(feedback.accept(feedback.begin(0, 10), 0, 0, 5010));
        int id = feedback.begin(0, 6000);
        feedback.cancel();
        assertFalse(feedback.accept(id, 0, 0, 6030));
    }
}
