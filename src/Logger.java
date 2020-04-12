public interface Logger {
  default void log(String s) {
    System.out.println(s);
  }
  default void logErr(String s) {
    System.err.println(s);
  }
}
