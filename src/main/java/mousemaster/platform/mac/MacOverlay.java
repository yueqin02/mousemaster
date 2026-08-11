package mousemaster.platform.mac;

import mousemaster.Grid;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.Indicator;
import mousemaster.Point;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.platform.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * Partial overlay: the mode indicator is drawn (via Qt, when it is available),
 * but grid lines and hint labels are not yet. Grid and hint *positions* still
 * work since they are pure geometry.
 */
public class MacOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(MacOverlay.class);

    private final MacMouseController mouse;
    private final MacScreens screens = new MacScreens();
    private boolean waitForZoomBeforeRepainting;
    private boolean warnedOnce;
    private MacIndicatorWindow indicatorWindow;

    public MacOverlay(MacMouseController mouse) {
        this.mouse = mouse;
    }

    /**
     * Null when Qt is unavailable, in which case nothing is drawn. MacIndicatorWindow
     * is only referenced from here so that its io.qt classes are never resolved
     * in that case.
     */
    private MacIndicatorWindow indicatorWindow() {
        if (indicatorWindow == null && QtManager.qtAvailable()) {
            indicatorWindow = new MacIndicatorWindow();
            logger.info("Created the mode indicator window");
        }
        return indicatorWindow;
    }

    private void warnOnce() {
        if (warnedOnce)
            return;
        warnedOnce = true;
        logger.info("The macOS overlay only draws the mode indicator: " +
                    "grid lines and hint labels are not drawn");
    }

    @Override
    public void update(double delta) {
        if (indicatorWindow == null || !indicatorWindow.showing())
            return;
        indicatorWindow.advanceAnimationsToFirstFrame();
        Point mousePosition = mouse.findMousePosition();
        if (mousePosition != null)
            indicatorWindow.reposition(mousePosition, activeScreen(mousePosition));
    }

    @Override
    public void flushCache() {
    }

    @Override
    public void setTopmost() {
        if (indicatorWindow != null)
            indicatorWindow.raise();
    }

    @Override
    public void setMessagePump(Runnable pump) {
    }

    @Override
    public void preWarmFontStyles(Set<HintMeshConfiguration> configs) {
    }

    @Override
    public void preWarmHintMeshWindows() {
    }

    @Override
    public void preWarmIndicatorWindow() {
        MacIndicatorWindow window = indicatorWindow();
        if (window != null)
            window.preWarm();
    }

    /**
     * Frontmost-window bounds are not available without per-window AX queries;
     * fall back to the bounds of the screen the mouse is on.
     */
    @Override
    public Rectangle activeWindowRectangle(double widthPct, double heightPct,
                                           int topInset, int bottomInset, int leftInset,
                                           int rightInset) {
        Rectangle screenRectangle = activeScreenRectangle();
        int windowWidth = screenRectangle.width();
        int windowHeight = screenRectangle.height();
        int noInsetGridWidth = Math.max(1, (int) (windowWidth * widthPct));
        int gridWidth = Math.max(1, noInsetGridWidth - leftInset - rightInset);
        int noInsetGridHeight = Math.max(1, (int) (windowHeight * heightPct));
        int gridHeight = Math.max(1, noInsetGridHeight - topInset - bottomInset);
        return new Rectangle(
                screenRectangle.x() + leftInset + (windowWidth - noInsetGridWidth) / 2,
                screenRectangle.y() + topInset + (windowHeight - noInsetGridHeight) / 2,
                gridWidth, gridHeight);
    }

    private Rectangle activeScreenRectangle() {
        return activeScreen(mouse.findMousePosition()).rectangle();
    }

    /** The screen the given mouse position is on, or the first screen. */
    private Screen activeScreen(Point mousePosition) {
        Screen firstScreen = null;
        for (Screen screen : screens.findScreens()) {
            if (firstScreen == null)
                firstScreen = screen;
            if (mousePosition != null &&
                screen.rectangle().contains(mousePosition.x(), mousePosition.y()))
                return screen;
        }
        return firstScreen != null ? firstScreen :
                new Screen(new Rectangle(0, 0, 1, 1), 96, 1.0d);
    }

    /**
     * renderAsCursor is ignored: replacing the system cursor image is not
     * implemented on macOS, so the indicator is always drawn as its own window.
     */
    @Override
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                             Duration fadeAnimationDuration, boolean allowFade,
                             boolean renderAsCursor, boolean includeCursorGlyph) {
        MacIndicatorWindow window = indicatorWindow();
        if (window == null) {
            warnOnce();
            return;
        }
        Point mousePosition = mouse.findMousePosition();
        if (mousePosition == null) {
            logger.warn("Unable to find mouse position for indicator");
            return;
        }
        window.setIndicator(indicator, fadeAnimationDuration, mousePosition,
                activeScreen(mousePosition));
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        if (indicatorWindow != null)
            indicatorWindow.hide();
    }

    @Override
    public void setGrid(Grid grid) {
        warnOnce();
    }

    @Override
    public void hideGrid() {
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        warnOnce();
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        warnOnce();
    }

    @Override
    public void hideHintMesh() {
    }

    @Override
    public boolean hintTransitionAnimating() {
        return false;
    }

    @Override
    public void animateHintMatch(Hint hint) {
    }

    @Override
    public void setZoom(Zoom zoom) {
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) {
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return waitForZoomBeforeRepainting;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
        waitForZoomBeforeRepainting = value;
    }

}
