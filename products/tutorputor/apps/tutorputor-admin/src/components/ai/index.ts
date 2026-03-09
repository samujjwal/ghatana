/**
 * AI Components Index
 *
 * Central export point for all AI-enhanced components
 * Reuses existing patterns and provides unified AI interface
 *
 * @doc.type module
 * @doc.purpose AI components aggregation
 * @doc.layer component
 * @doc.pattern Barrel Export
 */

// AI Components
export { default as AIAssistant } from "./AIAssistant";
export { default as SmartDashboard } from "./SmartDashboard";
export { default as LivingKernelDashboard } from "./LivingKernelDashboard";

// Re-export existing components for convenience
export { PluginCard } from "../ai-kernel/PluginCard";
