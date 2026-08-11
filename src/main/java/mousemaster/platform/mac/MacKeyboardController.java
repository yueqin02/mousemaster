package mousemaster.platform.mac;

import com.sun.jna.Pointer;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.MacroMoveDestination;
import mousemaster.ResolvedKeyMacroMove;
import mousemaster.ResolvedMacroMove;
import mousemaster.StringMacroMove;
import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static mousemaster.platform.mac.MacCoreGraphics.*;

/**
 * Injects keyboard events with CGEventPost. Much simpler than the Windows
 * version: CGEventCreateKeyboardEvent generates proper flagsChanged events for
 * modifier key codes, and posted events do not re-enter the event tap
 * synchronously, so no acknowledgment machinery is needed.
 */
public class MacKeyboardController implements KeyboardController {

    private static final Logger logger =
            LoggerFactory.getLogger(MacKeyboardController.class);

    /**
     * Event-source user data carried by every event mousemaster injects.
     * The event tap uses it to recognize mousemaster's own events.
     * "MMKB" = mousemaster keyboard (same value as on Windows).
     */
    public static final long MOUSEMASTER_INJECTED_EVENT_SIGNATURE = 0x4D4D4B42L;

    /** Generic flag bit | device-specific bit, per modifier key. */
    private static final Map<Key, Long> modifierMasks = Map.of(
            Key.leftshift, kCGEventFlagMaskShift | 0x2L,
            Key.rightshift, kCGEventFlagMaskShift | 0x4L,
            Key.leftctrl, kCGEventFlagMaskControl | 0x1L,
            Key.rightctrl, kCGEventFlagMaskControl | 0x2000L,
            Key.leftalt, kCGEventFlagMaskAlternate | 0x20L,
            Key.rightalt, kCGEventFlagMaskAlternate | 0x40L,
            Key.leftwin, kCGEventFlagMaskCommand | 0x8L,
            Key.rightwin, kCGEventFlagMaskCommand | 0x10L);

    public KeyboardLayout activeKeyboardLayout;

    private final Pointer eventSource;
    private final Set<Key> injectedPressedModifiers = new HashSet<>();
    private Key pressedKeyToRepeat;
    private double durationUntilNextKeyPressRepeat;
    private boolean repeatStartedDuringCurrentTick;

    public MacKeyboardController() {
        eventSource = INSTANCE.CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
        if (eventSource != null)
            INSTANCE.CGEventSourceSetUserData(eventSource,
                    MOUSEMASTER_INJECTED_EVENT_SIGNATURE);
    }

    @Override
    public void reset() {
        injectedPressedModifiers.clear();
        pressedKeyToRepeat = null;
        durationUntilNextKeyPressRepeat = 0;
        repeatStartedDuringCurrentTick = false;
    }

    @Override
    public void update(double delta) {
        repeatStartedDuringCurrentTick = false;
        if (pressedKeyToRepeat == null)
            return;
        durationUntilNextKeyPressRepeat -= delta;
        if (durationUntilNextKeyPressRepeat <= 0) {
            sendInputMoves(List.of(new ResolvedKeyMacroMove(pressedKeyToRepeat, true,
                    MacroMoveDestination.OS)), true);
            durationUntilNextKeyPressRepeat = 0.025d;
        }
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        for (ResolvedMacroMove move : moves) {
            switch (move) {
                case ResolvedKeyMacroMove keyMove -> sendKeyMove(keyMove, startRepeat);
                case StringMacroMove stringMove -> sendString(stringMove.string());
            }
        }
    }

    private void sendKeyMove(ResolvedKeyMacroMove move, boolean startRepeat) {
        Key key = move.key();
        int macKeyCode = MacVirtualKey.macKeyCodeFromKey(key, activeKeyboardLayout);
        if (macKeyCode == -1) {
            // Happens when a macro output contains a key not in the active keyboard layout.
            logger.debug("Cannot inject " + key + ": no macOS key code for it");
            return;
        }
        if (move.press()) {
            if (startRepeat) {
                pressedKeyToRepeat = key;
                durationUntilNextKeyPressRepeat = 0.5d;
                repeatStartedDuringCurrentTick = true;
            }
        }
        else if (key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
        if (modifierMasks.containsKey(key)) {
            if (move.press())
                injectedPressedModifiers.add(key);
            else
                injectedPressedModifiers.remove(key);
        }
        Pointer event = INSTANCE.CGEventCreateKeyboardEvent(eventSource,
                (short) macKeyCode, move.press());
        if (event == null) {
            logger.warn("Unable to create the key event for " + key);
            return;
        }
        INSTANCE.CGEventSetFlags(event, currentFlags());
        logger.trace("Sending " + move);
        INSTANCE.CGEventPost(kCGHIDEventTap, event);
        MacCoreFoundation.INSTANCE.CFRelease(event);
    }

    /**
     * Physical modifiers still visible to the OS, plus mousemaster's injected
     * modifiers that the OS does not track because the events come from a
     * private source state.
     */
    private long currentFlags() {
        long flags = INSTANCE.CGEventSourceFlagsState(
                kCGEventSourceStateCombinedSessionState);
        for (Key modifier : injectedPressedModifiers)
            flags |= modifierMasks.get(modifier);
        return flags;
    }

    private void sendString(String string) {
        logger.trace("Sending input string: " + string);
        for (int i = 0; i < string.length(); i++) {
            char[] character = {string.charAt(i)};
            for (boolean press : new boolean[]{true, false}) {
                Pointer event =
                        INSTANCE.CGEventCreateKeyboardEvent(eventSource, (short) 0,
                                press);
                if (event == null)
                    return;
                INSTANCE.CGEventKeyboardSetUnicodeString(event, 1, character);
                INSTANCE.CGEventPost(kCGHIDEventTap, event);
                MacCoreFoundation.INSTANCE.CFRelease(event);
            }
        }
    }

    @Override
    public void keyPressedNotEaten(Key key) {
        if (!repeatStartedDuringCurrentTick || !key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
        if (key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
        // No send queue on macOS: injected events are posted immediately.
    }

    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
    }

}
