package dong.anqi.grocery;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

public abstract class AbstractGrocerySlotChecker implements AutoCloseable, GrocerySlotChecker {
  private final String description;
  protected final RemoteWebDriver driver;
  protected final Logger logger;

  private RemoteWebDriver createDriver() {
    // sendKeys does not work with headless mode :(
    ChromeOptions chromeOptions = new ChromeOptions(); // .addArguments("--headless");
    RemoteWebDriver driver = new ChromeDriver(chromeOptions);
    return driver;
  }

  public AbstractGrocerySlotChecker(String description, Logger logger) {
    if (logger == null) { throw new NullPointerException(); }

    this.description = description;
    this.driver = createDriver();
    this.logger = logger;
  }

  @Override
  public String getDescription() { return description; }

  protected void log(String s) {
    logger.log(String.format("%s %s: %s", Utils.nowString(), getDescription(), s));
  }
  protected void logErr(String s) {
    logger.logErr(String.format("%s %s: %s", Utils.nowString(), getDescription(), s));
  }

  @Override
  public void close() {
    driver.quit();
  }
}
