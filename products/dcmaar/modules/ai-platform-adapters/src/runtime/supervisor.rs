//! Supervisor runtime component.
//!
//! Provides a lightweight supervision tree for Tokio tasks. Each supervised
//! task is created via a factory closure and restarted using an exponential
//! backoff strategy with jitter whenever it returns an error. A clean Ok(() )
//! completion stops supervision (used for one-shot tasks that may restart on
//! failure but are allowed to exit normally once successful).
//!
//! Typical usage:
//! ```rust,no_run
//! # use agent_rs::runtime::supervisor::{Supervisor, RestartPolicy};
//! let supervisor = Supervisor::new(Default::default());
//! supervisor.spawn_supervised("example", || async {
//!     // do work; return Err(anyhow::Error) to trigger restart
//!     Ok(())
//! });
//! ```
//! The policy (initial/max backoff, jitter) can be tuned via `RestartPolicy`.
//! Jitter helps prevent thundering herd patterns when multiple tasks fail
//! simultaneously.
use std::time::Duration;

use rand::Rng;
use tokio::task::JoinHandle;
use tracing::Instrument;

/// Policy for supervised task restarts
#[derive(Clone, Debug)]
pub struct RestartPolicy {
    /// Initial backoff duration
    pub initial_backoff: Duration,
    /// Maximum backoff duration
    pub max_backoff: Duration,
    /// Jitter percentage (0.0..=1.0)
    pub jitter: f64,
}

impl Default for RestartPolicy {
    fn default() -> Self {
        Self {
            initial_backoff: Duration::from_millis(200),
            max_backoff: Duration::from_secs(30),
            jitter: 0.2,
        }
    }
}

/// Supervisor spawns and monitors async tasks, restarting them on failure
#[derive(Clone)]
pub struct Supervisor {
    policy: RestartPolicy,
}

impl Supervisor {
    /// Create a supervisor with the provided restart policy.
    pub fn new(policy: RestartPolicy) -> Self {
        Self { policy }
    }

    /// Spawn a supervised task. The `factory` is called on each restart to create a new future.
    pub fn spawn_supervised<Fut, Factory>(
        &self,
        name: &'static str,
        factory: Factory,
    ) -> JoinHandle<()>
    where
        Fut: std::future::Future<Output = anyhow::Result<()>> + Send + 'static,
        Factory: Fn() -> Fut + Send + Sync + 'static,
    {
        let policy = self.policy.clone();
        let span = tracing::info_span!("supervised_task", task = name);
        tokio::spawn(
            async move {
                let mut backoff = policy.initial_backoff;
                loop {
                    let res = factory().instrument(tracing::info_span!("run")).await;
                    match res {
                        Ok(()) => {
                            tracing::info!("task exited cleanly; stopping supervision");
                            break;
                        }
                        Err(err) => {
                            tracing::error!(error = %err, "task failed; scheduling restart");
                            // Sleep with jittered backoff
                            let jitter = 1.0
                                + (rand::thread_rng().gen_range(-policy.jitter..=policy.jitter));
                            let sleep_for = Duration::from_millis(
                                (backoff.as_millis() as f64 * jitter)
                                    .clamp(50.0, policy.max_backoff.as_millis() as f64)
                                    as u64,
                            );
                            tokio::time::sleep(sleep_for).await;
                            // Exponential backoff with cap
                            backoff = std::cmp::min(backoff.saturating_mul(2), policy.max_backoff);
                        }
                    }
                }
            }
            .instrument(span),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Arc;

    #[tokio::test]
    async fn restarts_on_error() {
        let sup = Supervisor::new(Default::default());
        let attempts = Arc::new(AtomicUsize::new(0));
        let attempts_clone = attempts.clone();
        let handle = sup.spawn_supervised("flaky", move || {
            let run_attempt = attempts_clone.fetch_add(1, Ordering::SeqCst) + 1;
            async move {
                if run_attempt < 2 {
                    Err(anyhow::anyhow!("fail first"))
                } else {
                    // exit cleanly on second attempt
                    Ok(())
                }
            }
        });
        let _ = handle.await;
        assert!(attempts.load(Ordering::SeqCst) >= 2);
    }
}
