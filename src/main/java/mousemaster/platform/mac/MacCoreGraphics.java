package mousemaster.platform.mac;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

/**
 * CoreGraphics (Quartz Event Services) bindings: event tap, event injection,
 * display enumeration.
 */
public interface MacCoreGraphics extends Library {

    MacCoreGraphics INSTANCE = Native.load("CoreGraphics", MacCoreGraphics.class);

    // CGEventType
    int kCGEventLeftMouseDown = 1;
    int kCGEventLeftMouseUp = 2;
    int kCGEventRightMouseDown = 3;
    int kCGEventRightMouseUp = 4;
    int kCGEventMouseMoved = 5;
    int kCGEventLeftMouseDragged = 6;
    int kCGEventRightMouseDragged = 7;
    int kCGEventKeyDown = 10;
    int kCGEventKeyUp = 11;
    int kCGEventFlagsChanged = 12;
    int kCGEventScrollWheel = 22;
    int kCGEventOtherMouseDown = 25;
    int kCGEventOtherMouseUp = 26;
    int kCGEventOtherMouseDragged = 27;
    int kCGEventTapDisabledByTimeout = -2; // 0xFFFFFFFE
    int kCGEventTapDisabledByUserInput = -1; // 0xFFFFFFFF

    // CGMouseButton
    int kCGMouseButtonLeft = 0;
    int kCGMouseButtonRight = 1;
    int kCGMouseButtonCenter = 2;

    // CGEventTapLocation
    int kCGHIDEventTap = 0;
    int kCGSessionEventTap = 1;

    // CGEventTapPlacement
    int kCGHeadInsertEventTap = 0;

    // CGEventTapOptions
    int kCGEventTapOptionDefault = 0;

    // CGEventField
    int kCGMouseEventClickState = 1;
    int kCGKeyboardEventAutorepeat = 8;
    int kCGKeyboardEventKeycode = 9;
    int kCGEventSourceUserData = 42;

    // CGScrollEventUnit
    int kCGScrollEventUnitPixel = 0;

    // CGEventSourceStateID
    int kCGEventSourceStateCombinedSessionState = 0;
    int kCGEventSourceStateHIDSystemState = 1;

    // CGEventFlags: generic bit | device-specific bit.
    long kCGEventFlagMaskShift = 0x20000L;
    long kCGEventFlagMaskControl = 0x40000L;
    long kCGEventFlagMaskAlternate = 0x80000L;
    long kCGEventFlagMaskCommand = 0x100000L;

    interface CGEventTapCallBack extends Callback {
        Pointer callback(Pointer proxy, int type, Pointer event, Pointer userInfo);
    }

    @Structure.FieldOrder({"x", "y"})
    class CGPoint extends Structure {
        public double x;
        public double y;

        public CGPoint() {
        }

        public CGPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public static class ByValue extends CGPoint implements Structure.ByValue {
            public ByValue() {
            }

            public ByValue(double x, double y) {
                super(x, y);
            }
        }
    }

    @Structure.FieldOrder({"origin", "size"})
    class CGRect extends Structure {
        public CGPoint origin;
        public CGSize size;

        public static class ByValue extends CGRect implements Structure.ByValue {
        }
    }

    @Structure.FieldOrder({"width", "height"})
    class CGSize extends Structure {
        public double width;
        public double height;
    }

    Pointer CGEventTapCreate(int tap, int place, int options, long eventsOfInterest,
                             CGEventTapCallBack callback, Pointer userInfo);

    void CGEventTapEnable(Pointer machPort, boolean enable);

    boolean CGEventTapIsEnabled(Pointer machPort);

    long CGEventGetIntegerValueField(Pointer event, int field);

    void CGEventSetIntegerValueField(Pointer event, int field, long value);

    CGPoint.ByValue CGEventGetLocation(Pointer event);

    Pointer CGEventCreate(Pointer source);

    Pointer CGEventCreateMouseEvent(Pointer source, int mouseType,
                                    CGPoint.ByValue mouseCursorPosition, int mouseButton);

    Pointer CGEventCreateKeyboardEvent(Pointer source, short virtualKey, boolean keyDown);

    /**
     * Variadic after wheel1: pass extra wheel values in the varargs tail so JNA
     * uses the variadic ABI (mandatory on Apple Silicon).
     */
    Pointer CGEventCreateScrollWheelEvent(Pointer source, int units, int wheelCount,
                                          int wheel1, Object... otherWheels);

    void CGEventKeyboardSetUnicodeString(Pointer event, long stringLength,
                                         char[] unicodeString);

    void CGEventSetFlags(Pointer event, long flags);

    long CGEventSourceFlagsState(int stateID);

    Pointer CGEventSourceCreate(int stateID);

    void CGEventSourceSetUserData(Pointer source, long userData);

    void CGEventPost(int tapLocation, Pointer event);

    int CGGetActiveDisplayList(int maxDisplays, int[] activeDisplays,
                               IntByReference displayCount);

    CGRect.ByValue CGDisplayBounds(int display);

    Pointer CGDisplayCopyDisplayMode(int display);

    long CGDisplayModeGetPixelWidth(Pointer mode);

    long CGDisplayModeGetWidth(Pointer mode);

    void CGDisplayModeRelease(Pointer mode);

    int CGDisplayHideCursor(int display);

    int CGDisplayShowCursor(int display);

}
