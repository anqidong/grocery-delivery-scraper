package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class InstacartSlotChecker extends BaseInstacartSlotChecker {
  public enum Store {
    SPROUTS("sprouts"),
    H_MART("hmart"),
    SAFEWAY("safeway"),
    MOLLIE_STONES("mollie-stones", "Mollie Stone's"),
    COSTCO("costco"),
    TOTAL_WINE("total-wine-more", "Total Wine & More"),
    // Below: check URL before using
    SMART_AND_FINAL("smart-final", "Smart & Final"),
    RALEYS("raleys", "Raley's"),
    LUCKY("lucky-supermarkets");

    private final String instacartUrlPath;
    private final Optional<String> displayName;

    Store(String instacartUrlPath) {
      this.instacartUrlPath = instacartUrlPath;
      displayName = Optional.empty();
    }

    Store(String instacartUrlPath, String displayName) {
      this.instacartUrlPath = instacartUrlPath;
      this.displayName = Optional.of(displayName);
    }

    public String displayName() {
      return displayName.orElseGet(() -> name().replace('_', ' '));
    }

    String homePage() {
      return String.format("https://www.instacart.com/store/%s/storefront", instacartUrlPath);
    }

    By deliveryButtonQuery() {
      return By.cssSelector(String.format("a[href~=\"/%s/info?tab=delivery\"]", instacartUrlPath));
    }

    String deliveryInfoPage() {
      return String.format(
          "https://www.instacart.com/store/%s/info?tab=delivery", instacartUrlPath);
    }
  }

  private final Store store;

  public InstacartSlotChecker(Store store, Logger logger) {
    super("Instacart " + store.displayName(), logger);
    this.store = store;
  }

  @Override
  protected String getHomePage() {
    return store.homePage();
  }

  @Override
  protected @NotNull Set<String> getAcceptedHomeUrls() {
    return ImmutableSet.of(store.homePage());
  }

  @Override
  protected @NotNull By getDeliveryTimeButtonQuery() {
    return store.deliveryButtonQuery();
  }

  @Override
  protected String getDeliveryInfoPage() {
    return store.deliveryInfoPage();
  }

  private static final String CREDS_PATH = "creds/instacart.creds";
  private static final String LOGIN_START_PAGE = "https://www.instacart.com/";

  @Override
  protected void executeLogin() {
    driver.get(LOGIN_START_PAGE);
    if (!driver.getCurrentUrl().equals(LOGIN_START_PAGE)) {
      log(String.format("URL navigated to %s, already logged in?", driver.getCurrentUrl()));
    } else {
      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      List<WebElement> loginButtons = driver.findElements(By.tagName("button")).stream()
          .filter(el -> getInnerHtml(el).equals("Log in"))
          .collect(Collectors.toUnmodifiableList());
      if (loginButtons.isEmpty()) {
        logErr("No log in button found; giving up");
        return;
      } else if (loginButtons.size() != 1) {
        logErr("Multiple log in buttons found, randomly choosing one");
      }
      loginButtons.get(0).click();

      Utils.startInterruptibleSleep(Duration.ofMillis(500));
      Utils.Credentials creds = Utils.readCredentials(CREDS_PATH);

      WebElement passField = driver.findElement(By.id("nextgen-authenticate.all.log_in_password"));
      driver.findElement(By.id("nextgen-authenticate.all.log_in_email")).sendKeys(creds.user);
      passField.sendKeys(creds.pass);
      passField.sendKeys("\n");

      Utils.startInterruptibleSleep(Duration.ofSeconds(5));

      log(String.format("URL %s after login attempt", driver.getCurrentUrl()));
    }
  }
}