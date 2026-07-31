package mousemaster.platform.mac;

import mousemaster.Clock;

import java.time.Instant;

public class MacClock implements Clock {

    private Instant lastKeyboardHookEventTime;
    private boolean inKeyboardHookEvent;

    @Override
    public Instant now() {
        if (inKeyboardHookEvent)
            return lastKeyboardHookEventTime;
        return Instant.now();
    }

    public void setLastKeyboardHookEventTime(Instant time) {
        lastKeyboardHookEventTime = time;
        inKeyboardHookEvent = true;
    }

    public void keyboardHookEventHandled() {
        inKeyboardHookEvent = false;
    }

}
