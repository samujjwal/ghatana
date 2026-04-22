package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChangeDebouncer Tests [GH-90000]")
class ChangeDebouncerTest {

  @Test
  @DisplayName("debounce replaces existing scheduled action for the same key [GH-90000]")
  void debounceReplacesExistingScheduledActionForSameKey() { // GH-90000
    List<FakeCancellation> scheduled = new ArrayList<>(); // GH-90000
    ChangeDebouncer debouncer =
        new ChangeDebouncer( // GH-90000
            (delay, action) -> { // GH-90000
              FakeCancellation cancellation = new FakeCancellation(delay, action); // GH-90000
              scheduled.add(cancellation); // GH-90000
              return cancellation;
            });

    debouncer.debounce("file-1", Duration.ofSeconds(2), () -> {}); // GH-90000
    debouncer.debounce("file-1", Duration.ofSeconds(3), () -> {}); // GH-90000

    assertThat(scheduled).hasSize(2); // GH-90000
    assertThat(scheduled.get(0).cancelled).isTrue(); // GH-90000
    assertThat(scheduled.get(1).cancelled).isFalse(); // GH-90000
    assertThat(debouncer.pendingCount()).isEqualTo(1); // GH-90000
    assertThat(scheduled.get(1).delay).isEqualTo(Duration.ofSeconds(3)); // GH-90000
  }

  @Test
  @DisplayName("debounce executes action and removes pending key [GH-90000]")
  void debounceExecutesActionAndRemovesPendingKey() { // GH-90000
    AtomicInteger executions = new AtomicInteger(); // GH-90000
    List<FakeCancellation> scheduled = new ArrayList<>(); // GH-90000
    ChangeDebouncer debouncer =
        new ChangeDebouncer( // GH-90000
            (delay, action) -> { // GH-90000
              FakeCancellation cancellation = new FakeCancellation(delay, action); // GH-90000
              scheduled.add(cancellation); // GH-90000
              return cancellation;
            });

    debouncer.debounce("file-2", Duration.ofMillis(250), executions::incrementAndGet); // GH-90000
    scheduled.get(0).run(); // GH-90000

    assertThat(executions.get()).isEqualTo(1); // GH-90000
    assertThat(debouncer.pendingCount()).isZero(); // GH-90000
  }

  private static final class FakeCancellation implements ChangeDebouncer.Cancellation {
    private final Duration delay;
    private final Runnable action;
    private boolean cancelled;

    private FakeCancellation(Duration delay, Runnable action) { // GH-90000
      this.delay = delay;
      this.action = action;
    }

    @Override
    public void cancel() { // GH-90000
      cancelled = true;
    }

    private void run() { // GH-90000
      action.run(); // GH-90000
    }
  }
}
