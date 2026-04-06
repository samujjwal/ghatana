package com.ghatana.yappc.platform.ai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose Debounces rapid-fire change notifications to avoid redundant background analysis runs.
 * @doc.layer product
 * @doc.pattern Debouncer
 */
public final class ChangeDebouncer {

  private final DebounceScheduler scheduler;
  private final Map<String, Cancellation> pending = new ConcurrentHashMap<>();

  public ChangeDebouncer(DebounceScheduler scheduler) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  public void debounce(String key, Duration delay, Runnable action) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(action, "action");

    Cancellation existing = pending.remove(key);
    if (existing != null) {
      existing.cancel();
    }

    Cancellation scheduled =
        scheduler.schedule(
            delay,
            () -> {
              pending.remove(key);
              action.run();
            });
    pending.put(key, scheduled);
  }

  public int pendingCount() {
    return pending.size();
  }

  @FunctionalInterface
  public interface DebounceScheduler {
    Cancellation schedule(Duration delay, Runnable action);
  }

  @FunctionalInterface
  public interface Cancellation {
    void cancel();
  }
}