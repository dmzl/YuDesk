package cn.crossdesk.mobile;

/** UI-thread correlation for one foreground tap; a reply is consumed at most once. */
final class TapFeedback {
    private int sequence, pending, screen;
    private long started;

    int begin(int display, long now) {
        sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
        pending = sequence;
        screen = display;
        started = now;
        return pending;
    }

    void cancel() { pending = 0; }

    boolean accept(int id, int display, int currentDisplay, long now) {
        if (pending == 0 || pending != id || screen != display || screen != currentDisplay) return false;
        cancel();
        return now >= started && now - started <= 1500;
    }
}
