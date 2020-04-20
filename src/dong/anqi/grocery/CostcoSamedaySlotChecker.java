package dong.anqi.grocery;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;

import java.time.Duration;
import java.util.Set;

public final class CostcoSamedaySlotChecker extends BaseInstacartSlotChecker {
  public CostcoSamedaySlotChecker(Logger logger) {
    super("Costco", logger);

    driver.get(HOME_PAGE);
    driver.manage().addCookie(
        new Cookie.Builder("memberPrimaryPostal", "95134").domain("costco.com").build());
    driver.manage().addCookie(
        new Cookie.Builder("direct_retailer_zip_code", "95134").domain("sameday.costco.com")
            .build());
  }

  private static final String CREDS_PATH = "creds/costco.creds";

  private static final String LOGIN_PAGE = "https://www.costco.com/logon-instacart";
  private static final String HOME_PAGE = "https://sameday.costco.com/store/costco/storefront";
  private static final String DELIVERY_INFO_PAGE =
      "https://sameday.costco.com/store/costco/info?tab=delivery";

  private static final Set<String> ACCEPTED_HOME_URLS = ImmutableSet
      .of(HOME_PAGE, "https://sameday.costco.com/store/");

  @Override
  protected String getHomePage() {
    return HOME_PAGE;
  }

  @Override
  protected Set<String> getAcceptedHomeUrls() {
    return ACCEPTED_HOME_URLS;
  }

  @Contract(pure = true)
  @Override
  protected @NotNull By getDeliveryTimeButtonQuery() {
    return By.cssSelector("a[href~=\"/costco/info?tab=delivery\"]");
  }

  @Override
  protected String getDeliveryInfoPage() {
    return DELIVERY_INFO_PAGE;
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
