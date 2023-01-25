package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ShiptSlotChecker extends AbstractGrocerySlotChecker {
  public enum Store {
    RANCH_99("99 Ranch"),
    TARGET("Target"),
    SAFEWAY("Safeway");

    private final String shiptAriaLabel;

    Store(String shiptAriaLabel) {
      this.shiptAriaLabel = shiptAriaLabel;
    }

    public String displayName() {
      return shiptAriaLabel;
    }

    String shiptAriaLabel() {
      return shiptAriaLabel;
    }
  }

  private final Store store;

  public ShiptSlotChecker(Store store, Logger logger) {
    super("Shipt " + store.displayName(), logger);
    this.store = store;
  }

  @Override
  public boolean currentlyHasSlot() {
    return statusTracker.lastWasAvailable();
  }

  private static final String CREDS_PATH = "creds/shipt.creds";

  private static final String LOGIN_PAGE = "https://shop.shipt.com/login";
  private static final String HOME_PAGE = "https://shop.shipt.com/";

  private static final Set<String> ACCEPTED_HOME_URLS =
      ImmutableSet.of("https://shop.shipt.com/", "https://shop.shipt.com");
  private static final Set<String> UNAVAILABLE_TEXT =
      ImmutableSet.of("Not available", "Check back soon");

  private void executeLogin() {
    driver.get(LOGIN_PAGE);
    if (!driver.getCurrentUrl().equals(LOGIN_PAGE)) {
      log(String.format("URL navigated to %s, already logged in?", driver.getCurrentUrl()));
    } else {
      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      // TODO: Set values using JavascriptExecutor so we can run headless
      Utils.Credentials creds = Utils.readCredentials(CREDS_PATH);
      driver.findElement(By.id("username")).sendKeys(creds.user);
      driver.findElement(By.id("password")).sendKeys(creds.pass);
      driver.findElement(By.id("password")).submit();

      Utils.startInterruptibleSleep(Duration.ofSeconds(8));

      log(String.format("URL %s after login attempt", driver.getCurrentUrl()));
    }
  }

  private class StoreSelectFailureException extends Exception {}

  private void ensureStoreSelection(boolean assumeOnHomePage) throws StoreSelectFailureException {
    if (!assumeOnHomePage) {
      driver.get(HOME_PAGE);
    }

    driver.findElement(By.cssSelector("button[data-test~=\"ShoppingStoreSelect-storeView\"]"))
        .click();

    Utils.startInterruptibleSleep(Duration.ofSeconds(7));  // This one seems really laggy

    WebElement selectForm =
        driver.findElement(By.cssSelector("form[data-test~=\"ChooseStore-form\"]"));
    List<WebElement> storeButtons =
        selectForm.findElements(By.cssSelector("div[data-test~=\"ChooseStore-store\"]"));
    Optional<WebElement> usedButtonOr = storeButtons.stream()
        .filter(we -> we.getAttribute("aria-label").equals(store.shiptAriaLabel()))
        .findFirst();

    usedButtonOr.ifPresentOrElse(
            we -> we.click(),
            () -> logErr(store.displayName() + " not selectable"));
    if (usedButtonOr.isEmpty()) {
      throw new StoreSelectFailureException();
    }

    Utils.startInterruptibleSleep(Duration.ofSeconds(5));

    // TODO read page text again, and ensure that store selection stuck
  }

  private StatusTracker statusTracker = new StatusTracker();

  // This is a global (static) lock, because we're only using one account for Shipt, and the
  // selected store seems to be a global persisted variable stored per account.
  private static final AutocloseLock storeSelectMutex = new AutocloseLock(true);

  @Override
  public Optional<Status> doCheck() {
    driver.get(HOME_PAGE);
    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      log(String.format("URL navigated to %s, retrying login?", driver.getCurrentUrl()));

      executeLogin();
      driver.get(HOME_PAGE);
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(5));

    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      logErr(String.format("Failed to log in (URL %s), giving up", driver.getCurrentUrl()));
      return Optional.empty();
    }

    String availabilityText = null;
    try (AutoCloseable a = storeSelectMutex.lockAndGetResource()) {
      ensureStoreSelection(true);

      List<WebElement> deliveryElements =
          driver.findElements(By.cssSelector("div[data-test~=\"NextDeliveryWindow-text\"]"));
      if (deliveryElements.isEmpty()) {
        logErr("No delivery info found");
        return Optional.empty();
      }
      if (deliveryElements.size() != 1) {
        logErr("Non-unique NextDeliveryWindow div, found " + deliveryElements.size());
      }

      availabilityText =
          deliveryElements.get(0).findElement(By.cssSelector("[class*=\"body\"]")).getText();
    } catch (StoreSelectFailureException e) {
      return Optional.empty();
    } catch (Exception e) {
      e.printStackTrace();
      logErr("Unable to take lock for Shipt store selection");
    }

    boolean slotAvailable =
        availabilityText != null && !UNAVAILABLE_TEXT.contains(availabilityText);

    Status status = statusTracker.update(slotAvailable ?
        StatusTracker.State.HAS_SLOT :
        StatusTracker.State.NO_SLOT);

    if (slotAvailable) {
      String message = "Spots available for " + availabilityText;
      status.notificationMessage = Optional.of(message);
      log(message);
    } else {
      status.notificationMessage = Optional.empty();
      log("no slots");
    }

    return Optional.of(status);
  }
}
