package cn.crossdesk.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlbumGestureTest implements AlbumGesture.Output {
    final RemoteViewport viewport = new RemoteViewport();
    final AlbumGesture gesture = new AlbumGesture(viewport, this, 6, 12);
    int taps, wheel;
    float remoteX, remoteY;
    public AlbumGestureTest() { viewport.fit(100, 100, 100, 100); }
    public void tap(float x,float y) { taps++; remoteX=x; remoteY=y; }
    public void scroll(float x,float y,int direction) { wheel+=direction; remoteX=x;remoteY=y; }

    @Test public void panningDoesNotClickAndNextTapUsesVisibleCoordinates() {
        viewport.zoomBy(2,50,50);
        gesture.down(30,40);
        gesture.move(50,40);
        gesture.up(50,40);
        assertEquals(20,viewport.offsetX(),.01);
        assertEquals(0,taps);
        gesture.down(70,50);
        gesture.up(70,50);
        assertEquals(1,taps);
        assertEquals(.5,remoteX,.001);
    }
    @Test public void scrollLocksAtOriginalTargetEvenWhenZoomed() {
        viewport.zoomBy(2,50,50);
        gesture.down(20,40);
        gesture.second(30,40,20);
        gesture.pair(30,57,20);
        gesture.pair(30,61,35);
        gesture.cancel();
        gesture.up(30,61);
        assertTrue(wheel>0);
        assertEquals(.4,remoteX,.001);
        assertEquals(.45,remoteY,.001);
        assertEquals(2,viewport.scale(),.001);
        assertEquals(0,taps);
    }
    @Test public void pinchSuppressesScrollAndTrailingTap() {
        gesture.down(40,50);
        gesture.second(50,50,20);
        gesture.pair(50,50,40);
        gesture.pair(50,70,40);
        gesture.cancel();
        gesture.up(50,70);
        assertEquals(2,viewport.scale(),.001);
        assertEquals(0,wheel);
        assertEquals(0,taps);
    }
}
