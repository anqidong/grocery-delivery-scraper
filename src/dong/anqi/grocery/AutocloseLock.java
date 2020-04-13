package dong.anqi.grocery;

import java.util.concurrent.locks.ReentrantLock;

public class AutocloseLock extends ReentrantLock {
  public AutocloseLock(boolean fair) {
    super(fair);
  }

  public AutoCloseable lockAndGetResource() {
    lock();
    return this::unlock;
  }
}
