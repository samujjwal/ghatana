/**
 * Kernel Health Dashboard Page
 *
 * Displays health views for Kernel ProductUnits, including lifecycle status,
 * gate health, artifact health, deployment status, agent governance, and preview security.
 *
 * This page is the main entry point for YAPPC's kernel visibility/control-plane layer.
 */

import React from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/Button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Loader2, AlertCircle, CheckCircle, Clock, RefreshCw } from 'lucide-react';

import { LifecycleTimelinePanel, type LifecycleRunSummary } from './LifecycleTimelinePanel';
import { GateHealthPanel } from './GateHealthPanel';
import { ArtifactHealthPanel } from './ArtifactHealthPanel';
import { DeploymentHealthPanel, type Deployment, type HealthCheck } from './DeploymentHealthPanel';
import { AgentGovernanceHealthPanel } from './AgentGovernanceHealthPanel';
import {
  PreviewSecurityHealthPanel,
  type PreviewSecurity,
  type TokenScope,
} from './PreviewSecurityHealthPanel';
import { RecommendedActionsPanel } from './RecommendedActionsPanel';
import {
  useKernelProductUnitHealth,
  useKernelLifecycleTimeline,
  useKernelRecommendedActions,
} from '../../hooks/useKernelHealth';

type JsonRecord = Record<string, unknown>;

const toStringOr = (value: unknown, fallback: string): string =>
  typeof value === 'string' ? value : fallback;

const toBooleanOr = (value: unknown, fallback: boolean): boolean =>
  typeof value === 'boolean' ? value : fallback;

const toLifecycleStatus = (value: unknown): LifecycleRunSummary['status'] => {
  switch (value) {
    case 'succeeded':
    case 'failed':
    case 'running':
    case 'blocked':
    case 'pending':
      return value;
    default:
      return 'pending';
  }
};

const toDeploymentStatus = (value: unknown): Deployment['status'] => {
  switch (value) {
    case 'deployed':
    case 'failed':
    case 'pending':
    case 'rolling_back':
    case 'not_deployed':
      return value;
    default:
      return 'not_deployed';
  }
};

const toDeploymentEnvironment = (value: unknown): Deployment['environment'] => {
  switch (value) {
    case 'dev':
    case 'staging':
    case 'production':
    case 'preview':
      return value;
    default:
      return 'dev';
  }
};

const toPreviewTrustLevel = (value: unknown): PreviewSecurity['trustLevel'] => {
  switch (value) {
    case 'trusted':
    case 'semi-trusted':
    case 'untrusted':
      return value;
    default:
      return 'untrusted';
  }
};

const toAcknowledgementStatus = (
  value: unknown
): PreviewSecurity['acknowledgementStatus'] => {
  switch (value) {
    case 'acknowledged':
    case 'pending':
    case 'expired':
      return value;
    default:
      return 'pending';
  }
};

const toHealthCheck = (input: unknown): HealthCheck => {
  const record = typeof input === 'object' && input !== null ? (input as JsonRecord) : {};
  return {
    name: toStringOr(record.name, 'unknown-check'),
    status:
      record.status === 'pass' || record.status === 'fail' || record.status === 'pending'
        ? record.status
        : 'pending',
    lastChecked: toStringOr(record.lastChecked, ''),
  };
};

const normalizeRuns = (runs: unknown): LifecycleRunSummary[] => {
  if (!Array.isArray(runs)) {
    return [];
  }

  return runs.map((run, index) => {
    const record = typeof run === 'object' && run !== null ? (run as JsonRecord) : {};
    const durationValue = record.duration;
    return {
      phase: toStringOr(record.phase, `phase-${index + 1}`),
      status: toLifecycleStatus(record.status),
      timestamp: toStringOr(record.timestamp, ''),
      duration: typeof durationValue === 'number' ? durationValue : undefined,
    };
  });
};

const normalizeDeployment = (deployment: unknown, productUnitId: string): Deployment => {
  const record =
    typeof deployment === 'object' && deployment !== null ? (deployment as JsonRecord) : {};

  return {
    id: toStringOr(record.id, `${productUnitId}-deployment`),
    status: toDeploymentStatus(record.status),
    target: toStringOr(record.target, 'unknown-target'),
    environment: toDeploymentEnvironment(record.environment),
    deployedAt: typeof record.deployedAt === 'string' ? record.deployedAt : undefined,
    artifactId: toStringOr(record.artifactId, 'unknown-artifact'),
    healthChecks: Array.isArray(record.healthChecks)
      ? record.healthChecks.map(toHealthCheck)
      : [],
    rollbackAvailable: toBooleanOr(record.rollbackAvailable, false),
    rollbackStatus:
      record.rollbackStatus === 'available' ||
      record.rollbackStatus === 'not_available' ||
      record.rollbackStatus === 'in_progress'
        ? record.rollbackStatus
        : undefined,
  };
};

const toTokenScope = (input: unknown, index: number): TokenScope => {
  const record = typeof input === 'object' && input !== null ? (input as JsonRecord) : {};
  return {
    id: toStringOr(record.id, `scope-${index + 1}`),
    name: toStringOr(record.name, `scope-${index + 1}`),
    required: toBooleanOr(record.required, false),
    granted: toBooleanOr(record.granted, false),
  };
};

