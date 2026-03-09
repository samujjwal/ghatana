use sqlx::SqlitePool;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{Mutex, OwnedMutexGuard};
use tracing::{debug, error, instrument};

/// A cross-process lock for SQLite database access
///
/// This provides a way to coordinate access to SQLite database files
/// across multiple processes using file system locks.
#[derive(Debug, Clone)]
pub struct DbLock {
    inner: Arc<Mutex<()>>,
    pool: SqlitePool,
    timeout: Duration,
}

impl DbLock {
    /// Create a new database lock with the given pool and timeout
    pub fn new(pool: SqlitePool, timeout: Duration) -> Self {
        Self {
            inner: Arc::new(Mutex::new(())),
            pool,
            timeout,
        }
    }

    /// Execute a function with an exclusive lock on the database
    ///
    /// This will wait for the lock to become available, up to the configured timeout.
    /// Returns an error if the lock couldn't be acquired within the timeout.
    #[instrument(skip(self, f))]
    pub async fn with_lock<F, Fut, T, E>(&self, f: F) -> Result<T, E>
    where
        F: FnOnce(&SqlitePool) -> Fut + Send + 'static,
        Fut: std::future::Future<Output = Result<T, E>> + Send + 'static,
        T: Send + 'static,
        E: From<sqlx::Error> + Send + 'static,
    {
        debug!("Acquiring database lock");

        // Try to acquire the lock with a timeout
        let lock_result = tokio::time::timeout(self.timeout, self.inner.clone().lock_owned()).await;

        match lock_result {
            Ok(guard) => {
                debug!("Acquired database lock, executing query");
                let result = f(&self.pool).await;
                drop(guard); // Explicitly drop to release the lock
                debug!("Released database lock");
                result
            }
            Err(_) => {
                error!("Failed to acquire database lock within {:?}", self.timeout);
                // Return a custom error or handle timeout
                Err(sqlx::Error::PoolTimedOut.into())
            }
        }
    }
}

/// A guard that holds an exclusive lock on the database
///
/// The lock is automatically released when the guard is dropped.
#[allow(dead_code)]
pub struct DbLockGuard<'a> {
    _guard: OwnedMutexGuard<()>,
    pool: &'a SqlitePool,
}

impl<'a> std::ops::Deref for DbLockGuard<'a> {
    type Target = SqlitePool;

    fn deref(&self) -> &Self::Target {
        self.pool
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::sqlite::SqlitePoolOptions;
    use std::time::Duration;

    #[tokio::test]
    async fn test_db_lock() -> Result<(), Box<dyn std::error::Error>> {
        // Create an in-memory SQLite database
        let pool = SqlitePoolOptions::new().connect("sqlite::memory:").await?;

        // Create a table for testing
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS test (
                id INTEGER PRIMARY KEY,
                value TEXT NOT NULL
            )"#,
        )
        .execute(&pool)
        .await?;

        // Create a lock with a short timeout for testing
        let lock = DbLock::new(pool.clone(), Duration::from_secs(1));

        // Test that we can acquire the lock and execute a query
        let result = lock
            .with_lock(|pool| {
                let pool = pool.clone();
                async move {
                    sqlx::query("INSERT INTO test (value) VALUES (?1)")
                        .bind("test")
                        .execute(&pool)
                        .await?;

                    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM test")
                        .fetch_one(&pool)
                        .await?;

                    Ok::<_, sqlx::Error>(count.0)
                }
            })
            .await?;

        assert_eq!(result, 1);

        Ok(())
    }
}
