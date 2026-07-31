package mousemaster.platform.mac;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Accessibility (AX) trust check: event taps that listen to keyboard input only
 * work once the process is granted Accessibility permission.
 */
public interface MacAccessibility extends Library {

    MacAccessibility INSTANCE = Native.load("ApplicationServices", MacAccessibility.class);

    NativeLibrary NATIVE_LIBRARY = NativeLibrary.getInstance("ApplicationServices");

    boolean AXIsProcessTrusted();

    boolean AXIsProcessTrustedWithOptions(Pointer options);

    static boolean isTrusted(boolean prompt) {
        if (INSTANCE.AXIsProcessTrusted())
            return true;
        if (!prompt)
            return false;
        Pointer promptKey =
                NATIVE_LIBRARY.getGlobalVariableAddress("kAXTrustedCheckOptionPrompt")
                              .getPointer(0);
        Pointer options = MacCoreFoundation.INSTANCE.CFDictionaryCreate(null,
                new Pointer[]{promptKey},
                new Pointer[]{MacCoreFoundation.kCFBooleanTrue}, 1,
                MacCoreFoundation.kCFTypeDictionaryKeyCallBacks,
                MacCoreFoundation.kCFTypeDictionaryValueCallBacks);
        boolean trusted = INSTANCE.AXIsProcessTrustedWithOptions(options);
        MacCoreFoundation.INSTANCE.CFRelease(options);
        return trusted;
    }

}
