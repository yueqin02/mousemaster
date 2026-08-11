package mousemaster.platform.mac;

import com.sun.jna.Pointer;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.platform.MouseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static mousemaster.platform.mac.MacCoreGraphics.*;

public class MacMouseController implements MouseController {

    private static final Logger logger = LoggerFactory.getLogger(MacMouseController.class);

    private final Consumer<Point> mousePositionSetCallback;
    private final Pointer eventSource;
    private boolean leftPressed;
    private boolean middlePressed;
    private boolean rightPressed;
    private boolean cursorHidden;
    private boolean moving;
    /**
     * Position accumulated across moveBy() calls during a move session. Posted
     * mouse events take longer than one main-loop tick to be reflected by
     * CGEventGetLocation, so re-reading the actual position every tick would
     * lose most of the movement.
     */
    private Point movePosition;

    public MacMouseController(Consumer<Point> mousePositionSetCallback) {
        this.mousePositionSetCallback = mousePositionSetCallback;
        eventSource = INSTANCE.CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
        if (eventSource != null)
            INSTANCE.CGEventSourceSetUserData(eventSource,
                    MacKeyboardController.MOUSEMASTER_INJECTED_EVENT_SIGNATURE);
    }

    public Pointer eventSource() {
        return eventSource;
    }

    public Point findMousePosition() {
        Pointer event = INSTANCE.CGEventCreate(null);
        if (event == null)
            return null;
        CGPoint.ByValue location = INSTANCE.CGEventGetLocation(event);
        MacCoreFoundation.INSTANCE.CFRelease(event);
        return new Point(location.x, location.y);
    }

    @Override
    public void beginMove() {
        if (moving)
            return;
        moving = true;
        movePosition = findMousePosition();
    }

    @Override
    public void endMove() {
        moving = false;
        movePosition = null;
    }

    @Override
    public void moveBy(boolean xForward, double deltaX, boolean yForward,
                       double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        Point position = moving && movePosition != null ? movePosition :
                findMousePosition();
        if (position == null)
            return;
        double newX = position.x() + (long) deltaX * (xForward ? 1 : -1);
        double newY = position.y() + (long) deltaY * (yForward ? 1 : -1);
        Point clamped = clampToScreens(newX, newY);
        if (moving)
            movePosition = clamped;
        postMoveEvent(clamped.x(), clamped.y());
    }

