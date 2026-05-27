/**
 * Artifact Health Panel
 *
 * Displays artifact production status, including artifact type, surface,
 * fingerprint, deployment link, and health check status for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, XCircle, Package, PackageX, Link as LinkIcon } from 'lucide-react';
import { EmptyState } from '@/components/common/EmptyState';

export interface KernelArtifactHealth {
  id: string;
  type: 'docker-image' | 'jar' | 'npm-package' | 'helm-chart' | 'other';
  surface: string;
  path: string;
  fingerprint: string;
  producedBy: string;
  producedAt: string;
  deploymentLink?: string;
  healthCheckStatus?: 'healthy' | 'unhealthy' | 'unknown';
  lastVerified?: string;
}

interface ArtifactHealthPanelProps {
  productUnitId: string;
  artifacts: KernelArtifactHealth[];
}

export const ArtifactHealthPanel: React.FC<ArtifactHealthPanelProps> = ({
  productUnitId,
  artifacts,
}) => {
  const getHealthIcon = (status?: string) => {
    switch (status) {
      case 'healthy':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'unhealthy':
        return <XCircle className="h-4 w-4 text-red-500" />;
      default:
        return <Package className="h-4 w-4 text-gray-500" />;
    }
  };

  const getArtifactTypeBadgeVariant = (type: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (type) {
      case 'docker-image':
        return 'default';
      case 'jar':
        return 'secondary';
      case 'npm-package':
        return 'outline';
      default:
        return 'secondary';
    }
  };

  if (artifacts.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Artifact Health</CardTitle>
        </CardHeader>
        <CardContent>
          <EmptyState
            variant="compact"
            className="rounded-lg border border-dashed border-border bg-muted/40"
            icon={<PackageX className="h-full w-full" aria-hidden="true" />}
            title="No artifacts available"
            description={`No Kernel artifact metadata has been recorded for ${productUnitId} yet.`}
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Artifact Health</h3>
        <Badge variant="outline">{artifacts.length} artifacts</Badge>
      </div>

      <div className="space-y-3">
        {artifacts.map((artifact) => (
          <div key={artifact.id} className="p-4 border rounded-lg space-y-3">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4" />
                <h4 className="font-semibold">{artifact.type}</h4>
                <Badge variant={getArtifactTypeBadgeVariant(artifact.type)}>
                  {artifact.type}
                </Badge>
              </div>
              <div className="flex items-center gap-2">
                {getHealthIcon(artifact.healthCheckStatus)}
                {artifact.healthCheckStatus && (
                  <Badge variant={artifact.healthCheckStatus === 'healthy' ? 'default' : 'destructive'}>
                    {artifact.healthCheckStatus}
                  </Badge>
                )}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground">Surface</p>
                <p className="font-medium">{artifact.surface}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Produced By</p>
                <p className="font-medium">{artifact.producedBy}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Produced At</p>
                <p className="font-medium">
                  {artifact.producedAt ? new Date(artifact.producedAt).toLocaleString() : 'Unknown'}
                </p>
              </div>
              <div>
                <p className="text-muted-foreground">Last Verified</p>
                <p className="font-medium">
                  {artifact.lastVerified ? new Date(artifact.lastVerified).toLocaleString() : 'Never'}
                </p>
              </div>
            </div>

            <div className="text-sm">
              <p className="text-muted-foreground mb-1">Path</p>
              <p className="font-mono text-xs bg-muted p-2 rounded">{artifact.path}</p>
            </div>

            <div className="text-sm">
              <p className="text-muted-foreground mb-1">Fingerprint</p>
              <p className="font-mono text-xs bg-muted p-2 rounded">{artifact.fingerprint}</p>
            </div>

            {artifact.deploymentLink && (
              <div className="flex items-center gap-2 text-sm">
                <LinkIcon className="h-4 w-4" />
                <a
                  href={artifact.deploymentLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary hover:underline"
                >
                  View Deployment
                </a>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default ArtifactHealthPanel;
