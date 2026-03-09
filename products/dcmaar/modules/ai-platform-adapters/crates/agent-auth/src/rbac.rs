//! Role-based access control (RBAC)
//!
//! Defines roles and permissions for fine-grained access control.

use serde::{Deserialize, Serialize};

/// User roles with hierarchical permissions
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Role {
    /// Full system access
    Admin,
    /// Can write events and metrics
    Writer,
    /// Can read events and metrics
    Reader,
    /// Limited user access
    User,
    /// Service account (automated systems)
    Service,
}

impl Role {
    /// Check if this role has the given permission
    pub fn has_permission(&self, permission: Permission) -> bool {
        match (self, permission) {
            // Admin has all permissions
            (Role::Admin, _) => true,

            // Writer permissions
            (Role::Writer, Permission::WriteEvents) => true,
            (Role::Writer, Permission::WriteMetrics) => true,
            (Role::Writer, Permission::ReadEvents) => true,
            (Role::Writer, Permission::ReadMetrics) => true,

            // Reader permissions
            (Role::Reader, Permission::ReadEvents) => true,
            (Role::Reader, Permission::ReadMetrics) => true,

            // User permissions (limited)
            (Role::User, Permission::ReadEvents) => true,

            // Service permissions (automated systems)
            (Role::Service, Permission::WriteEvents) => true,
            (Role::Service, Permission::WriteMetrics) => true,

            // Default deny
            _ => false,
        }
    }

    /// Get all permissions for this role
    pub fn permissions(&self) -> Vec<Permission> {
        Permission::all()
            .into_iter()
            .filter(|&p| self.has_permission(p))
            .collect()
    }
}

/// Fine-grained permissions
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Permission {
    /// Can submit events
    WriteEvents,
    /// Can submit metrics
    WriteMetrics,
    /// Can read events
    ReadEvents,
    /// Can read metrics
    ReadMetrics,
    /// Can manage API keys
    ManageApiKeys,
    /// Can view system configuration
    ViewConfig,
    /// Can modify system configuration
    ModifyConfig,
    /// Can view metrics and telemetry
    ViewTelemetry,
}

impl Permission {
    /// Get all available permissions
    pub fn all() -> Vec<Permission> {
        vec![
            Permission::WriteEvents,
            Permission::WriteMetrics,
            Permission::ReadEvents,
            Permission::ReadMetrics,
            Permission::ManageApiKeys,
            Permission::ViewConfig,
            Permission::ModifyConfig,
            Permission::ViewTelemetry,
        ]
    }

    /// Get human-readable description
    pub fn description(&self) -> &'static str {
        match self {
            Permission::WriteEvents => "Submit events to the system",
            Permission::WriteMetrics => "Submit metrics to the system",
            Permission::ReadEvents => "Query and read events",
            Permission::ReadMetrics => "Query and read metrics",
            Permission::ManageApiKeys => "Create, revoke, and manage API keys",
            Permission::ViewConfig => "View system configuration",
            Permission::ModifyConfig => "Modify system configuration",
            Permission::ViewTelemetry => "View system telemetry and health metrics",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_admin_has_all_permissions() {
        let admin = Role::Admin;
        for permission in Permission::all() {
            assert!(
                admin.has_permission(permission),
                "Admin should have {:?}",
                permission
            );
        }
    }

    #[test]
    fn test_writer_permissions() {
        let writer = Role::Writer;
        assert!(writer.has_permission(Permission::WriteEvents));
        assert!(writer.has_permission(Permission::WriteMetrics));
        assert!(writer.has_permission(Permission::ReadEvents));
        assert!(writer.has_permission(Permission::ReadMetrics));
        assert!(!writer.has_permission(Permission::ManageApiKeys));
        assert!(!writer.has_permission(Permission::ModifyConfig));
    }

    #[test]
    fn test_reader_permissions() {
        let reader = Role::Reader;
        assert!(!reader.has_permission(Permission::WriteEvents));
        assert!(!reader.has_permission(Permission::WriteMetrics));
        assert!(reader.has_permission(Permission::ReadEvents));
        assert!(reader.has_permission(Permission::ReadMetrics));
    }

    #[test]
    fn test_user_permissions() {
        let user = Role::User;
        assert!(!user.has_permission(Permission::WriteEvents));
        assert!(user.has_permission(Permission::ReadEvents));
        assert!(!user.has_permission(Permission::ManageApiKeys));
    }

    #[test]
    fn test_service_permissions() {
        let service = Role::Service;
        assert!(service.has_permission(Permission::WriteEvents));
        assert!(service.has_permission(Permission::WriteMetrics));
        assert!(!service.has_permission(Permission::ReadEvents));
        assert!(!service.has_permission(Permission::ManageApiKeys));
    }

    #[test]
    fn test_role_permissions_list() {
        let admin = Role::Admin;
        let perms = admin.permissions();
        assert_eq!(perms.len(), Permission::all().len());

        let reader = Role::Reader;
        let perms = reader.permissions();
        assert!(perms.contains(&Permission::ReadEvents));
        assert!(!perms.contains(&Permission::WriteEvents));
    }
}
