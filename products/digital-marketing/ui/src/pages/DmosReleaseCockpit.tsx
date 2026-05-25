import React, { useState, useEffect } from 'react';
import { CheckCircle2, XCircle, AlertTriangle, Clock, Shield, Database, Activity, BarChart3, Target, Layers, Calendar, RefreshCw } from 'lucide-react';

type ComponentProps = React.PropsWithChildren<{ className?: string }>;

function Card({ children, className = '' }: ComponentProps): React.ReactElement {
  return <section className={`rounded-lg border border-gray-200 bg-white shadow-sm ${className}`}>{children}</section>;
}

function CardHeader({ children, className = '' }: ComponentProps): React.ReactElement {
  return <div className={`border-b border-gray-100 p-4 ${className}`}>{children}</div>;
}

function CardTitle({ children, className = '' }: ComponentProps): React.ReactElement {
  return <h2 className={`text-lg font-semibold ${className}`}>{children}</h2>;
}

function CardDescription({ children, className = '' }: ComponentProps): React.ReactElement {
  return <p className={`mt-1 text-sm text-gray-500 ${className}`}>{children}</p>;
}

function CardContent({ children, className = '' }: ComponentProps): React.ReactElement {
  return <div className={`p-4 ${className}`}>{children}</div>;
}

function Badge({ children, className = '', variant }: ComponentProps & { variant?: 'outline' | 'destructive' }): React.ReactElement {
  const variantClass = variant === 'outline'
    ? 'border border-gray-300 bg-white text-gray-700'
    : variant === 'destructive'
      ? 'bg-red-600 text-white'
      : 'text-white';
  return <span className={`inline-flex items-center rounded px-2 py-1 text-xs font-medium ${variantClass} ${className}`}>{children}</span>;
}

function Button({ children, className = '', variant: _variant, ...props }: ComponentProps & React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: string }): React.ReactElement {
  return <button className={`inline-flex items-center rounded border border-gray-300 px-3 py-2 text-sm font-medium ${className}`} {...props}>{children}</button>;
}

function Alert({ children, variant }: ComponentProps & { variant?: 'destructive' }): React.ReactElement {
  const tone = variant === 'destructive' ? 'border-red-200 bg-red-50 text-red-900' : 'border-gray-200 bg-white text-gray-900';
  return <div className={`rounded-lg border p-4 ${tone}`}>{children}</div>;
}

function AlertTitle({ children, className = '' }: ComponentProps): React.ReactElement {
  return <div className={`font-semibold ${className}`}>{children}</div>;
}

function AlertDescription({ children, className = '' }: ComponentProps): React.ReactElement {
  return <div className={`mt-1 text-sm ${className}`}>{children}</div>;
}

function Tabs({ children }: ComponentProps & { value?: string; defaultValue?: string; onValueChange?: (value: string) => void }): React.ReactElement {
  return <>{children}</>;
}

function TabsList({ children, className = '' }: ComponentProps): React.ReactElement {
  return <div className={`flex gap-2 ${className}`}>{children}</div>;
}

function TabsTrigger({ children, value, className = '' }: ComponentProps & { value: string }): React.ReactElement {
  return <button type="button" data-tab-value={value} className={`rounded border border-gray-300 px-3 py-2 text-sm ${className}`}>{children}</button>;
}

function TabsContent({ children, value, className = '' }: ComponentProps & { value: string }): React.ReactElement {
  return <div data-tab-content={value} className={className}>{children}</div>;
}

interface ReleaseReadinessEvidence {
  schemaVersion: string;
  productId: string;
  productName: string;
  checkedAt: string;
  sourceCommitSha?: string;
  targetCommitSha?: string;
  targetEnvironment?: string;
  validationStatus?: string;
  expiresAt?: string;
  evidenceRun?: {
    commit?: string;
    generatedAt?: string;
  };
  evidenceFreshness?: {
    status?: string;
    current?: boolean;
    warnings?: string[];
  };
  dataCloudProviderReadiness?: {
    status?: string;
    evidenceRef?: string;
  };
  dataCloudRuntimeProfile?: {
    status?: string;
    evidenceRef?: string;
  };
  contradictionState?: string;
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
  foundationUsage?: FoundationUsage;
  connectorReadiness?: ConnectorReadiness;
  rollbackStatus?: RollbackStatus;
}

