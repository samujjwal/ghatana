export type UserRole = "parent" | "child" | "admin";

export interface PermissionSet {
    canViewDevices: boolean;
    canManagePolicies: boolean;
    canViewUsage: boolean;
    canViewAlerts: boolean;
    canManageSettings: boolean;
    canViewReports: boolean;
    canBlockApps: boolean;
    canManageUsers: boolean;
    canViewAnalytics: boolean;
}

export interface RoleDefinition {
    role: UserRole;
    permissions: PermissionSet;
    sections: string[];
    actions: string[];
}

export const PERMISSIONS: Record<UserRole, PermissionSet> = {
    parent: {
        canViewDevices: true,
        canManagePolicies: true,
        canViewUsage: true,
        canViewAlerts: true,
        canManageSettings: true,
        canViewReports: true,
        canBlockApps: false,
        canManageUsers: false,
        canViewAnalytics: true
    },
    child: {
        canViewDevices: false,
        canManagePolicies: false,
        canViewUsage: true,
        canViewAlerts: false,
        canManageSettings: true,
        canBlockApps: false,
        canManageUsers: false,
        canViewReports: false,
        canViewAnalytics: false
    },
    admin: {
        canViewDevices: true,
        canManagePolicies: true,
        canViewUsage: true,
        canViewAlerts: true,
        canManageSettings: true,
        canViewReports: true,
        canBlockApps: true,
        canManageUsers: true,
        canViewAnalytics: true
    }
};

export const ROLE_CONFIG: Record<UserRole, RoleDefinition> = {
    parent: {
        role: "parent",
        permissions: PERMISSIONS.parent,
        sections: ["dashboard", "devices", "usage", "policies", "alerts", "analytics"],
        actions: ["createPolicy", "viewReport", "manageDevice", "setTimeLimit"]
    },
    child: {
        role: "child",
        permissions: PERMISSIONS.child,
        sections: ["dashboard", "usage", "blocks", "settings"],
        actions: ["viewUsage", "requestUnblock", "updateSettings"]
    },
    admin: {
        role: "admin",
        permissions: PERMISSIONS.admin,
        sections: [
            "dashboard",
            "devices",
            "usage",
            "policies",
            "alerts",
            "analytics",
            "admin"
        ],
        actions: ["all"]
    }
};
