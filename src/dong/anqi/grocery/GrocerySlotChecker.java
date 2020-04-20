package dong.anqi.grocery;

import java.time.Duration;
import java.util.Optional;

public interface GrocerySlotChecker extends AutoCloseable {
  String getDescription();

  /** Whether a slot was seen the last time {@link #doCheck()} was called. */
  boolean currentlyHasSlot();

  public static class Status {  // struct-like
    public boolean slotFound = false;
    public boolean isEdgeTransition = false;
    public Optional<Duration> timeSinceTransition = Optional.empty();

    public Optional<String> notificationMessage = Optional.empty();
  }

  /**
   *
   * @return Present status if check accomplished something, empty optional if the check failed and
   *         was indeterminate.
   */
  Optional<Status> doCheck();

  default Duration getPreferredCheckFrequency() { return Duration.ofMinutes(4); }
}
