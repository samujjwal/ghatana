//! Task execution and management for the DCMaar agent.

use std::future::Future;

use tokio::sync::oneshot;
use tokio::task::JoinHandle;
use tracing::{error, info};

use crate::error::{Error, Result};

/// A handle to a running task that can be used to await its completion.
///
/// Tasks spawned via `Task::new` run on the Tokio runtime and return a
/// `Result<T, Error>`. The `wait` method returns the task result or an
/// `Error` if the task panicked.
pub struct Task<T> {
    name: String,
    join_handle: JoinHandle<std::result::Result<T, Error>>,
}

impl<T> Task<T> {
    /// Create a new task with the given name and future.
    pub fn new<F>(name: impl Into<String>, future: F) -> Self
    where
        F: Future<Output = Result<T>> + Send + 'static,
        T: Send + 'static,
    {
        let name = name.into();
        let log_name = name.clone();
        let join_handle = tokio::spawn(async move {
            let result = future.await;
            if let Err(ref e) = result {
                error!(task = %log_name, error = %e, "task completed with error");
            } else {
                info!(task = %log_name, "task completed successfully");
            }
            result
        });

        Self { name, join_handle }
    }

    /// Get the name of the task.
    pub fn name(&self) -> &str {
        &self.name
    }

    /// Wait for the task to complete.
    pub async fn wait(self) -> Result<T> {
        match self.join_handle.await {
            Ok(result) => result,
            Err(e) => {
                error!(task = %self.name, error = %e, "task panicked");
                Err(Error::with_context(
                    crate::error::ErrorKind::Other,
                    format!("task '{}' panicked: {}", self.name, e),
                ))
            }
        }
    }

    /// Abort the underlying join handle. This signals the spawned task to
    /// terminate. Callers should still `wait` on the task to observe any
    /// termination error and to drive the join to completion.
    pub fn abort(&self) {
        self.join_handle.abort();
    }

    /// Wait for the task, but treat a cancelled join as a non-error.
    ///
    /// Returns `Ok(None)` when the underlying join handle was cancelled
    /// (e.g., because `abort` was called). Returns `Ok(Some(value))` when
    /// the task completed successfully, and `Err` for task-local errors or
    /// panics.
    pub async fn wait_ignore_cancel(self) -> Result<Option<T>> {
        match self.join_handle.await {
            Ok(result) => match result {
                Ok(v) => Ok(Some(v)),
                Err(e) => Err(e),
            },
            Err(e) => {
                if e.is_cancelled() {
                    // Task was cancelled; treat as non-fatal during shutdown.
                    info!(task = %self.name, "task was cancelled");
                    Ok(None)
                } else {
                    error!(task = %self.name, error = %e, "task panicked");
                    Err(Error::with_context(
                        crate::error::ErrorKind::Other,
                        format!("task '{}' panicked: {}", self.name, e),
                    ))
                }
            }
        }
    }
}

/// A task that can be cancelled.
///
/// Wraps a `Task` with a cancellation channel. Calling `cancel` will send
/// the cancellation signal to the inner future; the future is responsible
/// for handling cancellation and returning an error if it canceled.
pub struct CancellableTask<T> {
    cancel_tx: Option<oneshot::Sender<()>>,
    task: Task<T>,
}

impl<T> CancellableTask<T> {
    /// Create a new cancellable task.
    pub fn new<F, Fut>(name: impl Into<String>, future: F) -> Self
    where
        F: FnOnce(oneshot::Receiver<()>) -> Fut + Send + 'static,
        Fut: Future<Output = Result<T>> + Send + 'static,
        T: Send + 'static,
    {
        let name = name.into();
        let (cancel_tx, cancel_rx) = oneshot::channel();
        let task = Task::new(name, future(cancel_rx));

        Self {
            cancel_tx: Some(cancel_tx),
            task,
        }
    }

    /// Cancel the task.
    pub fn cancel(mut self) -> Task<T> {
        if let Some(cancel_tx) = self.cancel_tx.take() {
            let _ = cancel_tx.send(());
        }
        self.task
    }

    /// Wait for the task to complete.
    pub async fn wait(self) -> Result<T> {
        self.task.wait().await
    }
}

/// A task manager for running and managing multiple tasks.
///
/// Holds spawned background tasks and can wait for or shutdown all
/// registered tasks during agent shutdown.
pub struct TaskManager {
    tasks: Vec<Task<()>>,
}

impl Default for TaskManager {
    fn default() -> Self {
        Self::new()
    }
}

impl TaskManager {
    /// Create a new task manager.
    pub fn new() -> Self {
        Self { tasks: Vec::new() }
    }

    /// Spawn a new task.
    pub fn spawn<F>(&mut self, name: impl Into<String>, future: F) -> TaskHandle
    where
        F: Future<Output = Result<()>> + Send + 'static,
    {
        let name = name.into();
        let task = Task::new(name.clone(), future);
        let handle = TaskHandle { name };
        self.tasks.push(task);
        handle
    }

    /// Wait for all tasks to complete.
    pub async fn wait_all(self) -> Result<()> {
        let mut results = Vec::with_capacity(self.tasks.len());
        for task in self.tasks {
            results.push(task.wait().await);
        }
        results.into_iter().collect()
    }

    /// Shutdown all tasks by cancelling them and waiting for completion.
    pub async fn shutdown(mut self) -> Result<()> {
        // Abort all running tasks first to avoid waiting forever on
        // non-terminating background futures, then wait for join handles
        // to complete so we observe any errors.
        let tasks = std::mem::take(&mut self.tasks);

        // Signal cancellation
        for task in &tasks {
            task.abort();
        }

        // Wait for all tasks to complete and collect results
        let mut results = Vec::with_capacity(tasks.len());
        for task in tasks {
            match task.wait_ignore_cancel().await {
                Ok(Some(_)) => results.push(Ok(())),
                Ok(None) => {
                    // cancelled - treat as success for shutdown
                    results.push(Ok(()));
                }
                Err(e) => results.push(Err(e)),
            }
        }

        // Return the first error if any
        results.into_iter().collect()
    }
}

/// A handle to a task managed by a `TaskManager`.
pub struct TaskHandle {
    name: String,
}

impl TaskHandle {
    /// Get the name of the task.
    pub fn name(&self) -> &str {
        &self.name
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::time::Duration;

    #[tokio::test]
    async fn test_task() {
        let task = Task::new("test", async { Ok::<_, Error>(42) });
        assert_eq!(task.wait().await.unwrap(), 42);
    }

    #[tokio::test]
    async fn test_cancellable_task() {
        let task = CancellableTask::new("test", |mut cancel_rx| async move {
            tokio::select! {
                _ = tokio::time::sleep(Duration::from_millis(100)) => {
                    Ok(42)
                }
                _ = &mut cancel_rx => {
                    Err(Error::with_context(crate::error::ErrorKind::Other, "cancelled"))
                }
            }
        });

        let handle = task.cancel();
        assert!(handle.wait().await.is_err());
    }

    #[tokio::test]
    async fn test_task_manager() {
        let mut manager = TaskManager::new();
        manager.spawn("task1", async { Ok(()) });
        manager.spawn("task2", async { Ok(()) });
        assert!(manager.wait_all().await.is_ok());
    }
}