    /**
     * Without clamping, the accumulated position would keep running past the
     * screen edge while the real cursor is stuck at the edge, and reversing
     * direction would have a dead delay.
     */
    private Point clampToScreens(double x, double y) {
        Rectangle closestRectangle = null;
        double closestDistance = Double.MAX_VALUE;
        for (Screen screen : new MacScreens().findScreens()) {
            Rectangle rectangle = screen.rectangle();
            if (rectangle.contains(x, y))
                return new Point(x, y);
            double distance = Rectangle.rectangleEdgeDistanceTo(rectangle.x(),
                    rectangle.y(), rectangle.width(), rectangle.height(), x, y);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRectangle = rectangle;
            }
        }
        if (closestRectangle == null)
            return new Point(x, y);
        return new Point(
                Math.max(closestRectangle.x(),
                        Math.min(x, closestRectangle.x() + closestRectangle.width() - 1)),
                Math.max(closestRectangle.y(),
                        Math.min(y,
                                closestRectangle.y() + closestRectangle.height() - 1)));
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        if (moving)
            movePosition = new Point(x, y);
        postMoveEvent(x, y);
        mousePositionSetCallback.accept(new Point(x, y));
    }

    private void postMoveEvent(double x, double y) {
        int type;
        int button;
        if (leftPressed) {
            type = kCGEventLeftMouseDragged;
            button = kCGMouseButtonLeft;
        }
        else if (rightPressed) {
            type = kCGEventRightMouseDragged;
            button = kCGMouseButtonRight;
        }
        else if (middlePressed) {
            type = kCGEventOtherMouseDragged;
            button = kCGMouseButtonCenter;
        }
        else {
            type = kCGEventMouseMoved;
            button = kCGMouseButtonLeft;
        }
        postMouseEvent(type, button, x, y, 0);
    }

    /** macOS default; the two clicks of a double click must be closer than this. */
    private static final long doubleClickIntervalNanos = 500_000_000L;
    /** A double click must not wander further than this, in points. */
    private static final double doubleClickSlop = 5;

    private int lastClickButton = -1;
    private long lastClickNanos;
    private Point lastClickPosition;
    private int clickState = 1;

    /**
     * Consecutive clicks must be posted with an increasing click state, otherwise
     * the system reads them as separate single clicks and nothing that needs a
     * double click (opening a folder, YouTube's fullscreen toggle) reacts.
     */
    private void postButtonEvent(int type, int button, boolean press) {
        Point position = findMousePosition();
        if (position == null)
            return;
        if (press) {
            long now = System.nanoTime();
            boolean continuesClick = button == lastClickButton &&
                                     now - lastClickNanos < doubleClickIntervalNanos &&
                                     lastClickPosition != null &&
                                     Math.abs(position.x() - lastClickPosition.x()) <=
                                     doubleClickSlop &&
                                     Math.abs(position.y() - lastClickPosition.y()) <=
                                     doubleClickSlop;
            // Triple click is the highest state macOS distinguishes.
            clickState = continuesClick ? Math.min(clickState + 1, 3) : 1;
            lastClickButton = button;
            lastClickNanos = now;
            lastClickPosition = position;
        }
        postMouseEvent(type, button, position.x(), position.y(), clickState);
    }

    private void postMouseEvent(int type, int button, double x, double y,
                                int clickState) {
        Pointer event = INSTANCE.CGEventCreateMouseEvent(eventSource, type,
                new CGPoint.ByValue(x, y), button);
        if (event == null)
            return;
        if (clickState != 0)
            INSTANCE.CGEventSetIntegerValueField(event, kCGMouseEventClickState,
                    clickState);
        INSTANCE.CGEventPost(kCGHIDEventTap, event);
        MacCoreFoundation.INSTANCE.CFRelease(event);
    }

    @Override
    public void pressLeft() {
        leftPressed = true;
        postButtonEvent(kCGEventLeftMouseDown, kCGMouseButtonLeft, true);
    }

    @Override
    public void pressMiddle() {
        middlePressed = true;
        postButtonEvent(kCGEventOtherMouseDown, kCGMouseButtonCenter, true);
    }

    @Override
    public void pressRight() {
        rightPressed = true;
        postButtonEvent(kCGEventRightMouseDown, kCGMouseButtonRight, true);
    }

    @Override
    public void releaseLeft() {
        leftPressed = false;
        postButtonEvent(kCGEventLeftMouseUp, kCGMouseButtonLeft, false);
    }

    @Override
    public void releaseMiddle() {
        middlePressed = false;
        postButtonEvent(kCGEventOtherMouseUp, kCGMouseButtonCenter, false);
    }

    @Override
    public void releaseRight() {
        rightPressed = false;
        postButtonEvent(kCGEventRightMouseUp, kCGMouseButtonRight, false);
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        postWheelEvent(0, (int) delta * (forward ? -1 : 1));
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        postWheelEvent((int) delta * (forward ? -1 : 1), 0);
    }

    private void postWheelEvent(int vertical, int horizontal) {
        Pointer event = INSTANCE.CGEventCreateScrollWheelEvent(eventSource,
                kCGScrollEventUnitPixel, 2, vertical, horizontal);
        if (event == null) {
            logger.warn("Unable to create the scroll wheel event");
            return;
        }
        INSTANCE.CGEventPost(kCGHIDEventTap, event);
        MacCoreFoundation.INSTANCE.CFRelease(event);
    }

    @Override
    public void showCursor() {
        if (!cursorHidden)
            return;
        cursorHidden = false;
        INSTANCE.CGDisplayShowCursor(0);
    }

    @Override
    public void hideCursor() {
        if (cursorHidden)
            return;
        cursorHidden = true;
        INSTANCE.CGDisplayHideCursor(0);
    }

}
