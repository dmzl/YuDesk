package cn.crossdesk.mobile;

/** Touch navigation over the decoded picture. Only a tap or a latched scroll emits remote input. */
final class AlbumGesture {
    interface Output {
        void tap(float x, float y);
        void scroll(float x, float y, int direction);
    }

    private enum State { IDLE, TAP, PAN, TWO, ZOOM, SCROLL }
    private final RemoteViewport view;
    private final Output output;
    private final float slop, wheelStep;
    private State state = State.IDLE;
    private float startX, startY, lastX, lastY, lastSpan, scrollX, scrollY, wheelY;
    private boolean overImage;

    AlbumGesture(RemoteViewport view, Output output, float slop, float wheelStep) {
        this.view = view;
        this.output = output;
        this.slop = slop;
        this.wheelStep = wheelStep;
    }

    void down(float x, float y) {
        state = view.contains(x, y) ? State.TAP : State.IDLE;
        startX = lastX = x;
        startY = lastY = y;
    }

    void move(float x, float y) {
        if (state == State.TAP && Math.hypot(x - startX, y - startY) >= slop) state = State.PAN;
        if (state != State.PAN) return;
        if (view.isZoomed()) view.panAlbum(x - lastX, y - lastY);
        lastX = x;
        lastY = y;
    }

    void up(float x, float y) {
        move(x, y);
        if (state == State.TAP && view.contains(x, y)) output.tap(view.normalizedX(x), view.normalizedY(y));
        cancel();
    }

    void second(float x, float y, float span) {
        state = State.TWO;
        startX = lastX = x;
        startY = lastY = wheelY = y;
        lastSpan = Math.max(1f, span);
        overImage = view.contains(x, y);
        scrollX = view.normalizedX(x);
        scrollY = view.normalizedY(y);
    }

    void pair(float x, float y, float span) {
        if (state == State.TWO) {
            float spread = Math.abs(span - lastSpan);
            float travel = Math.abs(y - startY);
            if (spread >= slop && spread >= travel) state = State.ZOOM;
            else if (travel >= slop && overImage) state = State.SCROLL;
            else return;
        }
        if (state == State.ZOOM) {
            span = Math.max(1f, span);
            view.zoomBy(span / lastSpan, lastX, lastY);
            view.panAlbum(x - lastX, y - lastY);
            lastX = x;
            lastY = y;
            lastSpan = span;
        } else if (state == State.SCROLL) {
            int ticks = (int)((y - wheelY) / wheelStep);
            if (ticks == 0) return;
            wheelY += ticks * wheelStep;
            int direction = Integer.signum(ticks);
            for (int i = 0; i < Math.min(8, Math.abs(ticks)); ++i) output.scroll(scrollX, scrollY, direction);
        }
    }

    void cancel() { state = State.IDLE; }
}
