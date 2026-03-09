use crate::models::{UsageEvent, UsageSession, ContentCategory};
use agent_storage::{SqliteStorage, Storage, StorageError};
use chrono::{DateTime, Utc, TimeZone};
use sqlx::{Row, sqlite::SqliteRow};
use std::path::Path;

/// Storage layer for Guardian usage tracking
pub struct GuardianStorage {
    storage: SqliteStorage,
}

impl GuardianStorage {
    /// Create a new Guardian storage instance
    pub async fn new<P: AsRef<Path>>(db_path: P) -> Result<Self, StorageError> {
        let db_url = format!("sqlite:{}", db_path.as_ref().display());
        let migrations_path = "../../../dcmaar/framework/agent-daemon/crates/agent-storage/migrations";
        let storage = SqliteStorage::new(&db_url, migrations_path, 5).await?;
        storage.init().await?;
        Ok(Self { storage })
    }

    /// Create tables directly (for testing without migrations)
    #[allow(dead_code)]
    async fn create_tables(&self) -> Result<(), StorageError> {
        let query = r#"
            CREATE TABLE IF NOT EXISTS usage_events (
                event_id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                child_user_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                window_title TEXT,
                process_name TEXT,
                process_id INTEGER,
                executable_path TEXT,
                window_class TEXT,
                tab_url TEXT,
                tab_title TEXT,
                tab_browser TEXT,
                tab_id TEXT,
                tab_domain TEXT,
                duration_ms INTEGER,
                idle_duration INTEGER,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            );

            CREATE TABLE IF NOT EXISTS usage_sessions (
                session_id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                child_user_id TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                active_duration_seconds INTEGER NOT NULL,
                idle_duration_seconds INTEGER NOT NULL,
                app_name TEXT,
                domain TEXT,
                category TEXT,
                title TEXT,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            );

            CREATE TABLE IF NOT EXISTS daily_usage_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                child_user_id TEXT NOT NULL,
                date TEXT NOT NULL,
                category TEXT NOT NULL,
                total_duration_seconds INTEGER NOT NULL DEFAULT 0,
                session_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                UNIQUE(device_id, child_user_id, date, category)
            );
            "#;

        sqlx::query(query)
            .execute(self.storage.pool())
            .await?;

