package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChangeDebouncer Tests")
class ChangeDebouncerTest {

  @Test
  @DisplayName("debounce replaces existing scheduled action for the same key")
  void debounceReplacesExistingScheduledActionForSameKey() {
    List<FakeCancellation> scheduled = new ArrayList<>();
    ChangeDebouncer debouncer =
        new ChangeDebouncer(
            (delay, action) -> {
              FakeCancellation cancellation = new FakeCancellation(delay, action);
              scheduled.add(cancellation);
              return cancellation;
            });

    debouncer.debounce("file-1", Duration.ofSeconds(2), () -> {});
    debouncer.debounce("file-1", Duration.ofSeconds(3), () -> {});

    assertThat(scheduled).hasSize(2);
    assertThat(scheduled.get(0).cancelled).isTrue();
    assertThat(scheduled.get(1).cancelled).isFalse();
    assertThat(debouncer.pendingCount()).isEqualTo(1);
    assertThat(scheduled.get(1).delay).isEqualTo(Duration.ofSeconds(3));
  }

  @Test
  @DisplayName("debounce executes action and removes pending key")
  void debounceExecutesActionAndRemovesPendingKey() {
    AtomicInteger executions = new AtomicInteger();
    List<FakeCancellation> scheduled = new ArrayList<>();
    ChangeDebouncer debouncer =
        new ChangeDebouncer(
            (delay, action) -> {
              FakeCancellation cancellation = new FakeCancellation(delay, action);
              scheduled.add(cancellation);
              return cancellation;
            });

    debouncer.debounce("file-2", Duration.ofMillis(250), executions::incrementAndGet);
    scheduled.get(0).run();

    assertThat(executions.get()).isEqualTo(1);
    assertThat(debouncer.pendingCount()).isZero();
  }

  private static final class FakeCancellation implements ChangeDebouncer.Cancellation {
    private final Duration delay;
    private final Runnable action;
    private boolean cancelled;

    private FakeCancellation(Duration delay, Runnable action) {
      this.delay = delay;
      this.action = action;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    private void run() {
      action.run();
    }
  }
}
