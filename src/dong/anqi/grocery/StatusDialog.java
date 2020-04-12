package dong.anqi.grocery;

import javax.swing.*;
import java.awt.event.*;
import java.util.Optional;

public class StatusDialog extends JFrame {
  private JPanel contentPane;
  private JTextArea logTextArea;
  private JTextField commandTextField;

  /** Thread safety warning! Access through {@link #getCallbacks()}. */
  private volatile Optional<Callbacks> callbacks;

  public static interface Callbacks {
    default void commandEntered(String command) {}
    default void windowClosed() {}
  }

  public synchronized void setCallbacks(Callbacks callbacks) {
    this.callbacks = Optional.of(callbacks);
  }

  public synchronized void clearCallbacks() {
    callbacks = Optional.empty();
  }

  private synchronized Optional<Callbacks> getCallbacks() {
    return callbacks;
  }

  public StatusDialog() {
    setContentPane(contentPane);
    setTitle("Grocery Slot Watcher");

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onExit();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(e -> onExit(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    commandTextField.registerKeyboardAction(e -> {
          final String command = commandTextField.getText();
          getCallbacks().ifPresent(c -> c.commandEntered(command));
          commandTextField.setText("");
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onExit() {
    getCallbacks().ifPresent(c -> c.windowClosed());
    dispose();  // autogenerated
  }

  public void logText(final String s) {
    System.out.println(s);  // FIXME debugging
    logTextArea.append(s + "\n");
  }

  // Testing only
  public static void main(String[] args) throws InterruptedException {
    StatusDialog dialog = new StatusDialog();
    dialog.setCallbacks(new StatusDialog.Callbacks() {
      @Override
      public void commandEntered(String command) {
        dialog.logText(command);
      }

      @Override
      public void windowClosed() {
        System.exit(0);
      }
    });

    SwingUtilities.invokeLater(() -> {
      dialog.pack();
      dialog.setVisible(true);
    });

    while (true) {
      System.out.println("hi");
      Thread.sleep(5000);
    }
  }
}