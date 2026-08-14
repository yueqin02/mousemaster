package mousemaster.platform.mac;

import com.sun.jna.Pointer;
import mousemaster.Clock;
import mousemaster.HintMeshConfiguration;
import mousemaster.Key;
import mousemaster.KeyEvent;
import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import mousemaster.KeyRegurgitator;
import mousemaster.KeyboardLayout;
import mousemaster.KeyboardManager;
import mousemaster.Mode;
import mousemaster.ModeMap;
import mousemaster.MouseManager;
import mousemaster.MousePositionListener;
import mousemaster.Platform;
import mousemaster.Point;
import mousemaster.QtManager;
import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyboardController;
import mousemaster.platform.Overlay;
import mousemaster.platform.Screens;
import mousemaster.platform.UiAutomation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static mousemaster.platform.mac.MacCoreGraphics.*;

public class MacPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(MacPlatform.class);

    private final boolean keyRegurgitationEnabled;
    private final MacKeyboardController keyboard = new MacKeyboardController();
    private final MacMouseController mouse =
            new MacMouseController(this::mousePositionSet);
    private final Screens screens = new MacScreens();
    private final MacOverlay overlay = new MacOverlay(mouse);
    private final UiAutomation uiAutomation = new MacUiAutomation();
    private final ActiveAppFinder activeAppFinder = new MacActiveAppFinder();
    private final Console console = new MacConsole();
    private final KeyRegurgitator keyRegurgitator = new KeyRegurgitator(keyboard);
    private final MacClock clock = new MacClock();
    private final KeyboardLayout defaultKeyboardLayout;

    private MouseManager mouseManager;
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private ModeMap modeMap;
    private KeyEvent lastKeyEvent;
    private final Set<Key> currentlyPressedNotEatenKeys = new HashSet<>();
    private final Set<Long> pressedModifierKeyCodes = new HashSet<>();
    private Point lastMousePosition;
    private Point setMousePosition;

    /**
     * Keep a reference to the callback: without it, JNA's callback stub can be
     * garbage collected and the tap stops working (same issue as the Windows
     * hooks).
     */
    private CGEventTapCallBack eventTapCallback;
    private Pointer eventTapMachPort;
    private Pointer eventTapRunLoopSource;
    /** Set by the tap callback, read by eventTapDelivers(). */
    private boolean eventTapDelivered;
    /** kVK_Option, the key the tap self test posts a release for. */
    private static final int leftAltMacKeyCode = 58;
    private static boolean shutdown = false;

    public MacPlatform(boolean multipleInstancesAllowed,
                       boolean keyRegurgitationEnabled,
                       boolean ignoreInjectedEvents) {
        this.keyRegurgitationEnabled = keyRegurgitationEnabled;
        defaultKeyboardLayout = KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty");
        if (!MacAccessibility.isTrusted(true)) {
            logger.error("mousemaster needs the Accessibility permission to grab the " +
                         "keyboard: System Settings > Privacy & Security > " +
                         "Accessibility, enable the app you launched mousemaster " +
                         "from (e.g. your terminal), then restart mousemaster. " +
                         "Waiting up to 120s for the permission to be granted...");
            long deadline = System.nanoTime() + 120_000_000_000L;
            while (!MacAccessibility.isTrusted(false)) {
                if (System.nanoTime() > deadline)
                    throw new IllegalStateException(
                            "Accessibility permission was not granted");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            logger.info("Accessibility permission granted");
        }
    }

    @Override
    public void update(double delta) {
        keyboard.update(delta);
        overlay.update(delta);
    }

    @Override
    public void pumpEvents() {
        while (MacCoreFoundation.INSTANCE.CFRunLoopRunInMode(
                MacCoreFoundation.kCFRunLoopDefaultMode, 0, true) ==
               MacCoreFoundation.kCFRunLoopRunHandledSource) {
            // Drain all ready run loop sources (the event tap is one of them).
        }
    }

    @Override
    public void sleep() throws InterruptedException {
        pumpEvents();
        Point mousePosition = setMousePosition != null ? setMousePosition :
                mouse.findMousePosition();
        setMousePosition = null;
        if (mousePosition != null && (lastMousePosition == null ||
                                      mousePosition.x() != lastMousePosition.x() ||
                                      mousePosition.y() != lastMousePosition.y())) {
            lastMousePosition = mousePosition;
            for (MousePositionListener listener : mousePositionListeners)
                listener.mouseMoved((int) mousePosition.x(), (int) mousePosition.y());
        }
        pumpEvents();
        long sleepMillis = overlay.hintTransitionAnimating() ? 1 : 10;
        long beforeTime = System.nanoTime();
        while (true) {
            long currentTime = System.nanoTime();
            if ((currentTime - beforeTime) / 1e6 >= sleepMillis)
                break;
            Thread.sleep(1);
            pumpEvents();
        }
    }

    @Override
    public void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
                      ModeMap newModeMap,
                      List<MousePositionListener> mousePositionListeners,
                      KeyboardLayout activeKeyboardLayout) {
        ModeMap oldModeMap = this.modeMap;
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
        this.mousePositionListeners = mousePositionListeners;
        if (keyboard.activeKeyboardLayout != null &&
            !keyboard.activeKeyboardLayout.equals(activeKeyboardLayout)) {
            keyboardManager.reset();
            keyboard.reset();
        }
        keyboard.activeKeyboardLayout = activeKeyboardLayout;
        Set<HintMeshConfiguration> newHintMeshConfigurations = new HashSet<>();
        for (Mode mode : newModeMap.modes())
            newHintMeshConfigurations.add(mode.hintMesh());
        if (oldModeMap != null)
            overlay.flushCache();
        overlay.preWarmFontStyles(newHintMeshConfigurations);
        this.modeMap = newModeMap;
        Point mousePosition = mouse.findMousePosition();
        if (mousePosition != null) {
            for (MousePositionListener mousePositionListener : mousePositionListeners)
                mousePositionListener.mouseMoved((int) mousePosition.x(),
                        (int) mousePosition.y());
        }
        overlay.setMessagePump(this::pumpEvents);
        if (eventTapCallback == null) {
            installEventTap();
            flashIndicator(newModeMap);
        }
    }

    /**
     * Shows the indicator briefly once the tap is known to work. mousemaster has
     * no window, no dock icon and no menu bar item, so otherwise nothing at all
     * tells you it came up. This confirms startup only; a later death is covered
     * by the tap self test and launchd's KeepAlive.
     */
    private void flashIndicator(ModeMap modeMap) {
        Mode mode = modeMap.modes()
                           .stream()
                           .filter(candidate -> candidate.indicator().enabled())
                           .findFirst()
                           .orElse(null);
        if (mode == null)
            return;
        overlay.setIndicator(mode.indicator().idleIndicator(), false, Duration.ZERO,
                false, false, false);
        long deadline = System.nanoTime() + 700_000_000L;
        while (System.nanoTime() < deadline) {
            QtManager.processEvents();
            overlay.update(0);
            pumpEvents();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        overlay.hideIndicator(false);
        QtManager.processEvents();
    }

    private void installEventTap() {
        createEventTap();
        // CGEventTapCreate can hand back a valid port that never delivers
        // anything. Observed after a launchd restart: the process looked
        // perfectly healthy, logged no error, and every shortcut was dead.
        for (int attempt = 1; !eventTapDelivers(); attempt++) {
            if (attempt == 3)
                // Exiting is the last resort that works: launchd's KeepAlive
                // starts a whole new process, which has always come up healthy.
                throw new IllegalStateException(
                        "The keyboard event tap was created but does not deliver " +
                        "any event, after " + attempt + " attempts");
            logger.warn("The keyboard event tap does not deliver any event, " +
                        "reinstalling it");
            disableEventTap();
            createEventTap();
        }
        logger.trace("Installed keyboard event tap successfully");
        // Registered once, not per createEventTap() call: shutdown() is what the
        // hook runs and reinstalling the tap does not need another one.
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void createEventTap() {
        long eventMask = (1L << kCGEventKeyDown) | (1L << kCGEventKeyUp) |
                         (1L << kCGEventFlagsChanged);
        eventTapCallback = this::eventTapCallback;
        eventTapMachPort = INSTANCE.CGEventTapCreate(kCGSessionEventTap,
                kCGHeadInsertEventTap, kCGEventTapOptionDefault, eventMask,
                eventTapCallback, null);
        if (eventTapMachPort == null)
            throw new IllegalStateException(
                    "Unable to create the keyboard event tap. Make sure the app " +
                    "mousemaster runs from has the Accessibility permission " +
                    "(System Settings > Privacy & Security > Accessibility)");
        eventTapRunLoopSource = MacCoreFoundation.INSTANCE.CFMachPortCreateRunLoopSource(
                null, eventTapMachPort, 0);
        MacCoreFoundation.INSTANCE.CFRunLoopAddSource(
                MacCoreFoundation.INSTANCE.CFRunLoopGetCurrent(), eventTapRunLoopSource,
                MacCoreFoundation.kCFRunLoopDefaultMode);
        INSTANCE.CGEventTapEnable(eventTapMachPort, true);
    }

    /**
     * Posts an event the tap callback recognizes as mousemaster's own, and so
     * discards, and waits for the callback to run. The release of a modifier
     * that is not held changes nothing for the app underneath.
     */
    private boolean eventTapDelivers() {
        eventTapDelivered = false;
        Pointer event = INSTANCE.CGEventCreateKeyboardEvent(mouse.eventSource(),
                (short) leftAltMacKeyCode, false);
        if (event == null)
            return false;
        INSTANCE.CGEventPost(kCGHIDEventTap, event);
        MacCoreFoundation.INSTANCE.CFRelease(event);
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (!eventTapDelivered && System.nanoTime() < deadline) {
            pumpEvents();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return eventTapDelivered;
    }

    private Pointer eventTapCallback(Pointer proxy, int type, Pointer event,
                                     Pointer userInfo) {
        eventTapDelivered = true;
        clock.setLastKeyboardHookEventTime(Instant.now());
        try {
            if (type == kCGEventTapDisabledByTimeout ||
                type == kCGEventTapDisabledByUserInput) {
                logger.debug("Event tap was disabled (type = " + type +
                             "), re-enabling it");
                INSTANCE.CGEventTapEnable(eventTapMachPort, true);
                return event;
            }
            boolean injected = INSTANCE.CGEventGetIntegerValueField(event,
                    kCGEventSourceUserData) ==
                               MacKeyboardController.MOUSEMASTER_INJECTED_EVENT_SIGNATURE;
            if (logger.isTraceEnabled())
                logger.trace("Received raw event: type = " + type + ", keyCode = " +
                             INSTANCE.CGEventGetIntegerValueField(event,
                                     kCGKeyboardEventKeycode) +
                             (injected ? ", injected" : ""));
            if (injected)
                // mousemaster's own injected events (macros, regurgitation, key
                // repeat) must never be fed back into combo processing.
                return event;
            KeyEvent keyEvent = buildKeyEvent(type, event);
            if (keyEvent == null)
                return event;
            if (logger.isTraceEnabled())
                logger.trace("Received key event: " + keyEvent);
            if (keyboardManager == null)
                return event;
            boolean eaten = processKeyEvent(keyEvent);
            return eaten ? null : event;
        } catch (Throwable e) {
            // Never let an exception cross the JNA callback boundary silently:
            // rethrow so Native.setCallbackExceptionHandler sees it.
            throw e;
        } finally {
            clock.keyboardHookEventHandled();
        }
    }

    private KeyEvent buildKeyEvent(int type, Pointer event) {
        long macKeyCode =
                INSTANCE.CGEventGetIntegerValueField(event, kCGKeyboardEventKeycode);
        boolean release;
        if (type == kCGEventFlagsChanged) {
            // Modifier keys come in as flagsChanged events with no up/down
            // information: track pressed modifiers to derive the direction.
            // Caps lock (57) and fn (63) are excluded: caps lock is a toggle and
            // fn has no Windows equivalent; both are passed through.
            if (macKeyCode == 57 || macKeyCode == 63)
                return null;
            if (pressedModifierKeyCodes.contains(macKeyCode)) {
                pressedModifierKeyCodes.remove(macKeyCode);
                release = true;
            }
            else {
                pressedModifierKeyCodes.add(macKeyCode);
                release = false;
            }
        }
        else
            release = type == kCGEventKeyUp;
        Key key = MacVirtualKey.keyFromMacKeyCode((int) macKeyCode,
                keyboard.activeKeyboardLayout == null ? defaultKeyboardLayout :
                        keyboard.activeKeyboardLayout);
        if (key == null)
            return null;
        Instant time = clock.now();
        return release ? new ReleaseKeyEvent(time, key) : new PressKeyEvent(time, key);
    }

    /**
     * @return true if the event should be eaten.
     */
    private boolean processKeyEvent(KeyEvent keyEvent) {
        if (lastKeyEvent != null && lastKeyEvent.equals(keyEvent)) {
            logger.debug("Key event ignored because it is equal to the last event: " +
                         keyEvent);
            return false;
        }
        lastKeyEvent = keyEvent;
        if (keyEvent.isRelease())
            currentlyPressedNotEatenKeys.remove(keyEvent.key());
        KeyboardManager.EatAndRegurgitates eatAndRegurgitates =
                keyboardManager.keyEvent(keyEvent);
        if (keyEvent.isPress() && !eatAndRegurgitates.mustBeEaten())
            currentlyPressedNotEatenKeys.add(keyEvent.key());
        boolean eventKeyReleasedByRegurgitation = false;
        if (keyRegurgitationEnabled && !eatAndRegurgitates.regurgitates().isEmpty()) {
            for (KeyboardManager.Regurgitate regurgitate : eatAndRegurgitates.regurgitates()) {
                if (regurgitate.alsoRelease() &&
                    regurgitate.key().equals(keyEvent.key()) && keyEvent.isRelease())
                    eventKeyReleasedByRegurgitation = true;
                boolean startRepeat = !regurgitate.alsoRelease() &&
                                      !keyEvent.isRelease() &&
                                      currentlyPressedNotEatenKeys.isEmpty();
                keyRegurgitator.regurgitate(regurgitate, startRepeat);
                if (!regurgitate.alsoRelease())
                    currentlyPressedNotEatenKeys.add(regurgitate.key());
            }
        }
        return eatAndRegurgitates.mustBeEaten() || eventKeyReleasedByRegurgitation;
    }

    private void mousePositionSet(Point mousePosition) {
        setMousePosition = mousePosition;
    }

    @Override
    public void shutdown() {
        if (shutdown)
            return;
        shutdown = true;
        mouse.showCursor(); // Just in case we are shutting down while cursor is hidden.
        if (disableEventTap())
            logger.info("Disabled keyboard event tap");
    }

    private boolean disableEventTap() {
        if (eventTapMachPort == null)
            return false;
        INSTANCE.CGEventTapEnable(eventTapMachPort, false);
        if (eventTapRunLoopSource != null)
            MacCoreFoundation.INSTANCE.CFRunLoopRemoveSource(
                    MacCoreFoundation.INSTANCE.CFRunLoopGetCurrent(),
                    eventTapRunLoopSource, MacCoreFoundation.kCFRunLoopDefaultMode);
        return true;
    }

    @Override
    public void killProcess(int exitCode) {
        shutdown();
        Runtime.getRuntime().halt(exitCode);
    }

    @Override
    public KeyRegurgitator keyRegurgitator() {
        return keyRegurgitator;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public KeyboardLayout activeKeyboardLayout() {
        // TODO Detect the active input source (TISCopyCurrentKeyboardInputSource)
        //  and map it to a layout. Until then, us-qwerty; use
        //  keyboard.layout=<shortName> in the configuration to override.
        return defaultKeyboardLayout;
    }

    @Override
    public KeyboardController keyboard() {
        return keyboard;
    }

    @Override
    public mousemaster.platform.MouseController mouse() {
        return mouse;
    }

    @Override
    public Screens screens() {
        return screens;
    }

    @Override
    public Overlay overlay() {
        return overlay;
    }

    @Override
    public UiAutomation uiAutomation() {
        return uiAutomation;
    }

    @Override
    public ActiveAppFinder activeAppFinder() {
        return activeAppFinder;
    }

    @Override
    public Console console() {
        return console;
    }

    @Override
    public void modeChanged(Mode newMode) {
        // The Windows implementation coordinates zoom repaints here; the macOS
        // overlay does not support zoom yet.
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}
