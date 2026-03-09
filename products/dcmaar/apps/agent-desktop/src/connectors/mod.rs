//! Shared connector implementations mirroring the TypeScript connector contract.
//!
//! Sprint 5 additions:
//! - `sync_source`: Polls backend `/devices/:id/sync` for policies and commands.
//! - `command_sink`: Executes commands locally and acknowledges to backend.
//! - `telemetry_sink`: Batches and sends GuardianEvents to backend.

pub mod base;
pub mod command_sink;
pub mod sync_source;
pub mod telemetry_sink;

// Sprint 5 connector exports
pub use command_sink::{
    CommandExecutionSink, CommandExecutionSinkConfig, CommandHandler, CommandResult, CommandStatus,
};
pub use sync_source::{
    ActorType, CommandBundle, CommandIssuer, CommandKind, CommandSyncSource,
    CommandSyncSourceConfig, CommandTarget, GuardianCommand, PolicyBundle, PolicyItem,
    SyncSnapshot,
};
pub use telemetry_sink::{
    EventKind, EventSource, GuardianEventPayload, PiiLevel, PrivacyInfo, TelemetrySink,
    TelemetrySinkConfig,
};
