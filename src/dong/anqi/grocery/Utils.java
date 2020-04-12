package dong.anqi.grocery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

enum Utils {
  ;

  public static class Credentials {
    public String user;
    public String pass;
  }

  public static Credentials readCredentials(String file) {
    try {
      Path path = Paths.get(file);
      List<String> data = Files.readAllLines(path);

      if (data.size() < 2) {
        throw new IllegalStateException(String.format(path + " does not have enough lines"));
      }

      String user = data.get(0);
      String pass = data.get(1);

      Credentials creds = new Credentials();
      creds.user = user;
      creds.pass = pass;
      return creds;
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String nowString() {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now());
  }

  public static boolean startInterruptibleSleep(long millis) {
    try {
      Thread.sleep(millis);
      return true;
    } catch (InterruptedException e) {
      return false;
    }
  }

  public static boolean startInterruptibleSleep(Duration duration) {
    long millis = 0;
    try {
      millis = duration.toMillis();
    } catch (ArithmeticException e) {
      millis = Long.MAX_VALUE;
    }

    return startInterruptibleSleep(millis);
  }
}
