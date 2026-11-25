package cn.crossdesk.mobile;

import android.app.Dialog;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AlbumVideoSurfaceTest {
    private static void touch(View view, long down, int action, float y, float... xs) {
        MotionEvent.PointerProperties[] pointers = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i=0; i<xs.length; ++i) {
            pointers[i] = new MotionEvent.PointerProperties();
            pointers[i].id = i;
            pointers[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i]; coords[i].y = y; coords[i].pressure = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                xs.length, pointers, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    @Test public void switchClearsBackgroundFrameUntilNewDisplayRenders() {
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                var session = new NativeSession(activity, "localhost", 1, "", "", activity);
                var retained = new BackgroundSession(activity, session, "localhost", 1, "");
                try {
                    var sessionField = MainActivity.class.getDeclaredField("session");
                    var background = MainActivity.class.getDeclaredField("backgroundSession");
                    var ready = MainActivity.class.getDeclaredField("ready");
                    sessionField.setAccessible(true);background.setAccessible(true);ready.setAccessible(true);
                    sessionField.set(activity, session);background.set(activity, retained);ready.setBoolean(activity, true);
                    activity.videoSize(1920, 1080);
                    activity.switchToDisplay(1);
                    var save = MainActivity.class.getDeclaredMethod("captureBackgroundSession");
                    save.setAccessible(true);save.invoke(activity);
                    assertEquals(1, retained.selectedDisplay);
                    assertEquals(0, retained.width);assertEquals(0, retained.height);
                    activity.videoSize(540, 950);save.invoke(activity);
                    assertEquals(540, retained.width);assertEquals(950, retained.height);
                    ready.setBoolean(activity, false);background.set(activity, null);sessionField.set(activity, null);
                } catch (ReflectiveOperationException error) {throw new AssertionError(error);}
                finally {session.close();}
            });
        }
    }

    @Test public void pinchAndPanTransformVideoBeforeHitTesting() {
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            RemoteVideoView[] live = new RemoteVideoView[1];
            NativeSession[] sessions = new NativeSession[1];
            scenario.onActivity(a -> {
                try {
                    var field = MainActivity.class.getDeclaredField("privacyDialog");
                    field.setAccessible(true); Dialog dialog = (Dialog)field.get(a);
                    if (dialog != null) dialog.dismiss();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                sessions[0] = new NativeSession(a, "localhost", 1, "127.0.0.1:1", "", a);
                live[0] = new RemoteVideoView(a, sessions[0]);
                live[0].setMode(TouchMode.ALBUM);
                a.setContentView(live[0]);
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                var view = live[0]; var surface = view.getChildAt(0);
                float cx = view.getWidth()/2f, cy = view.getHeight()/2f, gap = cx/6;
                assertTrue("video laid out", gap > 8 && surface.getWidth() > 0);
                int[] taps = {0}; float[] target = new float[2];
                view.onTap = (x,y) -> { ++taps[0]; target[0]=x; target[1]=y; };
                long down = SystemClock.uptimeMillis();
                touch(view,down,MotionEvent.ACTION_DOWN,cy,cx-gap);
                touch(view,down,MotionEvent.ACTION_POINTER_DOWN | (1<<8),cy,cx-gap,cx+gap);
                touch(view,down,MotionEvent.ACTION_MOVE,cy,cx-2*gap,cx+2*gap);
                touch(view,down,MotionEvent.ACTION_POINTER_UP | (1<<8),cy,cx-2*gap,cx+2*gap);
                touch(view,down,MotionEvent.ACTION_UP,cy,cx-2*gap);
                assertEquals(2,surface.getScaleX(),.02);
                down = SystemClock.uptimeMillis();
                touch(view,down,MotionEvent.ACTION_DOWN,cy,cx);
                touch(view,down,MotionEvent.ACTION_MOVE,cy,cx+gap);
                touch(view,down,MotionEvent.ACTION_UP,cy,cx+gap);
                assertEquals(gap,surface.getTranslationX(),1);
                assertEquals(0,taps[0]);
                down = SystemClock.uptimeMillis();
                touch(view,down,MotionEvent.ACTION_DOWN,cy,cx+gap);
                touch(view,down,MotionEvent.ACTION_UP,cy,cx+gap);
                assertEquals(1,taps[0]); assertEquals(.5,target[0],.005); assertEquals(.5,target[1],.005);
                view.switchDisplay(1);
                assertNotSame("the previous desktop buffer must leave the view on screen change",
                        surface, view.getChildAt(0));
                // Detach the Surface before ending its test-only session.
                a.setContentView(new View(a)); sessions[0].close();
            });
        }
    }
}