        Ok(())
    }

    /// Save a usage event
    pub async fn save_event(&self, event: &UsageEvent) -> Result<(), StorageError> {
        let query = r#"
            INSERT INTO usage_events (
                event_id, device_id, child_user_id, timestamp, event_type,
                window_title, process_name, process_id, executable_path, window_class,
                tab_url, tab_title, tab_browser, tab_id, tab_domain,
                duration_ms, idle_duration
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        "#;

        let timestamp = event.timestamp.timestamp();
        let event_type = format!("{:?}", event.event_type);
        
        let (window_title, process_name, process_id, executable_path, window_class) = 
            if let Some(ref w) = event.window_info {
                (
                    Some(w.title.as_str()),
                    Some(w.process_name.as_str()),
                    Some(w.process_id as i64),
                    w.executable_path.as_deref(),
                    w.window_class.as_deref(),
                )
            } else {
                (None, None, None, None, None)
            };

        let (tab_url, tab_title, tab_browser, tab_id, tab_domain) =
            if let Some(ref t) = event.tab_info {
                (
                    Some(t.url.as_str()),
                    Some(t.title.as_str()),
                    Some(t.browser.as_str()),
                    Some(t.tab_id.as_str()),
                    Some(t.domain.as_str()),
                )
            } else {
                (None, None, None, None, None)
            };

        sqlx::query(query)
            .bind(event.event_id.to_string())
            .bind(&event.device_id)
            .bind(&event.child_user_id)
            .bind(timestamp)
            .bind(event_type)
            .bind(window_title)
            .bind(process_name)
            .bind(process_id)
            .bind(executable_path)
            .bind(window_class)
            .bind(tab_url)
            .bind(tab_title)
            .bind(tab_browser)
            .bind(tab_id)
            .bind(tab_domain)
            .bind(event.duration_ms.map(|d| d as i64))
            .bind(event.idle_duration)
            .execute(self.storage.pool())
            .await?;

        Ok(())
    }

    /// Save a usage session
    pub async fn save_session(&self, session: &UsageSession) -> Result<(), StorageError> {
        let query = r#"
            INSERT INTO usage_sessions (
                session_id, device_id, child_user_id,
                start_time, end_time,
                duration_seconds, active_duration_seconds, idle_duration_seconds,
                app_name, domain, category, title
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        "#;

        let start_timestamp = session.start_time.timestamp();
        let end_timestamp = session.end_time.timestamp();
        let category_str = session.category.as_ref().map(|c| format!("{:?}", c));

        sqlx::query(query)
            .bind(session.session_id.to_string())
            .bind(&session.device_id)
            .bind(&session.child_user_id)
            .bind(start_timestamp)
            .bind(end_timestamp)
            .bind(session.duration_seconds)
            .bind(session.active_duration_seconds)
            .bind(session.idle_duration_seconds)
            .bind(session.app_name.as_deref())
            .bind(session.domain.as_deref())
            .bind(category_str.as_deref())
            .bind(session.title.as_deref())
            .execute(self.storage.pool())
            .await
            ?;

        Ok(())
    }

    /// Get sessions for a child user within a time range
    pub async fn get_sessions(
        &self,
        child_user_id: &str,
        start_time: DateTime<Utc>,
        end_time: DateTime<Utc>,
    ) -> Result<Vec<UsageSession>, StorageError> {
        let query = r#"
            SELECT 
                session_id, device_id, child_user_id,
                start_time, end_time,
                duration_seconds, active_duration_seconds, idle_duration_seconds,
                app_name, domain, category, title
            FROM usage_sessions
            WHERE child_user_id = ?
              AND start_time >= ?
              AND start_time <= ?
            ORDER BY start_time DESC
        "#;

        let start_timestamp = start_time.timestamp();
        let end_timestamp = end_time.timestamp();

        let rows = sqlx::query(query)
            .bind(child_user_id)
            .bind(start_timestamp)
            .bind(end_timestamp)
            .fetch_all(self.storage.pool())
            .await
            ?;

        rows.iter()
            .map(|row| self.row_to_session(row))
            .collect::<Result<Vec<_>, _>>()
    }

    /// Get daily usage summary for a child user
    pub async fn get_daily_summary(
        &self,
        child_user_id: &str,
        date: &str, // YYYY-MM-DD format
    ) -> Result<Vec<(ContentCategory, i64, i64)>, StorageError> {
        let query = r#"
            SELECT category, total_duration_seconds, session_count
            FROM daily_usage_summary
            WHERE child_user_id = ? AND date = ?
            ORDER BY total_duration_seconds DESC
        "#;

        let rows = sqlx::query(query)
            .bind(child_user_id)
            .bind(date)
            .fetch_all(self.storage.pool())
            .await
            ?;

        rows.iter()
            .map(|row| {
                let category_str: String = row.try_get("category")
                    ?;
                let category = Self::parse_category(&category_str)?;
                let duration: i64 = row.try_get("total_duration_seconds")
                    ?;
                let count: i64 = row.try_get("session_count")
                    ?;
                Ok((category, duration, count))
            })
            .collect()
    }

    /// Update or create daily summary (called after saving a session)
    pub async fn update_daily_summary(&self, session: &UsageSession) -> Result<(), StorageError> {
        if session.category.is_none() {
            return Ok(()); // Skip uncategorized sessions
        }

        let date = session.start_time.format("%Y-%m-%d").to_string();
        let category = format!("{:?}", session.category.as_ref().unwrap());

        let query = r#"
            INSERT INTO daily_usage_summary (
                device_id, child_user_id, date, category,
                total_duration_seconds, session_count
            ) VALUES (?, ?, ?, ?, ?, 1)
            ON CONFLICT(device_id, child_user_id, date, category)
            DO UPDATE SET
                total_duration_seconds = total_duration_seconds + ?,
                session_count = session_count + 1,
                updated_at = strftime('%s', 'now')
        "#;

        sqlx::query(query)
            .bind(&session.device_id)
            .bind(&session.child_user_id)
            .bind(&date)
            .bind(&category)
            .bind(session.active_duration_seconds)
            .bind(session.active_duration_seconds)
            .execute(self.storage.pool())
            .await
            ?;

        Ok(())
    }

    /// Helper: Convert database row to UsageSession
    fn row_to_session(&self, row: &SqliteRow) -> Result<UsageSession, StorageError> {
        let session_id_str: String = row.try_get("session_id")?;
        let session_id = uuid::Uuid::parse_str(&session_id_str)
            .map_err(|e| StorageError::internal(format!("Invalid session ID: {}", e)))?;

        let start_timestamp: i64 = row.try_get("start_time")?;
        let end_timestamp: i64 = row.try_get("end_time")?;

        let start_time = Utc
            .timestamp_opt(start_timestamp, 0)
            .single()
            .ok_or_else(|| StorageError::internal("Invalid start_time timestamp"))?;
        let end_time = Utc
            .timestamp_opt(end_timestamp, 0)
            .single()
            .ok_or_else(|| StorageError::internal("Invalid end_time timestamp"))?;

        let category_str: Option<String> = row.try_get("category")?;
        let category = category_str.as_ref()
            .map(|s| Self::parse_category(s))
            .transpose()?;

        Ok(UsageSession {
            session_id,
            device_id: row.try_get("device_id")
                ?,
            child_user_id: row.try_get("child_user_id")
                ?,
            start_time,
            end_time,
            duration_seconds: row.try_get("duration_seconds")
                ?,
            active_duration_seconds: row.try_get("active_duration_seconds")
                ?,
            idle_duration_seconds: row.try_get("idle_duration_seconds")
                ?,
            app_name: row.try_get("app_name")
                ?,
            domain: row.try_get("domain")
                ?,
            category,
            title: row.try_get("title")
                ?,
        })
    }

    /// Helper: Parse category string
    fn parse_category(s: &str) -> Result<ContentCategory, StorageError> {
        match s {
            "Educational" => Ok(ContentCategory::Educational),
            "Social" => Ok(ContentCategory::Social),
            "Gaming" => Ok(ContentCategory::Gaming),
            "Entertainment" => Ok(ContentCategory::Entertainment),
            "Productivity" => Ok(ContentCategory::Productivity),
            "Shopping" => Ok(ContentCategory::Shopping),
            "News" => Ok(ContentCategory::News),
            "Communication" => Ok(ContentCategory::Communication),
            "Unknown" => Ok(ContentCategory::Unknown),
            _ => Err(StorageError::internal(format!("Unknown category: {}", s))),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::{EventType, WindowInfo};
    use tempfile::TempDir;

    async fn create_test_storage() -> (GuardianStorage, TempDir) {
        let temp_dir = TempDir::new().unwrap();
        let db_path = temp_dir.path().join("test.db");
        
        // Create storage without migrations for testing
        let db_url = format!("sqlite:{}", db_path.display());
        let storage_backend = SqliteStorage::new(&db_url, "", 5).await.unwrap();
        let storage = GuardianStorage { storage: storage_backend };
        
        // Create tables directly
        storage.create_tables().await.unwrap();
        
        (storage, temp_dir)
    }

    #[tokio::test]
    async fn test_save_and_retrieve_event() {
        let (storage, _temp_dir) = create_test_storage().await;

        let event = UsageEvent::new(
            "device-123".to_string(),
            "child-456".to_string(),
            EventType::WindowActivated,
        )
        .with_window_info(WindowInfo {
            title: "Test Window".to_string(),
            process_name: "TestApp".to_string(),
            process_id: 1234,
            executable_path: Some("/path/to/app".to_string()),
            window_class: Some("TestClass".to_string()),
        })
        .with_duration_ms(5000);

        storage.save_event(&event).await.unwrap();
    }

    #[tokio::test]
    async fn test_save_and_retrieve_session() {
        let (storage, _temp_dir) = create_test_storage().await;

        let now = Utc::now();
        let session = UsageSession {
            session_id: uuid::Uuid::new_v4(),
            device_id: "device-123".to_string(),
            child_user_id: "child-456".to_string(),
            start_time: now,
            end_time: now + chrono::Duration::minutes(5),
            duration_seconds: 300,
            active_duration_seconds: 280,
            idle_duration_seconds: 20,
            app_name: Some("TestApp".to_string()),
            domain: None,
            category: Some(ContentCategory::Productivity),
            title: Some("Test Window".to_string()),
        };

        storage.save_session(&session).await.unwrap();

        let sessions = storage
            .get_sessions(
                "child-456",
                now - chrono::Duration::hours(1),
                now + chrono::Duration::hours(1),
            )
            .await
            .unwrap();

        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].session_id, session.session_id);
        assert_eq!(sessions[0].app_name, Some("TestApp".to_string()));
    }

    #[tokio::test]
    async fn test_daily_summary_update() {
        let (storage, _temp_dir) = create_test_storage().await;

        let now = Utc::now();
        let session = UsageSession {
            session_id: uuid::Uuid::new_v4(),
            device_id: "device-123".to_string(),
            child_user_id: "child-456".to_string(),
            start_time: now,
            end_time: now + chrono::Duration::minutes(5),
            duration_seconds: 300,
            active_duration_seconds: 280,
            idle_duration_seconds: 20,
            app_name: Some("TestApp".to_string()),
            domain: None,
            category: Some(ContentCategory::Educational),
            title: Some("Test Window".to_string()),
        };

        storage.save_session(&session).await.unwrap();
        storage.update_daily_summary(&session).await.unwrap();

        let date = now.format("%Y-%m-%d").to_string();
        let summary = storage.get_daily_summary("child-456", &date).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(matches!(summary[0].0, ContentCategory::Educational));
        assert_eq!(summary[0].1, 280); // active duration
        assert_eq!(summary[0].2, 1); // session count
    }
}
