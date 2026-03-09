use std::time::Duration;

use agent_rs::storage::{MetricsStorage, Storage};
use sqlx::{sqlite::SqlitePoolOptions, SqlitePool};
use time::OffsetDateTime;

async fn setup_db() -> SqlitePool {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .unwrap();

    // minimal schema for tests
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            metric_type TEXT NOT NULL,
            hostname TEXT NOT NULL,
            data TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        "#,
    )
    .execute(&pool)
    .await
    .unwrap();
    pool
}

#[tokio::test]
async fn retention_deletes_old_rows() {
    // Build in-memory storage and ensure schema exists
    let storage = Storage::memory().await.unwrap();
    let pool = storage.pool().clone();
    // create schema for metrics table
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            metric_type TEXT NOT NULL,
            hostname TEXT NOT NULL,
            data TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        "#,
    )
    .execute(&pool)
    .await
    .unwrap();
    let metrics = MetricsStorage::new(storage);

    // Insert two rows: one old, one recent
    let now_ms = (OffsetDateTime::now_utc().unix_timestamp_nanos() / 1_000_000) as i64;
    let old_ms = now_ms - (10_i64 * 24 * 60 * 60 * 1000); // ~10 days

    sqlx::query(
        "INSERT INTO metrics (metric_type, hostname, timestamp, data) VALUES (?1, ?2, ?3, ?4)",
    )
    .bind("cpu")
    .bind("host")
    .bind(old_ms)
    .bind("{}")
    .execute(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO metrics (metric_type, hostname, timestamp, data) VALUES (?1, ?2, ?3, ?4)",
    )
    .bind("cpu")
    .bind("host")
    .bind(now_ms)
    .bind("{}")
    .execute(&pool)
    .await
    .unwrap();

    let deleted = metrics
        .cleanup_old_metrics(Duration::from_secs(7 * 24 * 3600))
        .await
        .unwrap();
    assert_eq!(deleted, 1);
}

#[tokio::test]
async fn budget_enforced_when_exceeded() {
    let storage = Storage::memory().await.unwrap();
    let pool = storage.pool().clone();
    // create schema
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            metric_type TEXT NOT NULL,
            hostname TEXT NOT NULL,
            data TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        "#,
    )
    .execute(&pool)
    .await
    .unwrap();
    let metrics = MetricsStorage::new(storage);

    // Insert a row with data ~100 bytes
    let ts_ms = (OffsetDateTime::now_utc().unix_timestamp_nanos() / 1_000_000) as i64;
    let data = "{\"x\":\"".to_string() + &"a".repeat(128) + "\"}";
    sqlx::query(
        "INSERT INTO metrics (metric_type, hostname, timestamp, data) VALUES (?1, ?2, ?3, ?4)",
    )
    .bind("cpu")
    .bind("host")
    .bind(ts_ms)
    .bind(data)
    .execute(&pool)
    .await
    .unwrap();

    // Set budget below current size to trigger error
    let err = metrics.enforce_storage_budget(10).await.err().unwrap();
    assert!(format!("{}", err).contains("storage budget exceeded"));
}
