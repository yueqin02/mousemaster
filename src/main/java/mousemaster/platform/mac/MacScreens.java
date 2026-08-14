package mousemaster.platform.mac;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.platform.Screens;

import java.util.HashSet;
import java.util.Set;

public class MacScreens implements Screens {

    /**
     * Static because callers create a MacScreens per call. See findScreens().
     */
    private static Set<Screen> lastKnownScreens = Set.of();

    /**
     * Coordinates are in points, in the global display space (origin at the
     * top-left of the main display, y going down), matching CGEvent locations.
     * Scale is reported as 1.0 because all coordinates handled by mousemaster
     * on macOS (mouse locations, screen bounds, Qt geometry) are already in
     * points.
     */
    @Override
    public Set<Screen> findScreens() {
        // CGGetActiveDisplayList occasionally reports no display at all, most
        // often within the first seconds of the process. ScreenManager throws on
        // an empty set, which killed the login item 179 times in a single day.
        Set<Screen> screens = activeScreens();
        // Wait for the display list only when there is nothing to fall back on,
        // so a later hiccup never stalls the main loop.
        for (int attempt = 0; screens.isEmpty() && lastKnownScreens.isEmpty() &&
                              attempt < 20; attempt++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            screens = activeScreens();
        }
        if (screens.isEmpty())
            return lastKnownScreens;
        lastKnownScreens = screens;
        return screens;
    }

    private Set<Screen> activeScreens() {
        MacCoreGraphics cg = MacCoreGraphics.INSTANCE;
        int[] displays = new int[16];
        IntByReference count = new IntByReference();
        cg.CGGetActiveDisplayList(displays.length, displays, count);
        Set<Screen> screens = new HashSet<>();
        for (int displayIndex = 0; displayIndex < count.getValue(); displayIndex++) {
            int display = displays[displayIndex];
            MacCoreGraphics.CGRect.ByValue bounds = cg.CGDisplayBounds(display);
            screens.add(new Screen(new Rectangle((int) bounds.origin.x,
                    (int) bounds.origin.y, (int) bounds.size.width,
                    (int) bounds.size.height), 96, 1.0d));
        }
        return screens;
    }

    /**
     * The display's backing scale (2.0 on Retina), independent of the
     * point-based coordinate system reported by findScreens().
     */
    public static double backingScale(int display) {
        MacCoreGraphics cg = MacCoreGraphics.INSTANCE;
        Pointer mode = cg.CGDisplayCopyDisplayMode(display);
        if (mode == null)
            return 1.0d;
        double scale = (double) cg.CGDisplayModeGetPixelWidth(mode) /
                       Math.max(1, cg.CGDisplayModeGetWidth(mode));
        cg.CGDisplayModeRelease(mode);
        return scale;
    }

}
