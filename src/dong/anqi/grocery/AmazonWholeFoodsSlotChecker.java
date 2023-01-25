package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AmazonWholeFoodsSlotChecker extends AbstractGrocerySlotChecker {
  public AmazonWholeFoodsSlotChecker(Store store, Logger logger) {
    super("Whole Foods", logger);
  }

  @Override
  public boolean currentlyHasSlot() {
    return statusTracker.lastWasAvailable();
  }

  private static final String CREDS_PATH = "creds/amazon.creds";

  private static final String LOGIN_PAGE = "https://shop.shipt.com/login";  // FIXME
  private static final String HOME_PAGE =
      "https://www.amazon.com/alm/storefront/ref=grocery_wholefoods?almBrandId=VUZHIFdob2xlIEZvb2Rz";

  private static final Set<String> UNAVAILABLE_TEXT =
      ImmutableSet.of("sold out");

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

  private StatusTracker statusTracker = new StatusTracker();

  // //*[@id="a-page"]/div[2]/div/div[1]
  // .alm-storefront-reserved-desktop

  /*
  <span class="a-size-medium naw-widget-banner-action-no-availability a-text-bold">
                    temporarily sold out
                </span>
   */

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
