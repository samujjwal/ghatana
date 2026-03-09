/**
 * Shared Types for Content Generation System
 *
 * Centralized type definitions to avoid duplicates across components
 * and ensure consistency across the content generation ecosystem.
 *
 * @doc.type module
 * @doc.purpose Shared types for content generation
 * @doc.layer shared
 * @doc.pattern Types
 */

export interface ContentMetrics {
  totalRequests: number;
  successRate: number;
  averageTime: number;
  confidenceScore: number;
  domainDistribution: DomainData[];
  performanceData: PerformanceData[];
  qualityMetrics: QualityData[];
}

export interface DomainData {
  name: string;
  value: number;
  color: string;
}

export interface PerformanceData {
  name: string;
  content: number;
  success: number;
  time?: number;
}

export interface QualityData {
  name: string;
  value: number;
  color: string;
}

export interface AutomationRule {
  id: string;
  name: string;
  trigger: "scheduled" | "event" | "api" | "ai-suggested";
  schedule?: string;
  condition: string;
  action: string;
  quality: "auto" | "review" | "full";
  delivery: string[];
  status: "active" | "paused" | "draft";
  lastRun?: string;
  nextRun?: string;
  successRate: number;
}

export interface AutomationMetrics {
  totalRules: number;
  activeRules: number;
  contentGenerated: number;
  automationSuccessRate: number;
  averageProcessingTime: number;
  errorRate: number;
  dailyVolume: number;
  weeklyTrend: PerformanceData[];
  triggerDistribution: DomainData[];
  qualityMetrics: QualityData[];
}

export type ViewType =
  | "overview"
  | "performance"
  | "quality"
  | "domains"
  | "rules"
  | "analytics"
  | "monitoring"
  | "existing";

export type TriggerType = "scheduled" | "event" | "api" | "ai-suggested";
export type QualityLevel = "auto" | "review" | "full";
export type RuleStatus = "active" | "paused" | "draft";

export interface SimulationState {
  bob?: {
    x: number;
    y: number;
    vx?: number;
    vy?: number;
  };
  burette?: {
    volume: number;
    color: string;
  };
  flask?: {
    volume: number;
    color: string;
  };
  indicator?: {
    color: string;
  };
  array?: {
    elements: number[];
  };
  pointer?: {
    index: number;
    x: number;
  };
}

export interface DemoConfig {
  id: string;
  name: string;
  description: string;
  entities: Record<string, any>;
  steps: Array<{
    description: string;
    action: string;
    entities: Record<string, any>;
  }>;
}

export type DemoType = "pendulum" | "chemistry" | "algorithm";
