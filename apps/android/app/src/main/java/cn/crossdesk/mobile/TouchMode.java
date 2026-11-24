package cn.crossdesk.mobile;

import android.content.Context;
import android.content.SharedPreferences;

enum TouchMode {
    ALBUM, RELATIVE, ABSOLUTE;

    static TouchMode read(SharedPreferences prefs) {
        String saved = prefs.getString("touchMode", "");
        for (TouchMode mode : values()) if (mode.name().equals(saved)) return mode;
        if (prefs.contains("relativeMouse")) return prefs.getBoolean("relativeMouse", true) ? RELATIVE : ABSOLUTE;
        return ALBUM;
    }

    String label(Context context) {
        return switch (this) {
            case ALBUM -> context.getString(R.string.yudesk_album);
            case RELATIVE -> L10n.tr("相对鼠标");
            case ABSOLUTE -> L10n.tr("绝对鼠标");
        };
    }

    static String[] labels(Context context) {
        return new String[]{ALBUM.label(context), RELATIVE.label(context), ABSOLUTE.label(context)};
    }

    TouchMode next() { return values()[(ordinal() + 1) % values().length]; }

    void save(SharedPreferences prefs) { prefs.edit().putString("touchMode", name()).apply(); }
}
