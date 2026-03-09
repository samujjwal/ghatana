//! Registry of shell actions allowed for execution by the agent.

use crate::config::{ActionsConfig, AllowedAction};
use std::collections::HashMap;

/// Metadata describing an allowed action.
#[derive(Debug, Clone, Default)]
pub struct ActionSpec {
    /// Maximum number of arguments permitted when invoking the action.
    pub max_args: usize,
}

/// In-memory registry enforcing the configured allow-list.
#[derive(Debug, Clone, Default)]
pub struct ActionRegistry {
    allowed: HashMap<String, ActionSpec>,
}

impl ActionRegistry {
    /// Create an empty registry with no allowed actions.
    pub fn new() -> Self {
        Self {
            allowed: HashMap::new(),
        }
    }

    /// Register an allowed command along with its specification.
    pub fn allow(mut self, cmd: impl Into<String>, spec: ActionSpec) -> Self {
        self.allowed.insert(cmd.into(), spec);
        self
    }

    /// Return `true` if the command is allowed with the provided arguments.
    pub fn is_allowed(&self, cmd: &str, args: &[String]) -> bool {
        match self.allowed.get(cmd) {
            Some(spec) => args.len() <= spec.max_args,
            None => false,
        }
    }

    /// Build a registry from the configuration allow-list.
    pub fn from_config(cfg: &ActionsConfig) -> Self {
        let mut reg = ActionRegistry::new();
        for AllowedAction {
            command, max_args, ..
        } in &cfg.allowed
        {
            reg = reg.allow(
                command.clone(),
                ActionSpec {
                    max_args: *max_args,
                },
            );
        }
        reg
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allow_and_check() {
        let reg = ActionRegistry::new().allow("echo", ActionSpec { max_args: 3 });
        assert!(reg.is_allowed("echo", &["hi".into()]));
        assert!(!reg.is_allowed(
            "echo",
            &["a".into(), "b".into(), "c".into(), "d".into()]
        ));
        assert!(!reg.is_allowed("rm", &[]));
    }
}