interface EvidenceCategory {
  status: string;
  lastChecked: string;
  evidenceRefs: string[];
  data?: Record<string, unknown>;
  freshness?: {
    ageHours: number;
    isStale: boolean;
  };
}

interface GateStatus {
  status: string;
  evidenceRef: string;
  environment?: string;
}

interface FoundationUsage {
  kernel: FoundationSlice;
  dataCloud: FoundationSlice;
  plugins: FoundationSlice[];
  overallStatus: string;
}

interface FoundationSlice {
  name: string;
  status: string;
  evidenceRef?: string;
  lastChecked?: string;
}

interface ConnectorReadiness {
  googleAds: ConnectorStatus;
  connectors: Record<string, ConnectorStatus>;
  overallStatus: string;
}

interface ConnectorStatus {
  name: string;
  status: string;
  lastChecked?: string;
  oauthValid?: boolean;
  tokenRefreshWorking?: boolean;
  idempotencyValid?: boolean;
}

interface RollbackStatus {
  staging: RollbackEnvironmentStatus;
  production: RollbackEnvironmentStatus;
  overallStatus: string;
}

interface RollbackEnvironmentStatus {
  hasEvidence: boolean;
  lastRollbackTest?: string;
  campaignStatePreserved?: boolean;
  externalIdsPreserved?: boolean;
  connectorCommandsPreserved?: boolean;
}

