package dong.anqi.grocery;

import com.google.common.collect.ImmutableList;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

  public static void main(String[] args) {
    System.setProperty("webdriver.chrome.driver", "/home/anqid/bin/chromedriver");

    StatusDialog dialog = new StatusDialog();
    TwitterClient twitterClient = new TwitterClient();
    Logger logger = new Logger() {
      @Override
      public void log(String s) {
        dialog.logText(s);
      }
    };

    List<GrocerySlotChecker> checkers = ImmutableList.of(
        new ShiptSlotChecker(ShiptSlotChecker.Store.RANCH_99, logger),
        new ShiptSlotChecker(ShiptSlotChecker.Store.TARGET, logger),
        new CostcoSamedaySlotChecker(logger)
    );

    ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(3);
    for (GrocerySlotChecker checker : checkers) {
      threadPoolExecutor.scheduleAtFixedRate(() -> {
        try {
          GrocerySlotChecker.Status status = checker.doCheck();
          if (status.isEdgeTransition) {
            String message = status.notificationMessage.orElse(
                "slot status: " + (status.slotFound ? "available" : getRandomNoString()));

            generateNotification(checker.getDescription(),
                message + (status.slotFound ? " go go go" : ""));
            if (status.slotFound) {
              twitterClient.sendDirectMessage(checker.getDescription() + ": " + message);
            }
          }
        } catch (Exception e) {
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
