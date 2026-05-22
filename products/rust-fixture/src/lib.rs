//! Rust fixture library for Kernel lifecycle validation.
//!
//! This is a minimal library used to validate the CargoRustAdapter
//! can handle Rust library projects through the full lifecycle.

use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct Message {
    pub text: String,
}

impl Message {
    pub fn new(text: impl Into<String>) -> Self {
        Self {
            text: text.into(),
        }
    }

    pub fn greet(&self) -> String {
        format!("Hello, {}!", self.text)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_greet() {
        let msg = Message::new("World");
        assert_eq!(msg.greet(), "Hello, World!");
    }

    #[test]
    fn test_new() {
        let msg = Message::new("Test");
        assert_eq!(msg.text, "Test");
    }
}
