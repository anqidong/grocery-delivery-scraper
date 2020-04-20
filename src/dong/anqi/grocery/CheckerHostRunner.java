package dong.anqi.grocery;

import com.google.common.collect.ImmutableList;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CheckerHostRunner {
  private static void rateLimitSleep() {
    // Try fairly hard to ensure that we sleep for long enough
    for (int i = 0; i < 5; i++) {
      if (Utils.startInterruptibleSleep(Duration.ofMinutes(2))) {
        break;
      } else {
        System.out.printf("%s Sleep failed, watch for DDOS", Utils.nowString());
      }
    }
  }

  private static void generateNotification(String title, String body) {
    try {
      new ProcessBuilder("/usr/bin/notify-send", "-t", "30000", title, body).start();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      new ProcessBuilder("/usr/bin/espeak", title + body).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void cliWait() throws IOException {
    System.out.println("Type q to quit");

    final BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      System.out.print("> ");
      String line = r.readLine();
      if (line.toUpperCase().equals("Q")) {
        break;
      } else {
        System.out.println();
      }
    }

    System.out.println("Exiting...");
  }

  private static final Random random = new Random();
  private static final List<String> NOPE_TEXT =
      ImmutableList.of("none", "nope", "niet", "womp womp", "nada", "no dice", "zzzt");

  private static final String getRandomNoString() {
    return NOPE_TEXT.get(random.nextInt(NOPE_TEXT.size()));
  }

  private static Optional<String> getDurationDescription(GrocerySlotChecker.Status status) {
    return status.timeSinceTransition
        .map(dur -> {
          String durationString =
              String.format("%dd%dh%dm", dur.toDaysPart(), dur.toHoursPart(), dur.toMinutesPart());
          return status.slotFound ? ("after " + durationString) : ("lasted " + durationString);
        });
  }

  private static final DateTimeFormatter FILE_NAME_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  public static void main(String[] args) {
    final String homeDir = System.getProperty("user.home");
    System.setProperty("webdriver.chrome.driver",
        Paths.get(homeDir, "bin", "chromedriver").toString());

    StatusDialog dialog = new StatusDialog();

    Path logDirectory = Paths.get(homeDir, "tmp", "grocery-logs");
    BufferedWriter writer;
    try {
      Files.createDirectories(logDirectory);
      writer = new BufferedWriter(new FileWriter(
          logDirectory.resolve("logs_" + FILE_NAME_FORMAT.format(LocalDateTime.now()) + ".txt").toFile()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Logger logger = new Logger() {
      @Override
      public void log(String s) {
        dialog.logText(s);
        try {
          writer.write(s);
          writer.newLine();
          writer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    };

    List<GrocerySlotChecker> checkers = ImmutableList.of(
        new ShiptSlotChecker(ShiptSlotChecker.Store.RANCH_99, logger),
        new ShiptSlotChecker(ShiptSlotChecker.Store.TARGET, logger),
        new InstacartSlotChecker(InstacartSlotChecker.Store.SPROUTS, logger),
        new InstacartSlotChecker(InstacartSlotChecker.Store.H_MART, logger),
        new CostcoSamedaySlotChecker(logger),
        new WeeeDateAvailableChecker(false, logger)
    );

    TwitterClient twitterClient = new TwitterClient();

    // TODO this needs to be in an effing class mate
    Map<GrocerySlotChecker, Instant> rateLimitTracker = new HashMap<>();
    Consumer<GrocerySlotChecker> genericFailure = checker -> {
      Duration timeSinceLast = Duration.between(
          rateLimitTracker.getOrDefault(checker, Instant.EPOCH), Instant.now());
      if (timeSinceLast.toMinutes() > 120) {
        twitterClient.sendDirectMessage(checker.getDescription() + " failed to scrape");
        rateLimitTracker.put(checker, Instant.now());
      } else {
        System.out.printf("%s Scrape failure for %s, rate-limiting Twitter",
            Utils.nowString(), checker.getDescription());
      }
    };

    ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(4);
    for (GrocerySlotChecker checker : checkers) {
      threadPoolExecutor.scheduleAtFixedRate(() -> {
        try {
          checker.doCheck().ifPresentOrElse(status -> {
            if (status.isEdgeTransition) {
              String message = status.notificationMessage.orElse(
                  "slot status: " + (status.slotFound ? "available" : getRandomNoString())) +
                  getDurationDescription(status).map(s -> ", " + s).orElse("");

              generateNotification(checker.getDescription(),
                  message + (status.slotFound ? " go go go" : ""));
              if (status.slotFound) {
                twitterClient.sendDirectMessage(checker.getDescription() + ": " + message);
              }
            }
          }, () -> genericFailure.accept(checker));
        } catch (Exception e) {
          twitterClient.sendDirectMessage(checker.getDescription() + " crashed");
          e.printStackTrace();
        }
      }, 10, 200, TimeUnit.SECONDS);
    }

    dialog.setCallbacks(new StatusDialog.Callbacks() {
      @Override
      public void commandEntered(String command) {
        if (command.toUpperCase().equals("QQ")) {
          windowClosed();
        }
      }

      @Override
      public void windowClosed() {
        for (GrocerySlotChecker checker : checkers) {
          try {
            checker.close();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

        System.exit(0);
      }
    });

    SwingUtilities.invokeLater(() -> {
      dialog.pack();
      dialog.setVisible(true);
    });
  }
}
