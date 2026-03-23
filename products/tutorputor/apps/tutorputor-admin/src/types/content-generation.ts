/**
 * Content Generation Types for Admin Dashboard
 */

export interface AutomationRule {
  id: string;
  name: string;
  condition: string;
  action: string;
  enabled: boolean;
}

export interface AutomationMetrics {
  totalRules: number;
  activeRules: number;
  triggersThisMonth: number;
  averageResponseTime: number;
}

export interface DomainData {
  name: string;
  value: number;
  growth: number;
}

export interface PerformanceData {
  timestamp: string;
  value: number;
}

export interface QualityData {
  score: number;
  issues: number;
  suggestions: number;
}

export interface ContentGenerationJob {
  id: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  progress: number;
  createdAt: string;
  completedAt?: string;
}

export interface SystemHealth {
  database: {
    status: string;
    connections: number;
    size: string;
    performance: number;
  };
  cache: {
    status: string;
    memory: string;
    keys: number;
    hitRate: number;
  };
  storage: {
    status: string;
    storage: string;
    objects: number;
    bandwidth: number;
  };
}
