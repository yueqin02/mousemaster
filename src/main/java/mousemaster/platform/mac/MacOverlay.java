package mousemaster.platform.mac;

import mousemaster.Grid;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.Indicator;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.platform.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * No-op overlay: keyboard-driven mouse movement, clicking, scrolling and modes
 * all work, but nothing is drawn yet (no indicator, no grid lines, no hint
 * labels). Grid and hint *positions* still work since they are pure geometry.
 * A Qt-based overlay (reusing the cross-platform renderer package) is the next
 * step.
 */
public class MacOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(MacOverlay.class);

    private final MacMouseController mouse;
    private final MacScreens screens = new MacScreens();
    private boolean waitForZoomBeforeRepainting;
    private boolean warnedOnce;

    public MacOverlay(MacMouseController mouse) {
        this.mouse = mouse;
    }

    private void warnOnce() {
        if (warnedOnce)
            return;
        warnedOnce = true;
        logger.info("The macOS overlay is not implemented yet: " +
                    "indicator, grid lines and hint labels are not drawn");
    }

    @Override
    public void update(double delta) {
    }

    @Override
    public void flushCache() {
    }

    @Override
    public void setTopmost() {
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
        Point mousePosition = mouse.findMousePosition();
        Rectangle firstRectangle = null;
        for (Screen screen : screens.findScreens()) {
            if (firstRectangle == null)
                firstRectangle = screen.rectangle();
            if (mousePosition != null &&
                screen.rectangle().contains(mousePosition.x(), mousePosition.y()))
                return screen.rectangle();
        }
        return firstRectangle != null ? firstRectangle : new Rectangle(0, 0, 1, 1);
    }

    @Override
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                             Duration fadeAnimationDuration, boolean allowFade,
                             boolean renderAsCursor, boolean includeCursorGlyph) {
        warnOnce();
    }

    @Override
    public void hideIndicator(boolean allowFade) {
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
