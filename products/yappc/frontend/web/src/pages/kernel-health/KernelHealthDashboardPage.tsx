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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Loader2, AlertCircle, CheckCircle, Clock, RefreshCw } from 'lucide-react';

import { LifecycleTimelinePanel } from './LifecycleTimelinePanel';
import { GateHealthPanel } from './GateHealthPanel';
import { ArtifactHealthPanel } from './ArtifactHealthPanel';
import { DeploymentHealthPanel } from './DeploymentHealthPanel';
import { AgentGovernanceHealthPanel } from './AgentGovernanceHealthPanel';
import { PreviewSecurityHealthPanel } from './PreviewSecurityHealthPanel';
import { RecommendedActionsPanel } from './RecommendedActionsPanel';
import {
  useKernelProductUnitHealth,
  useKernelLifecycleTimeline,
  useKernelRecommendedActions,
} from '../../hooks/useKernelHealth';
import type { KernelProductUnitHealthView } from '../../clients/kernelHealthClient';

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
                runs={timeline?.runs ?? []}
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
                deployment={healthView.deployment as Parameters<typeof DeploymentHealthPanel>[0]['deployment']}
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
                previewSecurity={healthView.healthSnapshot as Parameters<typeof PreviewSecurityHealthPanel>[0]['previewSecurity']}
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
