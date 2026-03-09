use serde_json::Value;
use std::time::Duration;

use crate::bridge::{BridgeMessage, ConnectorBridge};
use crate::config::RetryConfig;
use crate::events::{new_event_from_router_decision, new_event_from_router_health, EventsStorage};
use crate::policy::{routing_policies, PolicyEngine, PolicyEngineConfig, RouterDecision};
use crate::{Error, Result};

/// Prototype router agent that uses the PolicyEngine routing capabilities
/// and emits router telemetry events. This struct is intended as a thin
/// adapter around PolicyEngine for routing decisions.
pub struct RouterAgent {
    engine: PolicyEngine,
    storage: EventsStorage,
    component: String,
}

impl RouterAgent {
    /// Create a new router agent with default policy engine configuration and
    /// default routing policies.
    pub fn new(storage: EventsStorage, component: impl Into<String>) -> Self {
        let mut engine = PolicyEngine::new(PolicyEngineConfig::default());

        // Install default routing policies; best-effort and non-fatal.
        for policy in routing_policies::create_default_routing_policies() {
            let _ = engine.add_policy(policy);
        }

        Self {
            engine,
            storage,
            component: component.into(),
        }
    }

    /// Expose mutable access to the underlying policy engine for callers that
    /// want to add or modify policies before handling events.
    pub fn engine_mut(&mut self) -> &mut PolicyEngine {
        &mut self.engine
    }

    /// Handle a single event: evaluate routing policies, persist a router
    /// decision telemetry event, and return the structured decision.
    pub async fn handle_event(&mut self, event: Value) -> Result<RouterDecision> {
        let decision = self
            .engine
            .enforce_routing_for_event(event.clone(), self.component.clone())?;

        let telemetry_event = new_event_from_router_decision(&self.component, &decision);
        let _ = self.storage.insert(telemetry_event).await?;

        Ok(decision)
    }

    /// Handle an event and forward a routed representation via the connector bridge.
    /// Uses RetryConfig for basic backoff on transient connector errors.
    pub async fn handle_event_and_forward(
        &mut self,
        event: Value,
        bridge: &ConnectorBridge,
        retry_cfg: &RetryConfig,
    ) -> Result<RouterDecision> {
        let decision = self.handle_event(event.clone()).await?;

        let msg = self.build_bridge_message(&event, &decision);
        send_with_retry(bridge, &msg, retry_cfg).await?;

        Ok(decision)
    }

    /// Create a BridgeMessage representing a routed event and its decision.
    /// This does not send the message; callers can use ConnectorBridge to
    /// forward it to the TypeScript connector layer.
    pub fn build_bridge_message(&self, event: &Value, decision: &RouterDecision) -> BridgeMessage {
        let payload = serde_json::json!({
            "event": event,
            "routerDecision": {
                "allowed": decision.allowed,
                "destinations": decision.destinations,
                "violations": decision.violations,
                "metadata": decision.metadata,
                "evaluationDurationMs": decision.evaluation_duration.as_millis(),
            },
        });

        BridgeMessage::new(
            uuid::Uuid::new_v4().to_string(),
            "router.routed_event",
            payload,
        )
    }

    /// Persist a router health snapshot based on the policy engine statistics.
    pub async fn record_health_snapshot(&self) -> Result<()> {
        let stats = self.engine.get_statistics();
        let evt = new_event_from_router_health(&stats);
        let _ = self.storage.insert(evt).await?;
        Ok(())
    }
}

async fn send_with_retry(
    bridge: &ConnectorBridge,
    msg: &BridgeMessage,
    retry_cfg: &RetryConfig,
) -> Result<()> {
    let mut attempt: u32 = 0;
    let mut backoff_ms: u64 = retry_cfg.initial_backoff_ms as u64;

    loop {
        attempt = attempt.saturating_add(1);
        let msg_clone = msg.clone();

        match bridge.send(msg_clone).await {
            Ok(_resp) => {
                return Ok(());
            }
            Err(e) => {
                if attempt >= retry_cfg.max_retries {
                    return Err(Error::RetryLimitExceeded {
                        message: format!("connector bridge send failed: {}", e),
                        attempt,
                    });
                }

                tokio::time::sleep(Duration::from_millis(backoff_ms)).await;
                backoff_ms = (backoff_ms.saturating_mul(2)).min(retry_cfg.max_backoff_ms as u64);
            }
        }
    }
}
