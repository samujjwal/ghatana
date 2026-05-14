/**
 * Kernel Health Dashboard Page
 *
 * Displays health views for Kernel ProductUnits, including lifecycle status,
 * gate health, artifact health, deployment status, agent governance, and preview security.
 *
 * This page is the main entry point for YAPPC's kernel visibility/control-plane layer.
 */

import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Loader2, AlertCircle, CheckCircle, Clock, RefreshCw } from 'lucide-react';

// Placeholder imports for panel components - will be created in subsequent tasks
// import { LifecycleTimelinePanel } from './LifecycleTimelinePanel';
// import { GateHealthPanel } from './GateHealthPanel';
// import { ArtifactHealthPanel } from './ArtifactHealthPanel';
// import { DeploymentHealthPanel } from './DeploymentHealthPanel';
// import { AgentGovernanceHealthPanel } from './AgentGovernanceHealthPanel';
// import { PreviewSecurityHealthPanel } from './PreviewSecurityHealthPanel';

interface ProductUnitHealthSummary {
  productUnitId: string;
  overallStatus: 'healthy' | 'degraded' | 'failed' | 'unknown';
  currentPhase: string;
  lastRunTimestamp: string;
}

interface ProductUnitHealthView extends ProductUnitHealthSummary {
  gateFailureCount: number;
  deploymentStatus: string;
  healthSnapshot: Record<string, unknown>;
  lifecycleResult: Record<string, unknown>;
  deployment: Record<string, unknown>;
}

interface ActionRecommendation {
  severity: 'critical' | 'warning' | 'info';
  title: string;
  description: string;
  actionType: string;
}

export const KernelHealthDashboardPage: React.FC = () => {
  const { productUnitId } = useParams<{ productUnitId?: string }>();
  const [selectedProductUnit, setSelectedProductUnit] = useState<string | null>(
    productUnitId || null
  );
  const [healthView, setHealthView] = useState<ProductUnitHealthView | null>(null);
  const [recommendations, setRecommendations] = useState<ActionRecommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load health data when selectedProductUnit changes
  useEffect(() => {
    if (selectedProductUnit) {
      loadHealthData(selectedProductUnit);
    }
  }, [selectedProductUnit]);

  const loadHealthData = async (id: string) => {
    setLoading(true);
    setError(null);

    try {
      // TODO: Replace with actual API call to YAPPC backend
      // const response = await fetch(`/api/kernel-health/${id}`);
      // const data = await response.json();
      
      // Placeholder data for now
      const mockData: ProductUnitHealthView = {
        productUnitId: id,
        overallStatus: 'healthy',
        currentPhase: 'deploy',
        lastRunTimestamp: new Date().toISOString(),
        gateFailureCount: 0,
        deploymentStatus: 'deployed',
        healthSnapshot: {},
        lifecycleResult: {},
        deployment: {},
      };

      setHealthView(mockData);
      
      // Load recommendations
      // const recsResponse = await fetch(`/api/kernel-health/${id}/recommendations`);
      // const recsData = await recsResponse.json();
      setRecommendations([]);
      
    } catch (err) {
      setError('Failed to load health data');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    if (selectedProductUnit) {
      loadHealthData(selectedProductUnit);
    }
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
          {recommendations.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>Recommended Actions</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {recommendations.map((rec, index) => (
                    <Alert key={index} variant={rec.severity === 'critical' ? 'destructive' : 'default'}>
                      <AlertCircle className="h-4 w-4" />
                      <AlertTitle>{rec.title}</AlertTitle>
                      <AlertDescription>{rec.description}</AlertDescription>
                    </Alert>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

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
              <Card>
                <CardHeader>
                  <CardTitle>Lifecycle Timeline</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for LifecycleTimelinePanel */}
                  <p className="text-muted-foreground">
                    Lifecycle timeline panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="gates">
              <Card>
                <CardHeader>
                  <CardTitle>Gate Health</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for GateHealthPanel */}
                  <p className="text-muted-foreground">
                    Gate health panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="artifacts">
              <Card>
                <CardHeader>
                  <CardTitle>Artifact Health</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for ArtifactHealthPanel */}
                  <p className="text-muted-foreground">
                    Artifact health panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="deployment">
              <Card>
                <CardHeader>
                  <CardTitle>Deployment Health</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for DeploymentHealthPanel */}
                  <p className="text-muted-foreground">
                    Deployment health panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="governance">
              <Card>
                <CardHeader>
                  <CardTitle>Agent Governance</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for AgentGovernanceHealthPanel */}
                  <p className="text-muted-foreground">
                    Agent governance health panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="security">
              <Card>
                <CardHeader>
                  <CardTitle>Preview Security</CardTitle>
                </CardHeader>
                <CardContent>
                  {/* Placeholder for PreviewSecurityHealthPanel */}
                  <p className="text-muted-foreground">
                    Preview security health panel will be rendered here.
                  </p>
                </CardContent>
              </Card>
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
