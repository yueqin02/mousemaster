package mousemaster.platform.mac;

import mousemaster.Key;
import mousemaster.KeyboardLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps macOS (Carbon) virtual key codes to the Windows set-1 scan codes used by
 * keyboard-layouts.json. Both code sets identify physical key positions, so the
 * mapping is layout-independent: the active KeyboardLayout then resolves the
 * scan code to a Key.
 */
public class MacVirtualKey {

    private static final Map<Integer, Integer> scanCodeByMacKeyCode = new HashMap<>();
    private static final Map<Integer, Integer> macKeyCodeByScanCode = new HashMap<>();

    private static void map(int macKeyCode, int scanCode) {
        scanCodeByMacKeyCode.put(macKeyCode, scanCode);
        macKeyCodeByScanCode.putIfAbsent(scanCode, macKeyCode);
    }

    static {
        // Letter row block (kVK_ANSI_*).
        map(0, 0x1E);   // a
        map(1, 0x1F);   // s
        map(2, 0x20);   // d
        map(3, 0x21);   // f
        map(4, 0x23);   // h
        map(5, 0x22);   // g
        map(6, 0x2C);   // z
        map(7, 0x2D);   // x
        map(8, 0x2E);   // c
        map(9, 0x2F);   // v
        map(10, 0x56);  // ISO section key (102nd key)
        map(11, 0x30);  // b
        map(12, 0x10);  // q
        map(13, 0x11);  // w
        map(14, 0x12);  // e
        map(15, 0x13);  // r
        map(16, 0x15);  // y
        map(17, 0x14);  // t
        map(18, 0x02);  // 1
        map(19, 0x03);  // 2
        map(20, 0x04);  // 3
        map(21, 0x05);  // 4
        map(22, 0x07);  // 6
        map(23, 0x06);  // 5
        map(24, 0x0D);  // =
        map(25, 0x0A);  // 9
        map(26, 0x08);  // 7
        map(27, 0x0C);  // -
        map(28, 0x09);  // 8
        map(29, 0x0B);  // 0
        map(30, 0x1B);  // ]
        map(31, 0x18);  // o
        map(32, 0x16);  // u
        map(33, 0x1A);  // [
        map(34, 0x17);  // i
        map(35, 0x19);  // p
        map(36, 0x1C);  // return
        map(37, 0x26);  // l
        map(38, 0x24);  // j
        map(39, 0x28);  // '
        map(40, 0x25);  // k
        map(41, 0x27);  // ;
        map(42, 0x2B);  // backslash
        map(43, 0x33);  // ,
        map(44, 0x35);  // slash
        map(45, 0x31);  // n
        map(46, 0x32);  // m
        map(47, 0x34);  // .
        map(48, 0x0F);  // tab
        map(49, 0x39);  // space
        map(50, 0x29);  // ` (grave)
        map(51, 0x0E);  // delete = backspace
        map(53, 0x01);  // escape
        // Modifiers.
        map(54, 0xE05C); // right command -> rightwin
        map(55, 0xE05B); // command -> leftwin
        map(56, 0x2A);   // shift -> leftshift
        map(57, 0x3A);   // caps lock
        map(58, 0x38);   // option -> leftalt
        map(59, 0x1D);   // control -> leftctrl
        map(60, 0x36);   // right shift
        map(61, 0xE038); // right option -> rightalt
        map(62, 0xE01D); // right control
        // Keypad.
        map(65, 0x53);   // keypad decimal
        map(67, 0x37);   // keypad multiply
        map(69, 0x4E);   // keypad plus
        map(71, 0x45);   // keypad clear -> numlock
        map(75, 0xE035); // keypad divide
        map(76, 0xE01C); // keypad enter
        map(78, 0x4A);   // keypad minus
        map(82, 0x52);   // keypad 0
        map(83, 0x4F);   // keypad 1
        map(84, 0x50);   // keypad 2
        map(85, 0x51);   // keypad 3
        map(86, 0x4B);   // keypad 4
        map(87, 0x4C);   // keypad 5
        map(88, 0x4D);   // keypad 6
        map(89, 0x47);   // keypad 7
        map(91, 0x48);   // keypad 8
        map(92, 0x49);   // keypad 9
        // Function row.
        map(122, 0x3B); // f1
        map(120, 0x3C); // f2
        map(99, 0x3D);  // f3
        map(118, 0x3E); // f4
        map(96, 0x3F);  // f5
        map(97, 0x40);  // f6
        map(98, 0x41);  // f7
        map(100, 0x42); // f8
        map(101, 0x43); // f9
        map(109, 0x44); // f10
        map(103, 0x57); // f11
        map(111, 0x58); // f12
        map(105, 0x64); // f13
        map(107, 0x65); // f14
        map(113, 0x66); // f15
        map(106, 0x67); // f16
        map(64, 0x68);  // f17
        map(79, 0x69);  // f18
        map(80, 0x6A);  // f19
        map(90, 0x6B);  // f20
        // Navigation block.
        map(114, 0xE052); // help -> insert
        map(115, 0xE047); // home
        map(116, 0xE049); // page up
        map(117, 0xE053); // forward delete -> del
        map(119, 0xE04F); // end
        map(121, 0xE051); // page down
        map(123, 0xE04B); // left arrow
        map(124, 0xE04D); // right arrow
        map(125, 0xE050); // down arrow
        map(126, 0xE048); // up arrow
    }

    /**
     * @return null if the mac key code has no known physical equivalent
     * (e.g. the fn key) or the layout has no key at that position.
     */
    public static Key keyFromMacKeyCode(int macKeyCode, KeyboardLayout layout) {
        Integer scanCode = scanCodeByMacKeyCode.get(macKeyCode);
        if (scanCode == null)
            return null;
        return layout.keyFromScanCode(scanCode);
    }

    /**
     * @return -1 if the key cannot be injected on macOS.
     */
    public static int macKeyCodeFromKey(Key key, KeyboardLayout layout) {
        int scanCode = layout.scanCode(key);
        if (scanCode == -1)
            return -1;
        Integer macKeyCode = macKeyCodeByScanCode.get(scanCode);
        return macKeyCode == null ? -1 : macKeyCode;
    }

}
