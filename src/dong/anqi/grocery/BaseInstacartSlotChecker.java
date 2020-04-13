package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class BaseInstacartSlotChecker extends AbstractGrocerySlotChecker {
  private boolean lastWasAvailable = false;

  public BaseInstacartSlotChecker(String description, Logger logger) {
    super(description, logger);
  }

  @Override
  public final boolean currentlyHasSlot() {
    return lastWasAvailable;
  }

  protected abstract String getHomePage();
  protected abstract Set<String> getAcceptedHomeUrls();
  protected abstract @NotNull By getDeliveryTimeButtonQuery();
  protected abstract void executeLogin();

  private static final Set<String> UNAVAILABLE_TEXT =
      ImmutableSet.of("Not available", "See delivery times");

  @Override
  public final Status doCheck() {
    driver.get(getHomePage());
    if (!getAcceptedHomeUrls().contains(driver.getCurrentUrl())) {
      log(String.format("URL navigated to %s, retrying login?", driver.getCurrentUrl()));

      executeLogin();
      Utils.startInterruptibleSleep(Duration.ofSeconds(10));
      driver.get(getHomePage());
    }

    if (!getAcceptedHomeUrls().contains(driver.getCurrentUrl())) {
      logErr(String.format("Failed to log in (URL %s), giving up", driver.getCurrentUrl()));
      return new Status();
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(5));

    List<WebElement> deliveryElements = driver.findElements(getDeliveryTimeButtonQuery());
    if (deliveryElements.size() != 1) {
      logErr("Non-unique delivery time button, found " + deliveryElements.size());
    }
    if (deliveryElements.isEmpty()) {
      logErr("No delivery info found");
      return new Status();
    }

    String availabilityText =
        deliveryElements.get(0).findElement(By.tagName("span")).getText();
    boolean slotAvailable = !UNAVAILABLE_TEXT.contains(availabilityText);

    Status status = new Status();
    status.slotFound = slotAvailable;
    status.isEdgeTransition = (lastWasAvailable != slotAvailable);

    if (slotAvailable) {
      String message = "Spots available for " + availabilityText.replace("Arrives ", "");
      status.notificationMessage = Optional.of(message);
      log(message);
    } else {
      status.notificationMessage = Optional.empty();
      log("no slots");
    }

    return status;
  }
}