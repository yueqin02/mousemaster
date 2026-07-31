package mousemaster.platform.mac;

import mousemaster.platform.UiAutomation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Not implemented on macOS yet: hints cannot target UI elements
 * (hint.grid-area=interactive-elements). Position-based hints still work.
 */
public class MacUiAutomation implements UiAutomation {

    @Override
    public Future<List<UiElement>> startFindInteractiveUiElements() {
        return CompletableFuture.completedFuture(List.of());
    }

}
