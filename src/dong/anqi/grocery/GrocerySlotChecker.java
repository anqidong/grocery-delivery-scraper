package dong.anqi.grocery;

import java.util.Optional;

public interface GrocerySlotChecker extends AutoCloseable {
  String getDescription();

  /** Whether a slot was seen the last time {@link #doCheck()} was called. */
  boolean currentlyHasSlot();

  public static class Status {  // struct-like
    public boolean slotFound = false;
    public boolean isEdgeTransition = false;

    public Optional<String> notificationMessage;
  }

  Status doCheck();
}
