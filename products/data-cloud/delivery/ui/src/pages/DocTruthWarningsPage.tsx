import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { FileText, AlertTriangle, CheckCircle2, RefreshCw, Download, Eye } from 'lucide-react';

interface DocTruthWarning {
  id: string;
  docId: string;
  docName: string;
  docType: string;
  warningType: string;
  severity: 'critical' | 'warning' | 'info';
  message: string;
  suggestion: string;
  detectedAt: string;
  status: 'open' | 'acknowledged' | 'resolved';
}

interface DocTruthIngestionStatus {
  docId: string;
  docName: string;
  docType: string;
  ingestionStatus: 'pending' | 'ingested' | 'failed';
  lastIngestedAt: string;
  warningsCount: number;
}

export function DocTruthWarningsPage() {
  const [warnings, setWarnings] = useState<DocTruthWarning[]>([]);
  const [ingestionStatus, setIngestionStatus] = useState<DocTruthIngestionStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDocTruthData();
  }, []);

  const fetchDocTruthData = async () => {
    try {
      setLoading(true);
      const [warningsRes, statusRes] = await Promise.all([
        fetch('/api/doc-truth/warnings'),
        fetch('/api/doc-truth/ingestion-status')
      ]);

      if (!warningsRes.ok || !statusRes.ok) {
        throw new Error('Failed to fetch doc-truth data');
      }

      const warningsData = await warningsRes.json();
      const statusData = await statusRes.json();

      setWarnings(warningsData);
      setIngestionStatus(statusData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  const acknowledgeWarning = async (warningId: string) => {
    try {
      await fetch(`/api/doc-truth/warnings/${warningId}/acknowledge`, {
        method: 'POST'
      });
      fetchDocTruthData();
    } catch (err) {
      console.error('Failed to acknowledge warning:', err);
    }
  };

  const resolveWarning = async (warningId: string) => {
    try {
      await fetch(`/api/doc-truth/warnings/${warningId}/resolve`, {
        method: 'POST'
      });
      fetchDocTruthData();
    } catch (err) {
      console.error('Failed to resolve warning:', err);
    }
  };

  const triggerIngestion = async (docId: string) => {
    try {
      await fetch(`/api/doc-truth/ingest/${docId}`, {
        method: 'POST'
      });
      fetchDocTruthData();
    } catch (err) {
      console.error('Failed to trigger ingestion:', err);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-center">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto mb-4" />
          <p>Loading doc-truth data...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTriangle className="h-4 w-4" />
        <AlertTitle>Error</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  const criticalWarnings = warnings.filter(w => w.severity === 'critical' && w.status === 'open');
  const warningWarnings = warnings.filter(w => w.severity === 'warning' && w.status === 'open');
  const infoWarnings = warnings.filter(w => w.severity === 'info' && w.status === 'open');

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Doc-Truth Warnings</h1>
          <p className="text-muted-foreground">Documentation truth ingestion and warnings dashboard</p>
        </div>
        <Button onClick={fetchDocTruthData} variant="outline">
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {/* Critical Warnings Alert */}
      {criticalWarnings.length > 0 && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Critical Warnings ({criticalWarnings.length})</AlertTitle>
          <AlertDescription>
            {criticalWarnings.length} critical warnings require immediate attention.
          </AlertDescription>
        </Alert>
      )}

      {/* Warning Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Critical</CardTitle>
            <CardDescription>Open critical warnings</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-red-500">{criticalWarnings.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Warnings</CardTitle>
            <CardDescription>Open warnings</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-yellow-500">{warningWarnings.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Info</CardTitle>
            <CardDescription>Open info warnings</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-blue-500">{infoWarnings.length}</div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="warnings">
        <TabsList>
          <TabsTrigger value="warnings">Warnings</TabsTrigger>
          <TabsTrigger value="ingestion">Ingestion Status</TabsTrigger>
        </TabsList>

        <TabsContent value="warnings">
          <div className="space-y-4">
            {warnings.length === 0 ? (
              <Card>
                <CardContent className="pt-6">
                  <div className="flex items-center justify-center text-muted-foreground">
                    <CheckCircle2 className="h-8 w-8 mr-2 text-green-500" />
                    <p>No warnings detected</p>
                  </div>
                </CardContent>
              </Card>
            ) : (
              warnings.map((warning) => (
                <Card key={warning.id}>
                  <CardHeader>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <FileText className="h-5 w-5" />
                        <div>
                          <CardTitle className="text-lg">{warning.docName}</CardTitle>
                          <CardDescription>{warning.docType} • {warning.warningType}</CardDescription>
                        </div>
                      </div>
                      <Badge className={
                        warning.severity === 'critical' ? 'bg-red-500' :
                        warning.severity === 'warning' ? 'bg-yellow-500' : 'bg-blue-500'
                      }>
                        {warning.severity}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-4">
                      <div>
                        <p className="font-medium mb-1">Message:</p>
                        <p className="text-sm text-muted-foreground">{warning.message}</p>
                      </div>
                      <div>
                        <p className="font-medium mb-1">Suggestion:</p>
                        <p className="text-sm text-muted-foreground">{warning.suggestion}</p>
                      </div>
                      <div className="flex items-center justify-between text-sm text-muted-foreground">
                        <span>Detected: {new Date(warning.detectedAt).toLocaleString()}</span>
                        <Badge variant={warning.status === 'open' ? 'default' : 'secondary'}>
                          {warning.status}
                        </Badge>
                      </div>
                      {warning.status === 'open' && (
                        <div className="flex gap-2">
                          <Button size="sm" onClick={() => acknowledgeWarning(warning.id)}>
                            Acknowledge
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => resolveWarning(warning.id)}>
                            Resolve
                          </Button>
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            )}
          </div>
        </TabsContent>

        <TabsContent value="ingestion">
          <div className="space-y-4">
            {ingestionStatus.map((status) => (
              <Card key={status.docId}>
                <CardContent className="pt-6">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <FileText className="h-5 w-5" />
                      <div>
                        <p className="font-medium">{status.docName}</p>
                        <p className="text-sm text-muted-foreground">{status.docType}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <Badge className={
                        status.ingestionStatus === 'ingested' ? 'bg-green-500' :
                        status.ingestionStatus === 'failed' ? 'bg-red-500' : 'bg-gray-500'
                      }>
                        {status.ingestionStatus}
                      </Badge>
                      {status.warningsCount > 0 && (
                        <Badge variant="destructive">
                          {status.warningsCount} warnings
                        </Badge>
                      )}
                      <Button size="sm" variant="outline" onClick={() => triggerIngestion(status.docId)}>
                        <RefreshCw className="h-4 w-4 mr-1" />
                        Re-ingest
                      </Button>
                    </div>
                  </div>
                  <div className="mt-2 text-sm text-muted-foreground">
                    Last ingested: {status.lastIngestedAt ? new Date(status.lastIngestedAt).toLocaleString() : 'Never'}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
