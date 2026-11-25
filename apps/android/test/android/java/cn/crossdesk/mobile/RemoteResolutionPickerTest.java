package cn.crossdesk.mobile;

import android.app.AlertDialog;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import java.lang.reflect.Proxy;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteResolutionPickerTest {
    private static View control(View root, String word) {
        if (word.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription()))
            return root;
        if (root instanceof TextView && ((TextView)root).getText().toString().contains(word)) return root;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = control(group.getChildAt(i), word);
                if (child != null) return child;
            }
        return null;
    }

    @Test public void fixedAndFollowChoicesAreDistinctEvenAtSameDimensions() {
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    var consentField = MainActivity.class.getDeclaredField("privacyDialog");
                    consentField.setAccessible(true);
                    Dialog consent = (Dialog)consentField.get(activity);
                    if (consent != null) consent.dismiss();
                    boolean[] follow = {false};
                    var actions = (SessionControls.Actions)Proxy.newProxyInstance(
                            SessionControls.Actions.class.getClassLoader(), new Class[]{SessionControls.Actions.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("followsComputerResolution")) return follow[0];
                                if (method.getName().equals("displayResolution")) follow[0] = args[0] == null;
                                return null;
                            });
                    var controls = new SessionControls(activity, new MobileUi(activity), inset -> {}, actions);
                    activity.setContentView(controls);
                    var mode = new JSONObject().put("width", 540).put("height", 950);
                    var display = new JSONObject(mode.toString()).put("display_key", "phone")
                            .put("display_id", 0).put("modes", new JSONArray().put(mode));
                    var state = new RemoteDisplayResolutions();
                    state.receive(new JSONObject().put("type", 3).put("host_info",
                            new JSONObject().put("display_resolutions", new JSONArray().put(display))));
                    controls.displayResolutions(state, 0);
                    control(controls, L10n.tr("展开远程控制菜单")).performClick();
                    control(controls, L10n.tr("画面设置")).performClick();
                    var dialogField = SessionControls.class.getDeclaredField("resolutionDialog");
                    dialogField.setAccessible(true);
                    for (int position : new int[]{0, 1}) {
                        control(controls, "540 × 950").performClick();
                        var list = ((AlertDialog)dialogField.get(controls)).getListView();
                        assertEquals(L10n.tr("跟随电脑当前分辨率"), list.getItemAtPosition(0));
                        assertEquals("540 × 950", list.getItemAtPosition(1));
                        assertEquals(1 - position, list.getCheckedItemPosition());
                        list.performItemClick(null, position, position);
                        assertEquals(position == 0, follow[0]);
                        controls.displayResolutions(state, 0);
                    }
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }
}
