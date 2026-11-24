package cn.crossdesk.mobile;

import java.util.function.BiFunction;

/** Main-thread local-app checks triggered by visits, session endings and manual requests. */
final class AppUpdateChecker {
    enum Status { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }
    private final String currentVersion;
    private final RemoteVersionCheck.Fetcher fetcher;
    private final BiFunction<String, byte[], String> compare;
    private final Runnable changed;
    private RemoteVersionCheck.Cancellation request;
    private boolean enabled;
    private long generation;
    Status status = Status.IDLE;
    String availableVersion = "";

    AppUpdateChecker(String currentVersion, Runnable changed) {
        this(currentVersion, callback -> RemoteVersionCheck.ReleaseRequest.start(
                "https://api.github.com/repos/dmzl/YuDesk/releases?per_page=1", callback),
                AppUpdateChecker::compareForkRelease, changed);
    }

    AppUpdateChecker(String currentVersion, RemoteVersionCheck.Fetcher fetcher,
                     BiFunction<String, byte[], String> compare, Runnable changed) {
        this.currentVersion = currentVersion; this.fetcher = fetcher;
        this.compare = compare;
        this.changed = changed;
    }

    private static String compareForkRelease(String current, byte[] data) {
        try {
            var releases = new org.json.JSONArray(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            if (releases.length() == 0) return "";
            var release = releases.getJSONObject(0);
            if (release.optBoolean("prerelease") || release.optBoolean("draft")) return "";
            String tag = release.getString("tag_name");
            var version = new org.json.JSONObject().put("version", tag);
            var downloads = new org.json.JSONObject().put("android-arm64", version);
            var manifest = new org.json.JSONObject().put("downloads", downloads);
            return NativeSession.nCheckMobileUpdate(current,
                    manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (org.json.JSONException invalid) {
            return null;
        }
    }

    boolean updateAvailable() { return !availableVersion.isEmpty(); }

    void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (enabled) { checkNow(); return; }
        ++generation;
        if (request != null) request.cancel();
        request = null;
        if (status == Status.CHECKING) {
            status = updateAvailable() ? Status.AVAILABLE : Status.IDLE;
        }
        changed.run();
    }

    void checkNow() {
        if (!enabled || status == Status.CHECKING) return;
        status = Status.CHECKING;
        changed.run();
        long token = ++generation;
        request = fetcher.fetch(data -> {
            if (!enabled || token != generation) return;
            request = null;
            String result = data == null ? null : compare.apply(currentVersion, data);
            if (result == null) {
                status = Status.FAILED;
            } else {
                availableVersion = result;
                status = updateAvailable() ? Status.AVAILABLE : Status.UP_TO_DATE;
            }
            changed.run();
        });
    }
}
