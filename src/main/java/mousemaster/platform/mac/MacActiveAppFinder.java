package mousemaster.platform.mac;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import mousemaster.App;
import mousemaster.platform.ActiveAppFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the frontmost application name via NSWorkspace, using the Objective-C
 * runtime. App-based combo preconditions match against the app's localized
 * name (e.g. "Safari"), the macOS equivalent of "chrome.exe" on Windows.
 */
public class MacActiveAppFinder implements ActiveAppFinder {

    private static final Logger logger = LoggerFactory.getLogger(MacActiveAppFinder.class);

    private static final App UNKNOWN_APP = new App("");

    private interface ObjectiveC extends Library {
        Pointer objc_getClass(String name);

        Pointer sel_registerName(String name);

        Pointer objc_msgSend(Pointer receiver, Pointer selector);
    }

    private static final ObjectiveC objc;
    private static final Pointer nsWorkspaceClass;
    private static final Pointer sharedWorkspaceSelector;
    private static final Pointer frontmostApplicationSelector;
    private static final Pointer localizedNameSelector;
    private static final Pointer utf8StringSelector;

    static {
        // Force AppKit into the process so the NSWorkspace class exists.
        NativeLibrary.getInstance("AppKit");
        objc = Native.load("objc", ObjectiveC.class);
        nsWorkspaceClass = objc.objc_getClass("NSWorkspace");
        sharedWorkspaceSelector = objc.sel_registerName("sharedWorkspace");
        frontmostApplicationSelector = objc.sel_registerName("frontmostApplication");
        localizedNameSelector = objc.sel_registerName("localizedName");
        utf8StringSelector = objc.sel_registerName("UTF8String");
    }

    private boolean failedOnce;

    @Override
    public App activeApp() {
        try {
            Pointer workspace =
                    objc.objc_msgSend(nsWorkspaceClass, sharedWorkspaceSelector);
            if (workspace == null)
                return UNKNOWN_APP;
            Pointer application =
                    objc.objc_msgSend(workspace, frontmostApplicationSelector);
            if (application == null)
                return UNKNOWN_APP;
            Pointer name = objc.objc_msgSend(application, localizedNameSelector);
            if (name == null)
                return UNKNOWN_APP;
            Pointer utf8 = objc.objc_msgSend(name, utf8StringSelector);
            if (utf8 == null)
                return UNKNOWN_APP;
            return new App(utf8.getString(0, "UTF-8"));
        } catch (Throwable e) {
            if (!failedOnce) {
                failedOnce = true;
                logger.warn("Unable to find the active app", e);
            }
            return UNKNOWN_APP;
        }
    }

}
