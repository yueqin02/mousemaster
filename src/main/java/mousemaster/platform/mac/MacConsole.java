package mousemaster.platform.mac;

import mousemaster.platform.Console;

/**
 * No-op: on macOS mousemaster runs from a terminal the user controls.
 */
public class MacConsole implements Console {

    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

}
