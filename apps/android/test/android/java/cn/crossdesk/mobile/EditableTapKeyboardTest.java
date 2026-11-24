package cn.crossdesk.mobile;

import android.app.Dialog;
import android.os.SystemClock;
import android.view.WindowInsets;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class EditableTapKeyboardTest {
    private static Field field(String name) throws ReflectiveOperationException {
        Field f = MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void reply(MainActivity activity, int id, boolean editable) throws Exception {
        activity.data(new JSONObject().put("type", "yudesk_ime").put("id", id)
                .put("display", 0).put("editable", editable));
    }

    private static boolean ime(ActivityScenario<MainActivity> scenario) {
        boolean[] visible = {false};
        scenario.onActivity(activity -> {
            var insets = activity.getWindow().getDecorView().getRootWindowInsets();
            visible[0] = insets != null && insets.isVisible(WindowInsets.Type.ime());
        });
        return visible[0];
    }

    private static void awaitIme(ActivityScenario<MainActivity> scenario, boolean expected) {
        long timeout = SystemClock.uptimeMillis() + 4000;
        while (ime(scenario) != expected && SystemClock.uptimeMillis() < timeout) SystemClock.sleep(50);
        assertEquals("system IME visibility", expected, ime(scenario));
    }

    @Test public void editableReplyOpensSystemImeAndClosingInvalidatesNextReply() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 30);
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            NativeSession[] session = new NativeSession[1];
            scenario.onActivity(activity -> {
                try {
                    Dialog consent = (Dialog)field("privacyDialog").get(activity);
                    if (consent != null) consent.dismiss();
                    // A disconnected fixture exercises the UI without making network requests.
                    session[0] = new NativeSession(activity, "localhost", 1, "127.0.0.1:1", "", activity);
                    field("session").set(activity, session[0]);
                    var show = MainActivity.class.getDeclaredMethod("sessionScreen", boolean.class);
                    show.setAccessible(true);
                    show.invoke(activity, false);
                    field("ready").setBoolean(activity, true);
                } catch (Exception e) { throw new AssertionError(e); }
            });
            long timeout = SystemClock.uptimeMillis() + 3000;
            boolean[] focused = {false};
            do {
                scenario.onActivity(a -> focused[0] = a.hasWindowFocus());
                if (!focused[0]) SystemClock.sleep(50);
            } while (!focused[0] && SystemClock.uptimeMillis() < timeout);
            assertTrue("fixture has window focus", focused[0]);
            scenario.onActivity(a -> {
                try { reply(a, session[0].imeFeedback.begin(0, SystemClock.uptimeMillis()), false); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            awaitIme(scenario, false);
            scenario.onActivity(a -> {
                try { reply(a, session[0].imeFeedback.begin(0, SystemClock.uptimeMillis()), true); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            awaitIme(scenario, true);
            scenario.onActivity(a -> {
                try {
                    int id = session[0].imeFeedback.begin(0, SystemClock.uptimeMillis());
                    assertTrue(((SessionControls)field("controls").get(a)).closeKeyboard());
                    reply(a, id, true);
                } catch (Exception e) { throw new AssertionError(e); }
            });
            awaitIme(scenario, false);
        }
    }
}
