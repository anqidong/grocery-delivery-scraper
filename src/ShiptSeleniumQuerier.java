import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ShiptSeleniumQuerier extends AbstractGrocerySlotChecker {
  private boolean lastWasAvailable = false;

  public ShiptSeleniumQuerier(Logger logger) {
    super("Shipt", logger);
  }

  @Override
  public boolean currentlyHasSlot() {
    return lastWasAvailable;
  }

  private static final String CREDS_PATH = "creds/shipt.creds";

  private static final String SHIPT_LOGIN_PAGE = "https://shop.shipt.com/login";
  private static final String SHIPT_HOME_PAGE = "https://shop.shipt.com/";

  private static final Set<String> ACCEPTED_HOME_URLS =
      ImmutableSet.of("https://shop.shipt.com/", "https://shop.shipt.com");
  private static final Set<String> UNAVAILABLE_TEXT =
      ImmutableSet.of("Not available", "Check back soon");

  private void executeLogin() {
    driver.get(SHIPT_LOGIN_PAGE);
    if (!driver.getCurrentUrl().equals(SHIPT_LOGIN_PAGE)) {
      log(String.format("URL navigated to %s, already logged in?", driver.getCurrentUrl()));
    } else {
      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      // TODO: Set values using JavascriptExecutor so we can run headless
      Utils.Credentials creds = Utils.readCredentials(CREDS_PATH);
      driver.findElement(By.id("username")).sendKeys(creds.user);
      driver.findElement(By.id("password")).sendKeys(creds.pass);
      driver.findElement(By.id("password")).submit();

      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      log(String.format("URL %s after login attempt", driver.getCurrentUrl()));
    }
  }

  @Override
  public Status doCheck() {
    driver.get(SHIPT_HOME_PAGE);
    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      log(String.format("URL navigated to %s, retrying login?", driver.getCurrentUrl()));

      executeLogin();
      Utils.startInterruptibleSleep(Duration.ofSeconds(10));
      driver.get(SHIPT_HOME_PAGE);
    }

    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      logErr(String.format("Failed to log in (URL %s), giving up", driver.getCurrentUrl()));
      return new Status();
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(10));

    List<WebElement> deliveryElements =
        driver.findElements(By.cssSelector("div[data-test~=\"NextDeliveryWindow-text\"]"));
    if (deliveryElements.size() != 1) {
      logErr("Non-unique NextDeliveryWindow div, found " + deliveryElements.size());
    }
    if (deliveryElements.isEmpty()) {
      logErr("No delivery info found");
      return new Status();
    }

    String availabilityText =
        deliveryElements.get(0).findElement(By.cssSelector("[class*=\"body\"]")).getText();
    boolean slotAvailable = !UNAVAILABLE_TEXT.contains(availabilityText);

    Status status = new Status();
    status.slotFound = slotAvailable;
    status.isEdgeTransition = (lastWasAvailable != slotAvailable);

    if (slotAvailable) {
      String message = "Spots available for " + availabilityText;
      status.notificationMessage = Optional.of(message);
      log(message);
    } else {
      status.notificationMessage = Optional.empty();
      log("no slots");
    }

    lastWasAvailable = slotAvailable;

    return status;
  }
}