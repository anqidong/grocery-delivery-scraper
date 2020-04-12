import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CostcoSamedaySeleniumQuerier extends AbstractGrocerySlotChecker {
  private boolean lastWasAvailable = false;

  public CostcoSamedaySeleniumQuerier(Logger logger) {
    super("Costco", logger);

    driver.get(HOME_PAGE);
    driver.manage().addCookie(
        new Cookie.Builder("memberPrimaryPostal", "95134").domain("costco.com").build());
    driver.manage().addCookie(
        new Cookie.Builder("direct_retailer_zip_code", "95134").domain("sameday.costco.com")
            .build());
  }

  @Override
  public boolean currentlyHasSlot() {
    return lastWasAvailable;
  }

  private static final String CREDS_PATH = "creds/costco.creds";

  private static final String LOGIN_PAGE = "https://www.costco.com/logon-instacart";
  private static final String HOME_PAGE = "https://sameday.costco.com/store/costco/storefront";

  private static final Set<String> ACCEPTED_HOME_URLS = ImmutableSet
      .of(HOME_PAGE, "https://sameday.costco.com/store/");
  private static final Set<String> UNAVAILABLE_TEXT =
      ImmutableSet.of("Not available", "See delivery times");

  private void executeLogin() {
    driver.get(LOGIN_PAGE);
    if (!driver.getCurrentUrl().equals(LOGIN_PAGE)) {
      log(String.format("URL navigated to %s, already logged in?", driver.getCurrentUrl()));
    } else {
      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      // TODO: Set values using JavascriptExecutor so we can run headless
      Utils.Credentials creds = Utils.readCredentials(CREDS_PATH);
      driver.findElement(By.id("logonId")).sendKeys(creds.user);
      driver.findElement(By.id("logonPassword")).sendKeys(creds.pass);
      driver.findElement(By.id("logonPassword")).submit();

      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      log(String.format("URL %s after login attempt", driver.getCurrentUrl()));
    }
  }

  @Override
  public Status doCheck() {
    driver.get(HOME_PAGE);
    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      log(String.format("URL navigated to %s, retrying login?", driver.getCurrentUrl()));

      executeLogin();
      Utils.startInterruptibleSleep(Duration.ofSeconds(10));
      driver.get(HOME_PAGE);
    }

    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl())) {
      logErr(String.format("Failed to log in (URL %s), giving up", driver.getCurrentUrl()));
      return new Status();
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(10));

    List<WebElement> deliveryElements =
        driver.findElements(By.cssSelector("a[href~=\"/costco/info?tab=delivery\"]"));
    if (deliveryElements.size() != 1) {
      logErr("Non-unique delivery time <a>, found " + deliveryElements.size());
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