// Database module following repository pattern
// Implements WSRF-DES-003 (WAL/queue pattern)

use anyhow::Result;
use sqlx::{sqlite::SqlitePool, SqliteConnection};

pub mod models;
pub mod repositories;
pub mod adapters;

/// Database connection pool
pub struct Database {
    pool: SqlitePool,
}

impl Database {
    /// Create a new database connection
    pub async fn new(database_url: &str) -> Result<Self> {
        let pool = SqlitePool::connect(database_url).await?;
        Ok(Self { pool })
    }

    /// Initialize database with migrations
    pub async fn migrate(&self) -> Result<()> {
        sqlx::migrate!("./migrations")
            .run(&self.pool)
            .await?;
        Ok(())
    }

    /// Get a reference to the connection pool
    pub fn pool(&self) -> &SqlitePool {
        &self.pool
    }

    /// Get a connection from the pool
    pub async fn connection(&self) -> Result<SqliteConnection> {
        Ok(self.pool.acquire().await?.detach())
    }

    /// Close the database connection
    pub async fn close(&self) {
        self.pool.close().await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_database_creation() {
        let db = Database::new(":memory:").await.unwrap();
        assert!(db.migrate().await.is_ok());
    }
}
