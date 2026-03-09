import { createContext, useContext, type ReactNode } from "react";
import type { UserRole, RoleDefinition, PermissionSet } from "./types";
import { ROLE_CONFIG } from "./types";

export const RoleContext = createContext<RoleDefinition | null>(null);

export function useRole(): RoleDefinition {
    const context = useContext(RoleContext);
    if (!context) {
        throw new Error("useRole must be used within RoleContext.Provider");
    }
    return context;
}

export function useCanDo(permission: keyof PermissionSet): boolean {
    const { permissions } = useRole();
    return permissions[permission] ?? false;
}

export function useCanSeeSections(section: string | string[]): boolean {
    const { sections } = useRole();
    const sectionList = Array.isArray(section) ? section : [section];
    return sectionList.some((s) => sections.includes(s));
}

export function useCanDoAction(action: string): boolean {
    const { actions } = useRole();
    if (actions.includes("all")) {
        return true;
    }
    return actions.includes(action);
}

export function PermissionGuard(props: {
    permission: keyof PermissionSet;
    children: ReactNode;
    fallback?: ReactNode;
}): ReactNode {
    const canAccess = useCanDo(props.permission);
    if (canAccess) {
        return props.children;
    }
    return props.fallback ?? null;
}

export function SectionGuard(props: { section: string; children: ReactNode }): ReactNode {
    const canSee = useCanSeeSections(props.section);
    if (canSee) {
        return props.children;
    }
    return null;
}

export function getRoleConfig(role: UserRole): RoleDefinition {
    return ROLE_CONFIG[role];
}

export type { UserRole, RoleDefinition, PermissionSet };
export { ROLE_CONFIG };
