// Event classification using AI
// Classifies events by severity and category

use anyhow::Result;
use tracing::debug;

use crate::ai::types::{EventClassification, AIConfig};
use crate::db::models::Event;

pub struct EventClassifier {
    config: AIConfig,
}

impl EventClassifier {
    pub fn new(config: AIConfig) -> Self {
        Self { config }
    }

    /// Classify an event using AI
    pub async fn classify_event(&self, event: &Event) -> Result<EventClassification> {
        if !self.config.enabled || !self.config.event_classification_enabled {
            return Ok(self.default_classification(event));
        }

        // TODO: Call AI service for classification
        debug!("Classifying event: {}", event.event_id);

        Ok(self.default_classification(event))
    }

    /// Classify multiple events in batch
    pub async fn classify_batch(&self, events: Vec<&Event>) -> Result<Vec<EventClassification>> {
        let mut classifications = Vec::new();

        for event in events {
            classifications.push(self.classify_event(event).await?);
        }

        Ok(classifications)
    }

    fn default_classification(&self, event: &Event) -> EventClassification {
        EventClassification {
            event_id: event.event_id.clone(),
            predicted_severity: event.severity.clone(),
            predicted_category: event.event_type.clone(),
            confidence: 0.5,
            suggested_actions: vec![],
        }
    }

    pub fn update_config(&mut self, config: AIConfig) {
        self.config = config;
    }
}
