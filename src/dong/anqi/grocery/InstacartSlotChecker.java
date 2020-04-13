package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public final class InstacartSlotChecker extends BaseInstacartSlotChecker {
  public enum Store {
    SPROUTS("sprouts"),
    H_MART("hmart"),
    SAFEWAY("safeway"),
    MOLLIE_STONES("mollie-stones", "Mollie Stone's"),
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

    public String homePage() {
      return String.format("https://www.instacart.com/store/%s/storefront", instacartUrlPath);
    }

    public By deliveryButtonQuery() {
      return By.cssSelector(String.format("a[href~=\"/%s/info?tab=delivery\"]", instacartUrlPath));
    }
  }

  private final Store store;

  public InstacartSlotChecker(Store store, Logger logger) {
    super("Instacart " + store.displayName(), logger);
    this.store = store;
  }

  private static final String CREDS_PATH = "creds/instacart-google-oauth.creds";
  private static final String LOGIN_PAGE = "https://www.costco.com/logon-instacart";

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
  protected void executeLogin() {
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
}