import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { CheckCircle2, XCircle, AlertTriangle, Clock, Shield, Database, Activity } from 'lucide-react';

interface ReleaseReadinessEvidence {
  schemaVersion: string;
  productId: string;
  productName: string;
  checkedAt: string;
  releaseReadiness: {
    status: string;
    overallScore: number;
    blockingIssues: string[];
    warnings: string[];
  };
  evidenceCategories: Record<string, EvidenceCategory>;
  gates: Record<string, GateStatus>;
  summary: {
    totalChecks: number;
    passed: number;
    partial: number;
    failed: number;
    blocked: number;
    overallStatus: string;
  };
  nextRequiredWork: string[];
}

interface EvidenceCategory {
  status: string;
  lastChecked: string;
  evidenceRefs: string[];
  data?: Record<string, unknown>;
}

interface GateStatus {
  status: string;
  evidenceRef: string;
}

export function PhrReleaseCockpit() {
  const [evidence, setEvidence] = useState<ReleaseReadinessEvidence | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchReleaseReadiness();
  }, []);

  const fetchReleaseReadiness = async () => {
    try {
      setLoading(true);
      const response = await fetch('/api/phr/release-readiness');
      if (!response.ok) {
        throw new Error('Failed to fetch release readiness');
      }
      const data = await response.json();
      setEvidence(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-center">
          <Activity className="h-8 w-8 animate-spin mx-auto mb-4" />
          <p>Loading release readiness...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <XCircle className="h-4 w-4" />
        <AlertTitle>Error</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  if (!evidence) {
    return null;
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'ready':
      case 'ready-for-production':
      case 'passed':
        return <CheckCircle2 className="h-4 w-4 text-green-500" />;
      case 'blocked':
      case 'failed':
        return <XCircle className="h-4 w-4 text-red-500" />;
      case 'partial':
      case 'pending':
        return <AlertTriangle className="h-4 w-4 text-yellow-500" />;
      default:
        return <Clock className="h-4 w-4 text-gray-500" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ready':
      case 'ready-for-production':
      case 'passed':
        return 'bg-green-500';
      case 'blocked':
      case 'failed':
        return 'bg-red-500';
      case 'partial':
      case 'pending':
        return 'bg-yellow-500';
      default:
        return 'bg-gray-500';
    }
  };

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">PHR Release Cockpit</h1>
          <p className="text-muted-foreground">Personal Health Records Release Readiness Dashboard</p>
        </div>
        <Button onClick={fetchReleaseReadiness} variant="outline">
          <Activity className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {/* Overall Status */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            {getStatusIcon(evidence.releaseReadiness.status)}
            Overall Status: {evidence.releaseReadiness.status}
          </CardTitle>
          <CardDescription>
            Last checked: {new Date(evidence.checkedAt).toLocaleString()}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold">{evidence.releaseReadiness.overallScore.toFixed(1)}</div>
              <div className="text-sm text-muted-foreground">Overall Score</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-500">{evidence.summary.passed}</div>
              <div className="text-sm text-muted-foreground">Passed</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-yellow-500">{evidence.summary.partial}</div>
              <div className="text-sm text-muted-foreground">Partial</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-red-500">{evidence.summary.blocked}</div>
              <div className="text-sm text-muted-foreground">Blocked</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Blocking Issues */}
      {evidence.releaseReadiness.blockingIssues.length > 0 && (
        <Alert variant="destructive">
          <XCircle className="h-4 w-4" />
          <AlertTitle>Blocking Issues ({evidence.releaseReadiness.blockingIssues.length})</AlertTitle>
          <AlertDescription>
            <ul className="list-disc list-inside mt-2">
              {evidence.releaseReadiness.blockingIssues.map((issue, index) => (
                <li key={index}>{issue}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* Warnings */}
      {evidence.releaseReadiness.warnings.length > 0 && (
        <Alert>
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Warnings ({evidence.releaseReadiness.warnings.length})</AlertTitle>
          <AlertDescription>
            <ul className="list-disc list-inside mt-2">
              {evidence.releaseReadiness.warnings.map((warning, index) => (
                <li key={index}>{warning}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* Evidence Categories */}
      <Tabs defaultValue="categories">
        <TabsList>
          <TabsTrigger value="categories">Evidence Categories</TabsTrigger>
          <TabsTrigger value="gates">Gate Status</TabsTrigger>
          <TabsTrigger value="next-steps">Next Steps</TabsTrigger>
        </TabsList>

        <TabsContent value="categories">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {Object.entries(evidence.evidenceCategories).map(([category, data]) => (
              <Card key={category}>
                <CardHeader>
                  <CardTitle className="flex items-center justify-between text-lg">
                    <span className="capitalize">{category}</span>
                    {getStatusIcon(data.status)}
                  </CardTitle>
                  <CardDescription>
                    Last checked: {data.lastChecked ? new Date(data.lastChecked).toLocaleString() : 'Never'}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <Badge className={getStatusColor(data.status)}>{data.status}</Badge>
                  {data.evidenceRefs.length > 0 && (
                    <div className="mt-4">
                      <p className="text-sm font-medium mb-2">Evidence References:</p>
                      <ul className="text-sm text-muted-foreground list-disc list-inside">
                        {data.evidenceRefs.map((ref, index) => (
                          <li key={index} className="truncate">{ref}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="gates">
          <div className="space-y-4">
            {Object.entries(evidence.gates).map(([gate, status]) => (
              <Card key={gate}>
                <CardContent className="pt-6">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {getStatusIcon(status.status)}
                      <div>
                        <p className="font-medium">{gate}</p>
                        <p className="text-sm text-muted-foreground">{status.evidenceRef}</p>
                      </div>
                    </div>
                    <Badge className={getStatusColor(status.status)}>{status.status}</Badge>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="next-steps">
          <Card>
            <CardHeader>
              <CardTitle>Next Required Work</CardTitle>
              <CardDescription>
                Tasks that must be completed before production release
              </CardDescription>
            </CardHeader>
            <CardContent>
              {evidence.nextRequiredWork.length > 0 ? (
                <ul className="space-y-2">
                  {evidence.nextRequiredWork.map((task, index) => (
                    <li key={index} className="flex items-start gap-2">
                      <Clock className="h-4 w-4 mt-1 text-muted-foreground" />
                      <span>{task}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="flex items-center gap-2 text-green-600">
                  <CheckCircle2 className="h-4 w-4" />
                  <p>All required work completed</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
