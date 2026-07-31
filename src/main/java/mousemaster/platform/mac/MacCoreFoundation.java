package mousemaster.platform.mac;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Minimal CoreFoundation bindings needed to drive the event tap run loop.
 */
public interface MacCoreFoundation extends Library {

    MacCoreFoundation INSTANCE = Native.load("CoreFoundation", MacCoreFoundation.class);

    NativeLibrary NATIVE_LIBRARY = NativeLibrary.getInstance("CoreFoundation");

    Pointer kCFRunLoopDefaultMode =
            NATIVE_LIBRARY.getGlobalVariableAddress("kCFRunLoopDefaultMode").getPointer(0);
    Pointer kCFBooleanTrue =
            NATIVE_LIBRARY.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0);
    Pointer kCFTypeDictionaryKeyCallBacks =
            NATIVE_LIBRARY.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks");
    Pointer kCFTypeDictionaryValueCallBacks =
            NATIVE_LIBRARY.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks");

    int kCFRunLoopRunHandledSource = 4;

    Pointer CFMachPortCreateRunLoopSource(Pointer allocator, Pointer machPort, long order);

    Pointer CFRunLoopGetCurrent();

    void CFRunLoopAddSource(Pointer runLoop, Pointer source, Pointer mode);

    void CFRunLoopRemoveSource(Pointer runLoop, Pointer source, Pointer mode);

    int CFRunLoopRunInMode(Pointer mode, double seconds, boolean returnAfterSourceHandled);

    void CFRelease(Pointer cfTypeRef);

    Pointer CFDictionaryCreate(Pointer allocator, Pointer[] keys, Pointer[] values,
                               long numValues, Pointer keyCallBacks,
                               Pointer valueCallBacks);

}
