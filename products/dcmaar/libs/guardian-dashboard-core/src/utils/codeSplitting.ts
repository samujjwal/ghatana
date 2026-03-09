import { lazy, type ComponentType, type LazyExoticComponent } from "react";
import type { UserRole } from "../roles";

export type FeatureName =
    | "devices"
    | "usage"
    | "policies"
    | "alerts"
    | "analytics"
    | "blocks"
    | "settings"
    | "admin";

const ROLE_FEATURES: Record<UserRole, FeatureName[]> = {
    parent: ["devices", "usage", "policies", "alerts", "analytics"],
    child: ["usage", "blocks", "settings"],
    admin: ["devices", "usage", "policies", "alerts", "analytics", "admin"]
};

const featureModules: Partial<
    Record<FeatureName, () => Promise<{ default: ComponentType<any> }>>
> = {
    devices: async () => {
        const module = await import("../features/devices/DevicesSection");
        return { default: module.DevicesSection as ComponentType<any> };
    },
    usage: async () => {
        const module = await import("../features/usage/UsageSection");
        return { default: module.UsageSection as ComponentType<any> };
    },
    policies: async () => {
        const module = await import("../features/policies/PoliciesSection");
        return { default: module.PoliciesSection as ComponentType<any> };
    },
    alerts: async () => {
        const module = await import("../features/alerts/AlertsSection");
        return { default: module.AlertsSection as ComponentType<any> };
    }
};

const lazyComponents: Partial<
    Record<FeatureName, LazyExoticComponent<ComponentType<any>>>
> = {};

export function getFeaturesForRole(role: UserRole): FeatureName[] {
    return ROLE_FEATURES[role] || [];
}

export function getFeatureComponent(
    feature: FeatureName
): LazyExoticComponent<ComponentType<any>> {
    if (!lazyComponents[feature]) {
        const loader = featureModules[feature];
        if (!loader) {
            const Placeholder: ComponentType<any> = () => null;
            lazyComponents[feature] = lazy(async () => ({ default: Placeholder }));
        } else {
            lazyComponents[feature] = lazy(loader);
        }
    }
    return lazyComponents[feature] as LazyExoticComponent<ComponentType<any>>;
}

export function preloadFeature(feature: FeatureName): void {
    const loader = featureModules[feature];
    if (loader) {
        void loader();
    }
}

export function preloadRoleFeatures(role: UserRole): void {
    const features = getFeaturesForRole(role);
    features.forEach(preloadFeature);
}
