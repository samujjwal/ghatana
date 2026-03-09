use serde::{Deserialize, Serialize};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageConfig {
    /// Database connection URL
    pub database_url: String,
    /// Maximum number of database connections
    pub max_connections: u32,
    /// Connection timeout
    pub connection_timeout: Duration,
    /// Maximum number of connection retries
    pub max_retries: u32,
    /// Delay between connection retries
    pub retry_delay: Duration,
    /// Automatically create database tables
    pub auto_create_tables: bool,
    /// Automatically run migrations
    pub auto_migrate: bool,
}

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            database_url: "sqlite://metrics.db".to_string(),
            max_connections: 10,
            connection_timeout: Duration::from_secs(30),
            max_retries: 3,
            retry_delay: Duration::from_secs(1),
            auto_create_tables: true,
            auto_migrate: true,
        }
    }
}

mod duration_secs {
    use serde::{Deserialize, Deserializer, Serialize, Serializer};
    use std::time::Duration;

    pub fn serialize<S>(duration: &Duration, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_u64(duration.as_secs())
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Duration, D::Error>
    where
        D: Deserializer<'de>,
    {
        let secs = u64::deserialize(deserializer)?;
        Ok(Duration::from_secs(secs))
    }
}