export function DmosReleaseCockpit() {
  const [evidence, setEvidence] = useState<ReleaseReadinessEvidence | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEnvironment, setSelectedEnvironment] = useState<string>('production');

  useEffect(() => {
    fetchReleaseReadiness();
  }, [selectedEnvironment]);

  const fetchReleaseReadiness = async () => {
    try {
      setLoading(true);
      const response = await fetch(`/api/digital-marketing/release-readiness?environment=${selectedEnvironment}`);
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
  const isFreshnessBlocked = evidence.evidenceFreshness?.current === false || evidence.validationStatus === 'failed';
  const hasContradiction = evidence.contradictionState === 'EVIDENCE_CONTRADICTION';

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Digital Marketing Release Cockpit</h1>
          <p className="text-muted-foreground">Digital Marketing Operations System Release Readiness Dashboard</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedEnvironment}
            onChange={(e) => setSelectedEnvironment(e.target.value)}
            className="px-3 py-2 border rounded-md"
          >
            <option value="local">Local</option>
            <option value="staging">Staging</option>
            <option value="production">Production</option>
          </select>
          <Button onClick={fetchReleaseReadiness} variant="outline">
            <Activity className="h-4 w-4 mr-2" />
            Refresh
          </Button>
        </div>
      </div>

      {(hasContradiction || isFreshnessBlocked) && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>{hasContradiction ? 'Evidence contradiction' : 'Evidence freshness blocked'}</AlertTitle>
          <AlertDescription>
            {hasContradiction
              ? 'Product readiness and Data Cloud provider/runtime evidence disagree. Review the evidence records below.'
              : 'Release readiness is blocked until current commit evidence is regenerated and validated.'}
          </AlertDescription>
        </Alert>
      )}

      {/* Overall Status */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            {getStatusIcon(evidence.releaseReadiness.status)}
            Overall Status: {evidence.releaseReadiness.status}
            {evidence.targetEnvironment && (
              <Badge variant="outline" className="ml-2">
                {evidence.targetEnvironment}
              </Badge>
            )}
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

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            Evidence Freshness
          </CardTitle>
          <CardDescription>Commit, environment, and Data Cloud evidence alignment</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <div className="text-sm text-muted-foreground">Target commit</div>
              <div className="font-mono text-xs break-all">{evidence.targetCommitSha || evidence.evidenceRun?.commit || 'unknown'}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Validation</div>
              <Badge className={getStatusColor(evidence.validationStatus || evidence.evidenceFreshness?.status || 'pending')}>
                {evidence.validationStatus || evidence.evidenceFreshness?.status || 'pending'}
              </Badge>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Expires</div>
              <div className="text-sm">{evidence.expiresAt ? new Date(evidence.expiresAt).toLocaleString() : 'unknown'}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Data Cloud provider</div>
              <Badge className={getStatusColor(evidence.dataCloudProviderReadiness?.status || 'pending')}>
                {evidence.dataCloudProviderReadiness?.status || 'pending'}
              </Badge>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Runtime profile</div>
              <Badge className={getStatusColor(evidence.dataCloudRuntimeProfile?.status || 'pending')}>
                {evidence.dataCloudRuntimeProfile?.status || 'pending'}
              </Badge>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Contradiction</div>
              <Badge className={hasContradiction ? 'bg-red-500' : 'bg-green-500'}>
                {evidence.contradictionState || 'NONE'}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Foundation Usage - DMOS-008 */}
      {evidence.foundationUsage && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Layers className="h-5 w-5" />
              Foundation Usage
            </CardTitle>
            <CardDescription>
              Platform foundation readiness for Digital Marketing
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between p-3 border rounded-lg">
                <div className="flex items-center gap-3">
                  <Shield className="h-5 w-5 text-blue-500" />
                  <div>
                    <p className="font-medium">Kernel</p>
                    <p className="text-sm text-muted-foreground">{evidence.foundationUsage.kernel.name}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {evidence.foundationUsage.kernel.lastChecked && (
                    <span className="text-xs text-muted-foreground">
                      {new Date(evidence.foundationUsage.kernel.lastChecked).toLocaleString()}
                    </span>
                  )}
                  <Badge className={getStatusColor(evidence.foundationUsage.kernel.status)}>
                    {evidence.foundationUsage.kernel.status}
                  </Badge>
                </div>
              </div>
              <div className="flex items-center justify-between p-3 border rounded-lg">
                <div className="flex items-center gap-3">
                  <Database className="h-5 w-5 text-green-500" />
                  <div>
                    <p className="font-medium">Data Cloud</p>
                    <p className="text-sm text-muted-foreground">{evidence.foundationUsage.dataCloud.name}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {evidence.foundationUsage.dataCloud.lastChecked && (
                    <span className="text-xs text-muted-foreground">
                      {new Date(evidence.foundationUsage.dataCloud.lastChecked).toLocaleString()}
                    </span>
                  )}
                  <Badge className={getStatusColor(evidence.foundationUsage.dataCloud.status)}>
                    {evidence.foundationUsage.dataCloud.status}
                  </Badge>
                </div>
              </div>
              {evidence.foundationUsage.plugins.map((plugin, index) => (
                <div key={index} className="flex items-center justify-between p-3 border rounded-lg">
                  <div className="flex items-center gap-3">
                    <Layers className="h-5 w-5 text-purple-500" />
                    <div>
                      <p className="font-medium">{plugin.name}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {plugin.lastChecked && (
                      <span className="text-xs text-muted-foreground">
                        {new Date(plugin.lastChecked).toLocaleString()}
                      </span>
                    )}
                    <Badge className={getStatusColor(plugin.status)}>
                      {plugin.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Connector Readiness - DMOS-008 */}
      {evidence.connectorReadiness && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Target className="h-5 w-5" />
              Connector Readiness
            </CardTitle>
            <CardDescription>
              External connector status and validation
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between p-3 border rounded-lg">
                <div className="flex items-center gap-3">
                  <Target className="h-5 w-5 text-orange-500" />
                  <div>
                    <p className="font-medium">Google Ads</p>
                    <p className="text-sm text-muted-foreground">{evidence.connectorReadiness.googleAds.name}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {evidence.connectorReadiness.googleAds.lastChecked && (
                    <span className="text-xs text-muted-foreground">
                      {new Date(evidence.connectorReadiness.googleAds.lastChecked).toLocaleString()}
                    </span>
                  )}
                  <Badge className={getStatusColor(evidence.connectorReadiness.googleAds.status)}>
                    {evidence.connectorReadiness.googleAds.status}
                  </Badge>
                </div>
              </div>
              {evidence.connectorReadiness.googleAds.oauthValid !== undefined && (
                <div className="text-sm text-muted-foreground">
                  OAuth Valid: {evidence.connectorReadiness.googleAds.oauthValid ? '✓' : '✗'} |
                  Token Refresh: {evidence.connectorReadiness.googleAds.tokenRefreshWorking ? '✓' : '✗'} |
                  Idempotency: {evidence.connectorReadiness.googleAds.idempotencyValid ? '✓' : '✗'}
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Rollback Status - DMOS-008 */}
      {evidence.rollbackStatus && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <RefreshCw className="h-5 w-5" />
              Rollback Status
            </CardTitle>
            <CardDescription>
              Rollback evidence and state preservation validation
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-3 border rounded-lg">
                <p className="font-medium mb-2">Staging</p>
                <div className="space-y-1 text-sm">
                  <div className="flex items-center gap-2">
                    {evidence.rollbackStatus.staging.hasEvidence ? (
                      <CheckCircle2 className="h-4 w-4 text-green-500" />
                    ) : (
                      <XCircle className="h-4 w-4 text-red-500" />
                    )}
                    <span>Has Evidence</span>
                  </div>
                  {evidence.rollbackStatus.staging.lastRollbackTest && (
                    <div className="text-muted-foreground">
                      Last test: {new Date(evidence.rollbackStatus.staging.lastRollbackTest).toLocaleString()}
                    </div>
                  )}
                  {evidence.rollbackStatus.staging.campaignStatePreserved !== undefined && (
                    <div className="flex items-center gap-2">
                      {evidence.rollbackStatus.staging.campaignStatePreserved ? (
                        <CheckCircle2 className="h-4 w-4 text-green-500" />
                      ) : (
                        <XCircle className="h-4 w-4 text-red-500" />
                      )}
                      <span>Campaign State Preserved</span>
                    </div>
                  )}
                </div>
              </div>
              <div className="p-3 border rounded-lg">
                <p className="font-medium mb-2">Production</p>
                <div className="space-y-1 text-sm">
                  <div className="flex items-center gap-2">
                    {evidence.rollbackStatus.production.hasEvidence ? (
                      <CheckCircle2 className="h-4 w-4 text-green-500" />
                    ) : (
                      <XCircle className="h-4 w-4 text-red-500" />
                    )}
                    <span>Has Evidence</span>
                  </div>
                  {evidence.rollbackStatus.production.lastRollbackTest && (
                    <div className="text-muted-foreground">
                      Last test: {new Date(evidence.rollbackStatus.production.lastRollbackTest).toLocaleString()}
                    </div>
                  )}
                  {evidence.rollbackStatus.production.campaignStatePreserved !== undefined && (
                    <div className="flex items-center gap-2">
                      {evidence.rollbackStatus.production.campaignStatePreserved ? (
                        <CheckCircle2 className="h-4 w-4 text-green-500" />
                      ) : (
                        <XCircle className="h-4 w-4 text-red-500" />
                      )}
                      <span>Campaign State Preserved</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

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
                    <span className="capitalize flex items-center gap-2">
                      {getCategoryIcon(category)}
                      {category}
                    </span>
                    <div className="flex items-center gap-2">
                      {data.freshness?.isStale && (
                        <Badge variant="destructive" className="text-xs">
                          <AlertTriangle className="h-3 w-3 mr-1" />
                          Stale
                        </Badge>
                      )}
                      {getStatusIcon(data.status)}
                    </div>
                  </CardTitle>
                  <CardDescription>
                    <div className="flex items-center gap-2">
                      <Calendar className="h-3 w-3" />
                      Last checked: {data.lastChecked ? new Date(data.lastChecked).toLocaleString() : 'Never'}
                    </div>
                    {data.freshness && (
                      <div className="text-xs text-muted-foreground mt-1">
                        Age: {data.freshness.ageHours.toFixed(1)} hours
                      </div>
                    )}
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
                        {status.environment && (
                          <Badge variant="outline" className="text-xs mt-1">
                            {status.environment}
                          </Badge>
                        )}
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

function getCategoryIcon(category: string) {
  switch (category) {
    case 'registry':
      return <Database className="h-4 w-4" />;
    case 'manifest':
      return <Shield className="h-4 w-4" />;
    case 'lifecycle':
      return <Activity className="h-4 w-4" />;
    case 'connector':
      return <Target className="h-4 w-4" />;
    case 'persistence':
      return <Database className="h-4 w-4" />;
    case 'bridge':
      return <BarChart3 className="h-4 w-4" />;
    default:
      return <Shield className="h-4 w-4" />;
  }
}