const normalizePreviewSecurity = (snapshot: unknown, productUnitId: string): PreviewSecurity => {
  const record = typeof snapshot === 'object' && snapshot !== null ? (snapshot as JsonRecord) : {};
  return {
    previewTokenId: toStringOr(record.previewTokenId, `${productUnitId}-preview-token`),
    tokenScope: Array.isArray(record.tokenScope)
      ? record.tokenScope.map(toTokenScope)
      : [],
    trustLevel: toPreviewTrustLevel(record.trustLevel),
    acknowledgementRequired: toBooleanOr(record.acknowledgementRequired, false),
    acknowledgementStatus: toAcknowledgementStatus(record.acknowledgementStatus),
    scopeMismatches: Array.isArray(record.scopeMismatches)
      ? record.scopeMismatches.filter((item): item is string => typeof item === 'string')
      : [],
    expiresAt: typeof record.expiresAt === 'string' ? record.expiresAt : undefined,
    lastRefreshed: typeof record.lastRefreshed === 'string' ? record.lastRefreshed : undefined,
  };
};

export const KernelHealthDashboardPage: React.FC = () => {
  const { productUnitId: routeProductUnitId } = useParams<{ productUnitId?: string }>();
  const selectedProductUnit: string | null = routeProductUnitId ?? null;

  const activeProductUnitId = selectedProductUnit ?? undefined;

  const {
    data: healthView,
    isLoading: healthLoading,
    isError: healthError,
    refetch: refetchHealth,
  } = useKernelProductUnitHealth(activeProductUnitId);

  const {
    data: timeline,
    isLoading: timelineLoading,
    refetch: refetchTimeline,
  } = useKernelLifecycleTimeline(activeProductUnitId);

  const {
    data: recommendations = [],
    isLoading: recsLoading,
    refetch: refetchRecs,
  } = useKernelRecommendedActions(activeProductUnitId);

  const loading = healthLoading || timelineLoading || recsLoading;
  const error = healthError ? 'Failed to load Kernel health data' : null;
  const timelineRuns = normalizeRuns(timeline?.runs);
  const deployment = healthView
    ? normalizeDeployment(healthView.deployment, healthView.productUnitId)
    : null;
  const previewSecurity = healthView
    ? normalizePreviewSecurity(healthView.healthSnapshot, healthView.productUnitId)
    : null;

  const handleRefresh = () => {
    void refetchHealth();
    void refetchTimeline();
    void refetchRecs();
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'healthy':
        return <CheckCircle className="h-5 w-5 text-green-500" />;
      case 'degraded':
        return <Clock className="h-5 w-5 text-yellow-500" />;
      case 'failed':
        return <AlertCircle className="h-5 w-5 text-red-500" />;
      default:
        return <Clock className="h-5 w-5 text-gray-500" />;
    }
  };

  const getStatusBadgeVariant = (status: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'healthy':
        return 'default';
      case 'degraded':
        return 'secondary';
      case 'failed':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  if (loading && !healthView) {
    return (
      <div className="flex items-center justify-center h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Kernel Health Dashboard</h1>
          <p className="text-muted-foreground">
            Visibility into Kernel ProductUnit lifecycle execution and health
          </p>
        </div>
        <Button onClick={handleRefresh} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {healthView && (
        <>
          {/* ProductUnit Summary Card */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2">
                  {getStatusIcon(healthView.overallStatus)}
                  {healthView.productUnitId}
                </CardTitle>
                <Badge variant={getStatusBadgeVariant(healthView.overallStatus)}>
                  {healthView.overallStatus}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div>
                  <p className="text-sm text-muted-foreground">Current Phase</p>
                  <p className="font-semibold">{healthView.currentPhase}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Last Run</p>
                  <p className="font-semibold">
                    {new Date(healthView.lastRunTimestamp).toLocaleString()}
                  </p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Gate Failures</p>
                  <p className="font-semibold">{healthView.gateFailureCount}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Deployment</p>
                  <p className="font-semibold">{healthView.deploymentStatus}</p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Action Recommendations */}
          <RecommendedActionsPanel
            productUnitId={healthView.productUnitId}
            recommendations={recommendations}
          />

          {/* Health Detail Tabs */}
          <Tabs defaultValue="timeline" className="w-full">
            <TabsList className="grid w-full grid-cols-6">
              <TabsTrigger value="timeline">Lifecycle Timeline</TabsTrigger>
              <TabsTrigger value="gates">Gate Health</TabsTrigger>
              <TabsTrigger value="artifacts">Artifacts</TabsTrigger>
              <TabsTrigger value="deployment">Deployment</TabsTrigger>
              <TabsTrigger value="governance">Agent Governance</TabsTrigger>
              <TabsTrigger value="security">Preview Security</TabsTrigger>
            </TabsList>

            <TabsContent value="timeline">
              <LifecycleTimelinePanel
                productUnitId={healthView.productUnitId}
                runs={timelineRuns}
              />
            </TabsContent>

            <TabsContent value="gates">
              <GateHealthPanel
                productUnitId={healthView.productUnitId}
                gates={[]}
              />
            </TabsContent>

            <TabsContent value="artifacts">
              <ArtifactHealthPanel
                productUnitId={healthView.productUnitId}
                artifacts={[]}
              />
            </TabsContent>

            <TabsContent value="deployment">
              <DeploymentHealthPanel
                productUnitId={healthView.productUnitId}
                deployment={deployment ?? normalizeDeployment({}, healthView.productUnitId)}
              />
            </TabsContent>

            <TabsContent value="governance">
              <AgentGovernanceHealthPanel
                productUnitId={healthView.productUnitId}
                agents={[]}
              />
            </TabsContent>

            <TabsContent value="security">
              <PreviewSecurityHealthPanel
                productUnitId={healthView.productUnitId}
                previewSecurity={
                  previewSecurity ?? normalizePreviewSecurity({}, healthView.productUnitId)
                }
              />
            </TabsContent>
          </Tabs>
        </>
      )}

      {!selectedProductUnit && (
        <Card>
          <CardContent className="flex items-center justify-center h-64">
            <p className="text-muted-foreground">
              Select a ProductUnit to view health details
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default KernelHealthDashboardPage;
