package cn.crossdesk.mobile;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteResolutionStoreTest {
    @Test public void followingComputerRemovesOnlySelectedDisplayFromReconnect() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        RemoteResolutionStore store = new RemoteResolutionStore(context);
        RemoteDisplayResolutions.Mode size = new RemoteDisplayResolutions.Mode(1920, 1080);
        store.remember("test.invalid", 1, "mac", "work", size);
        store.remember("test.invalid", 1, "mac", "phone", size);
        store.remember("test.invalid", 1, "mac", "phone", null);
        RemoteResolutionStore reopened = new RemoteResolutionStore(context);
        assertTrue(reopened.followsComputer("test.invalid", 1, "mac", "phone"));
        assertFalse(reopened.followsComputer("test.invalid", 1, "mac", "work"));
        JSONArray reconnect = reopened.load("test.invalid", 1, "mac");
        assertEquals(1, reconnect.length());
        assertEquals("work", reconnect.getJSONObject(0).getString("display_key"));
    }

    @Test public void lateFixedReplyCannotOverwriteNewFollowSelection() throws Exception {
        RemoteDisplayResolutions state = new RemoteDisplayResolutions();
        JSONObject fixed = new JSONObject().put("width", 1280).put("height", 720);
        JSONObject display = new JSONObject().put("display_id", 0).put("display_key", "work")
                .put("width", 1920).put("height", 1080).put("modes", new JSONArray().put(fixed));
        JSONArray displays = new JSONArray().put(display);
        state.receive(new JSONObject().put("type", 3)
                .put("host_info", new JSONObject().put("display_resolutions", displays)));
        RemoteDisplayResolutions.Mode mode = new RemoteDisplayResolutions.Mode(1280, 720);
        RemoteDisplayResolutions.Request request = state.begin(0, mode);
        state.expire(request.id);
        state.selectionSaved("work");
        JSONObject ack = new JSONObject(fixed.toString()).put("display_id", 0)
                .put("display_key", "work").put("request_id", request.id).put("accepted", true);
        JSONObject reply = new JSONObject().put("type", 16).put("display_resolution", ack)
                .put("display_resolutions", displays);
        assertNull(state.receive(reply));
        assertNotEquals(request.id, state.begin(0, mode).id);
        assertNull(state.receive(reply));
        assertTrue(state.pending);
    }
}
