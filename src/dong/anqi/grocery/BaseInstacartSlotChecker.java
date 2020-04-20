package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class BaseInstacartSlotChecker extends AbstractGrocerySlotChecker {
  public BaseInstacartSlotChecker(String description, Logger logger) {
    super(description, logger);
  }

  @Override
  public final boolean currentlyHasSlot() {
    return statusTracker.lastWasAvailable();
  }

  protected abstract void executeLogin();

  // For primary availability check (storefront)
  protected abstract String getHomePage();

  protected abstract Set<String> getAcceptedHomeUrls();

  protected abstract @NotNull By getDeliveryTimeButtonQuery();

  // For secondary availability check (delivery info)
  protected abstract String getDeliveryInfoPage();

  private static final Set<String> UNAVAILABLE_TEXT = ImmutableSet.of("Not available");
  private static final Set<String> INDETERMINATE_TEXT = ImmutableSet.of("See delivery times");

  private StatusTracker statusTracker = new StatusTracker();

  /**
   * Tries to load the requested page, and attempts to log in if the page load initially fails.
   * <p>
   * Gives up if the log in attempt also fails.
   *
   * @param requestedUrl The URL to try to load.
   * @param acceptedUrls URLs that will be accepted, should the server redirect the GET request to a
   *                     different URL.
   * @return Whether the login attempt ultimately succeeded.
   */
  private boolean tryToLoadPageWithAttemptedLogin(String requestedUrl, Set<String> acceptedUrls) {
    driver.get(requestedUrl);
    if (!acceptedUrls.contains(driver.getCurrentUrl())) {
      log(String.format("URL navigated to %s, retrying login?", driver.getCurrentUrl()));

      executeLogin();
      Utils.startInterruptibleSleep(Duration.ofSeconds(10));
      driver.get(requestedUrl);
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(5));

    return acceptedUrls.contains(driver.getCurrentUrl());
  }

  private static class StatusCheckOutput {
    enum Result {DEFINITE_GOOD, DEFINITE_FAIL, INDETERMINATE, SCRAPE_ERROR}

    final Result result;

    /**
     * This field is only populated if {@link #result} is
     * {@link StatusCheckOutput.Result#DEFINITE_GOOD}.
     */
    final @Nullable Status status;

    StatusCheckOutput(Result result) {
      if (result == Result.DEFINITE_GOOD) {
        throw new IllegalArgumentException("Result.DEFINITE_GOOD requires status");
      }

      this.result = result;
      this.status = null;
    }

    StatusCheckOutput(Status status) {
      if (status == null) {
        throw new IllegalArgumentException("Do not pass null status, use other ctor");
      }

      this.result = Result.DEFINITE_GOOD;
      this.status = status;
    }
  }

  private StatusCheckOutput checkAvailabilityOnHomePage() {
    List<WebElement> deliveryElements = driver.findElements(getDeliveryTimeButtonQuery());
    if (deliveryElements.isEmpty()) {
      logErr("No delivery info found on homepage");
      return new StatusCheckOutput(StatusCheckOutput.Result.SCRAPE_ERROR);
    }
    if (deliveryElements.size() != 1) {
      logErr("Non-unique delivery time button, found " + deliveryElements.size());
    }

    String availabilityText =
        deliveryElements.get(0).findElement(By.tagName("span")).getText();

    if (UNAVAILABLE_TEXT.contains(availabilityText)) {
      return new StatusCheckOutput(StatusCheckOutput.Result.DEFINITE_FAIL);
    } else if (INDETERMINATE_TEXT.contains(availabilityText)) {
      // Instacart sometimes shows "See delivery slots" when slots are actually available
      return new StatusCheckOutput(StatusCheckOutput.Result.INDETERMINATE);
    }

    // Otherwise, presume slot is available. Whitelist approach is risky LOL.
    Status status = statusTracker.update(StatusTracker.State.HAS_SLOT);

    String message = "Spots available for " +
        availabilityText.replace("Arrives ", "");
    status.notificationMessage = Optional.of(message);
    log(message);

    return new StatusCheckOutput(status);
  }

  private StatusCheckOutput checkAvailabilityOnDeliveryInfoPage() {
    List<WebElement> reactPanelElements = driver.findElements(
        By.cssSelector("div[aria-label*=\"retailer info modal\" i] div#react-tabs-1"));
    if (reactPanelElements.isEmpty()) {
      logErr("No delivery info panel found");
      return new StatusCheckOutput(StatusCheckOutput.Result.SCRAPE_ERROR);
    }
    if (reactPanelElements.size() != 1) {
      // This contains an ID selector LOL, so this should never happen
      logErr("Non-unique delivery times panel, found " + reactPanelElements.size());
    }

    final WebElement panelElement = reactPanelElements.get(0);
    if (getInnerHtml(panelElement).contains("No delivery times available")) {
      return new StatusCheckOutput(StatusCheckOutput.Result.DEFINITE_FAIL);
    }

    List<WebElement> deliverySlotElements = panelElement.findElements(By.cssSelector(
        // "div > div > div > div > div:nth-child(2) > div > div > div"
        "div.module-wrapper:nth-child(2) > div > div > div"
    ));

    Optional<String> header = deliverySlotElements.stream()
        .map(el -> el.findElement(By.tagName("div")))  // gets first div child
        .filter(el -> el.findElements(By.tagName("div")).isEmpty())  // has no further children
        .filter(el ->
            Integer.parseInt(el.getCssValue("font-weight")) >= 500) // element is bold
        .map(el -> el.getText()).findFirst();
    Optional<String> detail = deliverySlotElements.stream()
        .map(el -> el.findElement(By.tagName("div")))  // gets first div child
        .filter(el -> el.getCssValue("display").equals("flex")) // element is flex
        .map(el -> el.findElement(By.tagName("div")))  // gets first div sub-child
        .map(el -> el.getText()).findFirst();

    if (header.isEmpty() && detail.isEmpty()) {
      return new StatusCheckOutput(StatusCheckOutput.Result.SCRAPE_ERROR);
    } else {
      Status status = statusTracker.update(StatusTracker.State.HAS_SLOT);

      String message = "Spots available for " +
          header.map(s -> s + " ").orElse("") + detail.orElse("");
      status.notificationMessage = Optional.of(message);
      log(message);

      return new StatusCheckOutput(status);
    }
  }

  @Override
  public final Optional<Status> doCheck() {
    if (!tryToLoadPageWithAttemptedLogin(getHomePage(), getAcceptedHomeUrls())) {
      logErr(String.format("Failed to log in (URL %s), giving up", driver.getCurrentUrl()));
      return Optional.empty();
    }

    {
      StatusCheckOutput homePageStatus = checkAvailabilityOnHomePage();
      if (homePageStatus.result == StatusCheckOutput.Result.DEFINITE_GOOD) {
        return Optional.of(homePageStatus.status);
      } else if (homePageStatus.result == StatusCheckOutput.Result.DEFINITE_FAIL) {
        log("no slots");
        return Optional.of(statusTracker.update(StatusTracker.State.NO_SLOT));
      }
    }

    if (!tryToLoadPageWithAttemptedLogin(
        getDeliveryInfoPage(), ImmutableSet.of(getDeliveryInfoPage()))) {
      logErr(String.format("Failed to load delivery info page (URL %s), giving up",
          driver.getCurrentUrl()));
      return Optional.empty();
    }

    {
      StatusCheckOutput deliveryInfoPageStatus = checkAvailabilityOnDeliveryInfoPage();
      if (deliveryInfoPageStatus.result == StatusCheckOutput.Result.DEFINITE_GOOD) {
        return Optional.of(deliveryInfoPageStatus.status);
      } else if (deliveryInfoPageStatus.result == StatusCheckOutput.Result.DEFINITE_FAIL) {
        log("no slots");
        return Optional.of(statusTracker.update(StatusTracker.State.NO_SLOT));
      }
    }

    // Final result was indeterminate; don't update `statusTracker`.
    return Optional.empty();
  }
}
