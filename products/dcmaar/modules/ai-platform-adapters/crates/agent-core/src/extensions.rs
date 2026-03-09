//! Extension traits for common types.

use std::path::Path;
use std::time::{Duration, SystemTime};

/// Extension trait for `Path` and `PathBuf`.
pub trait PathExt {
    /// Check if the path is a directory and not empty.
    fn is_non_empty_dir(&self) -> std::io::Result<bool>;

    /// Get the file name as a string.
    fn file_name_str(&self) -> Option<&str>;

    /// Get the file stem as a string.
    fn file_stem_str(&self) -> Option<&str>;

    /// Get the parent directory as a string.
    fn parent_str(&self) -> Option<&str>;
}

impl PathExt for Path {
    fn is_non_empty_dir(&self) -> std::io::Result<bool> {
        if !self.is_dir() {
            return Ok(false);
        }

        let mut entries = std::fs::read_dir(self)?;
        Ok(entries.next().is_some())
    }

    fn file_name_str(&self) -> Option<&str> {
        self.file_name()?.to_str()
    }

    fn file_stem_str(&self) -> Option<&str> {
        self.file_stem()?.to_str()
    }

    fn parent_str(&self) -> Option<&str> {
        self.parent()?.to_str()
    }
}

/// Extension trait for `Duration`.
pub trait DurationExt {
    /// Format the duration as a human-readable string.
    fn human_readable(&self) -> String;
}

impl DurationExt for Duration {
    fn human_readable(&self) -> String {
        let secs = self.as_secs();
        if secs < 1 {
            return format!("{}ms", self.as_millis());
        }

        let (value, unit) = if secs < 60 {
            (secs, "s")
        } else if secs < 3600 {
            (secs / 60, "m")
        } else if secs < 86400 {
            (secs / 3600, "h")
        } else {
            (secs / 86400, "d")
        };

        format!("{}{}", value, unit)
    }
}

/// Extension trait for `SystemTime`.
pub trait SystemTimeExt {
    /// Format the time as a human-readable string relative to now.
    fn relative_time(&self) -> String;
}

impl SystemTimeExt for SystemTime {
    fn relative_time(&self) -> String {
        match self.elapsed() {
            Ok(elapsed) => {
                if elapsed.as_secs() < 1 {
                    "just now".to_string()
                } else if elapsed.as_secs() < 60 {
                    format!("{}s ago", elapsed.as_secs())
                } else if elapsed.as_secs() < 3600 {
                    format!("{}m ago", elapsed.as_secs() / 60)
                } else if elapsed.as_secs() < 86400 {
                    format!("{}h ago", elapsed.as_secs() / 3600)
                } else {
                    format!("{}d ago", elapsed.as_secs() / 86400)
                }
            }
            Err(_) => "in the future".to_string(),
        }
    }
}

/// Stable iterator helpers (do not rely on nightly Try/into_result).
pub trait IteratorExt: Iterator {
    /// Collect the iterator into a `Result<Vec<T>, E>` when iterating over `Result<T, E>`.
    fn collect_result<T, E>(self) -> Result<Vec<T>, E>
    where
        Self: Iterator<Item = Result<T, E>> + Sized,
    {
        self.collect()
    }
}

impl<I: Iterator> IteratorExt for I {}

/// Concrete helpers for Option iterators.
pub trait OptionIteratorExt<T>: Iterator<Item = Option<T>> + Sized {
    /// Filter out `None` values, keeping only `T`.
    #[allow(clippy::type_complexity)]
    fn filter_some(self) -> std::iter::FilterMap<Self, fn(Option<T>) -> Option<T>> {
        fn id<T>(x: Option<T>) -> Option<T> {
            x
        }
        self.filter_map(id::<T>)
    }
}
impl<I, T> OptionIteratorExt<T> for I where I: Iterator<Item = Option<T>> {}

/// Concrete helpers for Result iterators (re-exported for clarity)
pub trait ResultIteratorExt<T, E>: Iterator<Item = Result<T, E>> + Sized {
    /// Collect into Result<Vec<T>, E>
    fn collect_result(self) -> Result<Vec<T>, E> {
        self.collect()
    }
}
impl<I, T, E> ResultIteratorExt<T, E> for I where I: Iterator<Item = Result<T, E>> {}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;
    use std::time::Duration;

    #[test]
    fn test_path_ext() {
        let path = Path::new("/path/to/file.txt");
        assert_eq!(path.file_name_str(), Some("file.txt"));
        assert_eq!(path.file_stem_str(), Some("file"));
        assert_eq!(path.parent_str(), Some("/path/to"));
    }

    #[test]
    fn test_duration_ext() {
        assert_eq!(Duration::from_millis(500).human_readable(), "500ms");
        assert_eq!(Duration::from_secs(30).human_readable(), "30s");
        assert_eq!(Duration::from_secs(90).human_readable(), "1m");
        assert_eq!(Duration::from_secs(7200).human_readable(), "2h");
        assert_eq!(Duration::from_secs(172800).human_readable(), "2d");
    }

    #[test]
    fn test_system_time_ext() {
        let now = SystemTime::now();
        assert!(now.relative_time().ends_with("s ago") || now.relative_time() == "just now");
    }

    #[test]
    fn test_iterator_exts() {
        let results: Vec<Result<i32, &str>> = vec![Ok(1), Err("error"), Ok(2)];
    // Disambiguate which collect_result we want (IteratorExt) to avoid ambiguity with ResultIteratorExt
    assert_eq!(crate::extensions::IteratorExt::collect_result(results.into_iter()), Err("error"));

        let options = vec![Some(1), None, Some(2)];
        let filtered: Vec<_> = options.into_iter().filter_some().collect();
        assert_eq!(filtered, vec![1, 2]);
    }
}
