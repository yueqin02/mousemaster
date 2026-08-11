package mousemaster.platform.mac;

import io.qt.core.Qt;
import io.qt.widgets.QWidget;
import mousemaster.Indicator;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.renderer.IndicatorRenderer;

import java.time.Duration;

/**
 * The mode indicator window on macOS, wrapping the cross-platform
 * {@link IndicatorRenderer}.
 * <p>
 * Kept in its own class so that {@link MacOverlay} can still be loaded when Qt
 * is unavailable: the io.qt classes are only resolved once this class is
 * instantiated, which MacOverlay only does when Qt initialized successfully.
 */
class MacIndicatorWindow {

    /**
     * The macOS arrow cursor, in points. Only used to offset the indicator so
     * that it does not sit under the cursor; an approximation is enough.
     */
    private static final int cursorWidth = 17;
    private static final int cursorHeight = 24;

    private final IndicatorRenderer renderer = new IndicatorRenderer();

    MacIndicatorWindow() {
        QWidget window = renderer.window();
        // Float above everything, never take focus, and let input through to
        // whatever is underneath. Qt.WindowType.Tool is deliberately not used:
        // macOS hides tool windows whenever their application is not the active
        // one, and mousemaster never activates.
        //
        // WindowTransparentForInput is what actually makes the window ignore
        // input at the window-server level. The indicator sits right on top of
        // the cursor, so without it the window swallows scroll wheel events
        // meant for the application underneath.
        window.setWindowFlags(Qt.WindowType.FramelessWindowHint,
                Qt.WindowType.WindowStaysOnTopHint,
                Qt.WindowType.WindowDoesNotAcceptFocus,
                Qt.WindowType.WindowTransparentForInput);
        window.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        window.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating);
    }

    void preWarm() {
        renderer.preWarm();
    }

    void advanceAnimationsToFirstFrame() {
        renderer.advanceAnimationsToFirstFrame();
    }

    boolean showing() {
        return renderer.showing();
    }

    void raise() {
        renderer.window().raise();
    }

    /**
     * The fade animation is forced off: its QVariantAnimation never emits a value
     * on macOS, so a fade-in leaves the window at opacity 0 (invisible indicator)
     * and a fade-out never completes (indicator stuck on screen showing the mode
     * you just left). Showing and hiding immediately is correct in both cases.
     */
    void setIndicator(Indicator indicator, Duration fadeAnimationDuration,
                      Point mousePosition, Screen activeScreen) {
        if (renderer.showing() && indicator.equals(renderer.currentIndicator()))
            return;
        renderer.setIndicator(indicator, false, fadeAnimationDuration, false,
                mouseRectangle(mousePosition), cursorVisualCenter(), activeScreen,
                null);
    }

    void reposition(Point mousePosition, Screen activeScreen) {
        if (renderer.currentIndicator() == null)
            return;
        renderer.reposition(mouseRectangle(mousePosition), cursorVisualCenter(),
                activeScreen, null);
    }

    void hide() {
        renderer.hide(false);
    }

    private static Rectangle mouseRectangle(Point mousePosition) {
        return new Rectangle((int) mousePosition.x(), (int) mousePosition.y(),
                cursorWidth, cursorHeight);
    }

    private static Point cursorVisualCenter() {
        return new Point(cursorWidth / 2.0, cursorHeight / 2.0);
    }

}
