package dong.anqi.grocery;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class WeeeDateAvailableChecker extends AbstractGrocerySlotChecker {
  private final boolean showBundleBuy;

  public WeeeDateAvailableChecker(boolean showBundleBuy, Logger logger) {
    super("Weee", logger);
    this.showBundleBuy = showBundleBuy;
  }

  @Override
  public boolean currentlyHasSlot() {
    return statusTracker.lastWasAvailable();
  }

  private static final String CONFIG_PAGE = "https://www.sayweee.com/zh";
  private static final String HOME_PAGE = "https://www.sayweee.com/";

  private static final Set<String> ACCEPTED_HOME_URLS = ImmutableSet.of(
      HOME_PAGE, "https://www.sayweee.com");

  private void executeConfig() {
    driver.get(CONFIG_PAGE);
    if (!driver.getCurrentUrl().equals(CONFIG_PAGE)) {
      log(String.format("URL navigated to %s, already configured?", driver.getCurrentUrl()));
    } else {
      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      if (driver.findElements(By.id("zip_code")).isEmpty() &&
          !driver.findElements(By.id("date_select_header")).isEmpty()) {
        log(String.format("URL is %s, but still configured?", driver.getCurrentUrl()));
      }

      // TODO: Set values using JavascriptExecutor so we can run headless
      driver.findElement(By.id("zip_code")).sendKeys("95134");
      driver.findElement(By.id("zip_code")).submit();

      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      log(String.format("URL %s after login attempt", driver.getCurrentUrl()));
    }
  }

  private Stream<WebElement> getDeliveryDateDivs() {
    return driver.findElement(By.id("date_list")).findElements(By.className("week")).stream()
        .flatMap(el -> el.findElements(By.className("date-cell")).stream());
  }

  private static List<String> getClasses(WebElement el) {
    return Arrays.asList(el.getAttribute("class").split("\\s+"));
  }

  private StatusTracker statusTracker = new StatusTracker();

  @Override
  public Optional<Status> doCheck() {
    driver.get(HOME_PAGE);
    if (!ACCEPTED_HOME_URLS.contains(driver.getCurrentUrl()) ||
        driver.manage().getCookies().isEmpty()) {
      log(String.format("URL navigated to %s, %d cookies, retrying login?",
          driver.getCurrentUrl(), driver.manage().getCookies().size()));

      executeConfig();

      // This is necessary for Weee, in order to clear an onboarding modal
      driver.get(HOME_PAGE);
    }

    // TODO this should be a page loaded waiter in case site is bogged down
    Utils.startInterruptibleSleep(Duration.ofSeconds(5));

    // Open the availability dates modal
    {
      List<WebElement> dateSelectElement = driver.findElements(By.id("date_select_header"));
      if (dateSelectElement.isEmpty()) {
        logErr("No date select button found");
        return Optional.empty();
      }
      if (dateSelectElement.size() != 1) {
        logErr("Non-unique date select button, found " + dateSelectElement.size() +
            ", randomly choosing one");
      }

      dateSelectElement.get(0).click();
    }

    Utils.startInterruptibleSleep(Duration.ofSeconds(3));

    // TODO may need to exclude .portal-pickup and only accept .portal-delivery
    Optional<WebElement> dateElement = getDeliveryDateDivs()
        .filter(el -> !(getClasses(el).contains("unavailable")))
        .findFirst();
    if (dateElement.isEmpty()) {
      dateElement = getDeliveryDateDivs()
          .filter(el -> !Strings.isNullOrEmpty(el.getAttribute("data-url")) &&
              (showBundleBuy || !getClasses(el).contains("has-bundle")))
          .findFirst();
    }

    Status status = statusTracker.update(dateElement.isPresent() ?
        StatusTracker.State.HAS_SLOT :
        StatusTracker.State.NO_SLOT);

    dateElement.ifPresentOrElse(el -> {
      String message = "Spots available for " + el.getAttribute("data-date");
      status.notificationMessage = Optional.of(message);
      log(message);
    }, () -> {
      status.notificationMessage = Optional.empty();
      log("no slots");
    });

    return Optional.of(status);
  }
}
