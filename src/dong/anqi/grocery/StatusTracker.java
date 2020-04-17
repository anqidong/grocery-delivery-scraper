package dong.anqi.grocery;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class StatusTracker {
  public enum State { HAS_SLOT, NO_SLOT }

  /**
   * Value of none = uninitialized, no updates found.
   */
  private Optional<State> trackerState = Optional.empty();
  Instant stateChangeTime = Instant.now();

  boolean lastWasAvailable() {
    return trackerState.filter(s -> s == State.HAS_SLOT).isPresent();
  }

  /**
   * Updates this status tracker and returns the representing status after the update.
   * @return A status field, with slotFound, isEdgeTransition, and potentially timeSinceTransition
   *   set.
   */
  public GrocerySlotChecker.Status update(State newState) {
    GrocerySlotChecker.Status status = new GrocerySlotChecker.Status();

    trackerState.ifPresentOrElse(currState -> {
      status.isEdgeTransition = (currState != newState);
      if (status.isEdgeTransition) {
        status.timeSinceTransition = Optional.of(Duration.between(stateChangeTime, Instant.now()));
        stateChangeTime = Instant.now();
      }
    }, () -> {
      status.isEdgeTransition = false;
      stateChangeTime = Instant.now();
    });

    status.slotFound = (newState == State.HAS_SLOT);
    trackerState = Optional.of(newState);

    return status;
  }
}
