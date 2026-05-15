import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

/**
 * Artifact types supported by the visualization.
 */
export type ArtifactType =
  | 'docker-image'
  | 'kubernetes-manifest'
  | 'terraform-config'
  | 'build-artifact'
  | 'test-report'
  | 'documentation'
  | 'security-scan'
  | 'performance-report'
  | 'deployment-package'
  | 'configuration';

/**
 * Artifact status types.
 */
export type ArtifactStatus = 'created' | 'validated' | 'deployed' | 'archived' | 'failed';

/**
 * Artifact data structure.
 */
export interface Artifact {
  id: string;
  name: string;
  version: string;
  type: ArtifactType;
  status: ArtifactStatus;
  size: number;
  checksum: string;
  surfaceId: string;
  createdAt: string;
  updatedAt?: string;
  description?: string;
  tags?: string[];
  metadata?: Record<string, string>;
  downloadUrl?: string;
  evidenceRefs?: string[];
}

/**
 * Studio artifact visualization panel.
 *
 * @doc.type component
 * @doc.purpose Artifact visualization for Studio mode
 * @doc.layer ui
 */
export interface ArtifactVisualizationPanelProps {
  artifacts?: Artifact[];
  onRefresh?: () => void;
  onDownload?: (artifact: Artifact) => void;
  onView?: (artifact: Artifact) => void;
  isLoading?: boolean;
  className?: string;
}

export function ArtifactVisualizationPanel({
  artifacts = [],
  onRefresh,
  onDownload,
  onView,
  isLoading = false,
  className,
}: ArtifactVisualizationPanelProps) {
  const { t } = useTranslation('studio');
  const [selectedArtifact, setSelectedArtifact] = useState<Artifact | null>(null);
  const [filter, setFilter] = useState<{
    type?: ArtifactType;
    status?: ArtifactStatus;
    search: string;
  }>({
    search: '',
  });
  const [sortBy, setSortBy] = useState<'name' | 'createdAt' | 'size' | 'type'>('createdAt');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  // Filter and sort artifacts
  const filteredArtifacts = artifacts
    .filter(artifact => {
      if (filter.type && artifact.type !== filter.type) return false;
      if (filter.status && artifact.status !== filter.status) return false;
      if (filter.search && !artifact.name.toLowerCase().includes(filter.search.toLowerCase())) return false;
      return true;
    })
    .sort((a, b) => {
      let comparison = 0;
      switch (sortBy) {
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'createdAt':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
        case 'size':
          comparison = a.size - b.size;
          break;
        case 'type':
          comparison = a.type.localeCompare(b.type);
          break;
      }
      return sortOrder === 'asc' ? comparison : -comparison;
    });

  const getArtifactIcon = (type: ArtifactType): string => {
    switch (type) {
      case 'docker-image': return '🐳';
      case 'kubernetes-manifest': return '☸️';
      case 'terraform-config': return '🏗️';
      case 'build-artifact': return '📦';
      case 'test-report': return '📊';
      case 'documentation': return '📚';
      case 'security-scan': return '🔒';
      case 'performance-report': return '⚡';
      case 'deployment-package': return '🚀';
      case 'configuration': return '⚙️';
      default: return '📄';
    }
  };

  const getStatusColor = (status: ArtifactStatus): string => {
    switch (status) {
      case 'created': return 'text-info-600 dark:text-info-400';
      case 'validated': return 'text-success-600 dark:text-success-400';
      case 'deployed': return 'text-success-600 dark:text-success-400';
      case 'archived': return 'text-fg-muted dark:text-fg-muted';
      case 'failed': return 'text-danger-600 dark:text-danger-400';
      default: return 'text-fg-muted dark:text-fg-muted';
    }
  };

  const getStatusBgColor = (status: ArtifactStatus): string => {
    switch (status) {
      case 'created': return 'bg-info-100 dark:bg-info-900/20';
      case 'validated': return 'bg-success-100 dark:bg-success-900/20';
      case 'deployed': return 'bg-success-100 dark:bg-success-900/20';
      case 'archived': return 'bg-surface-100 dark:bg-surface-800';
      case 'failed': return 'bg-danger-100 dark:bg-danger-900/20';
      default: return 'bg-surface-100 dark:bg-surface-800';
    }
  };

  const formatFileSize = (bytes: number): string => {
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size.toFixed(1)} ${units[unitIndex]}`;
  };

  const formatTimestamp = (timestamp: string): string => {
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  const artifactTypes: ArtifactType[] = [
    'docker-image',
    'kubernetes-manifest',
    'terraform-config',
    'build-artifact',
    'test-report',
    'documentation',
    'security-scan',
    'performance-report',
    'deployment-package',
    'configuration',
  ];

  const artifactStatuses: ArtifactStatus[] = ['created', 'validated', 'deployed', 'archived', 'failed'];

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-border dark:border-border">
        <h3 className="text-sm font-medium text-fg dark:text-fg">
          {t('artifacts.title')} ({filteredArtifacts.length})
        </h3>
        <div className="flex items-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            onClick={onRefresh}
            disabled={isLoading}
            className="text-xs text-fg-muted hover:text-fg"
            aria-label={t('artifacts.refresh')}
          >
            {isLoading ? '⏳' : '🔄'}
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="p-3 border-b border-border dark:border-border space-y-2">
        <div className="flex gap-2">
          <input
            type="text"
            placeholder={t('artifacts.searchPlaceholder')}
            value={filter.search}
            onChange={(e) => setFilter(prev => ({ ...prev, search: e.target.value }))}
            className="flex-1 px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          />
          <select
            value={filter.type || ''}
            onChange={(e) => setFilter(prev => ({
              ...prev,
              type: e.target.value ? e.target.value as ArtifactType : undefined
            }))}
            className="px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          >
            <option value="">{t('artifacts.allTypes')}</option>
            {artifactTypes.map(type => (
              <option key={type} value={type}>
                {getArtifactIcon(type)} {t(`artifacts.types.${type}`)}
              </option>
            ))}
          </select>
          <select
            value={filter.status || ''}
            onChange={(e) => setFilter(prev => ({
              ...prev,
              status: e.target.value ? e.target.value as ArtifactStatus : undefined
            }))}
            className="px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          >
            <option value="">{t('artifacts.allStatuses')}</option>
            {artifactStatuses.map(status => (
              <option key={status} value={status}>
                {t(`artifacts.statuses.${status}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-fg-muted dark:text-fg-muted">{t('artifacts.sortBy')}:</span>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
            className="px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          >
            <option value="createdAt">{t('artifacts.sortOptions.createdAt')}</option>
            <option value="name">{t('artifacts.sortOptions.name')}</option>
            <option value="size">{t('artifacts.sortOptions.size')}</option>
            <option value="type">{t('artifacts.sortOptions.type')}</option>
          </select>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setSortOrder(prev => prev === 'asc' ? 'desc' : 'asc')}
            className="text-xs text-fg-muted hover:text-fg"
          >
            {sortOrder === 'asc' ? '↑' : '↓'}
          </Button>
        </div>
      </div>

      {/* Artifacts List */}
      <div className="flex-1 overflow-auto p-3">
        {filteredArtifacts.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-fg-muted dark:text-fg-muted">
            <div className="text-4xl mb-2">📦</div>
            <p className="text-sm text-center">
              {isLoading ? t('artifacts.loading') : t('artifacts.noArtifacts')}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredArtifacts.map((artifact) => (
              <div
                key={artifact.id}
                className={cn(
                  'p-3 rounded-lg border cursor-pointer transition-all',
                  'border-border dark:border-border',
                  'hover:border-info-300 dark:hover:border-info-600',
                  selectedArtifact?.id === artifact.id && 'border-info-500 dark:border-info-400',
                  getStatusBgColor(artifact.status)
                )}
                onClick={() => setSelectedArtifact(artifact)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    setSelectedArtifact(artifact);
                  }
                }}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-lg">{getArtifactIcon(artifact.type)}</span>
                      <h4 className="text-sm font-medium text-fg dark:text-fg truncate">
                        {artifact.name}
                      </h4>
                      <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(artifact.status))}>
                        {t(`artifacts.statuses.${artifact.status}`)}
                      </span>
                    </div>
                    <p className="text-xs text-fg-muted dark:text-fg-muted mt-1">
                      {t('artifacts.version')}: {artifact.version} • {formatFileSize(artifact.size)}
                    </p>
                    {artifact.description && (
                      <p className="text-xs text-fg-muted dark:text-fg-muted mt-1 truncate">
                        {artifact.description}
                      </p>
                    )}
                    <div className="flex items-center gap-4 mt-2 text-xs text-fg-muted dark:text-fg-muted">
                      <span>{formatTimestamp(artifact.createdAt)}</span>
                      <span className="px-1 py-0.5 bg-surface-200 dark:bg-surface-700 rounded">
                        {t(`artifacts.types.${artifact.type}`)}
                      </span>
                      {artifact.tags && artifact.tags.length > 0 && (
                        <div className="flex gap-1">
                          {artifact.tags.slice(0, 2).map(tag => (
                            <span key={tag} className="px-1 py-0.5 bg-info-100 dark:bg-info-900/20 text-info-600 dark:text-info-400 rounded text-xs">
                              {tag}
                            </span>
                          ))}
                          {artifact.tags.length > 2 && (
                            <span className="text-fg-muted dark:text-fg-muted">+{artifact.tags.length - 2}</span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-1 ml-2">
                    {onView && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={(e) => {
                          e.stopPropagation();
                          onView(artifact);
                        }}
                        className="text-xs text-fg-muted hover:text-fg"
                        aria-label={t('artifacts.view')}
                      >
                        👁️
                      </Button>
                    )}
                    {onDownload && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={(e) => {
                          e.stopPropagation();
                          onDownload(artifact);
                        }}
                        className="text-xs text-fg-muted hover:text-fg"
                        aria-label={t('artifacts.download')}
                      >
                        ⬇️
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Selected Artifact Details */}
      {selectedArtifact && (
        <div className="border-t border-border dark:border-border p-3">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-sm font-medium text-fg dark:text-fg">
              {t('artifacts.details')}
            </h4>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSelectedArtifact(null)}
              className="text-xs text-fg-muted hover:text-fg"
              aria-label={t('artifacts.closeDetails')}
            >
              ✕
            </Button>
          </div>
          <div className="space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.id')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedArtifact.id}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.name')}:</span>
              <span className="text-fg dark:text-fg">{selectedArtifact.name}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.version')}:</span>
              <span className="text-fg dark:text-fg">{selectedArtifact.version}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.type')}:</span>
              <span className="text-fg dark:text-fg">
                {getArtifactIcon(selectedArtifact.type)} {t(`artifacts.types.${selectedArtifact.type}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.status')}:</span>
              <span className={cn('font-medium', getStatusColor(selectedArtifact.status))}>
                {t(`artifacts.statuses.${selectedArtifact.status}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.size')}:</span>
              <span className="text-fg dark:text-fg">{formatFileSize(selectedArtifact.size)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.checksum')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedArtifact.checksum}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.surfaceId')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedArtifact.surfaceId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.createdAt')}:</span>
              <span className="text-fg dark:text-fg">{formatTimestamp(selectedArtifact.createdAt)}</span>
            </div>
            {selectedArtifact.updatedAt && (
              <div className="flex justify-between">
                <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.updatedAt')}:</span>
                <span className="text-fg dark:text-fg">{formatTimestamp(selectedArtifact.updatedAt)}</span>
              </div>
            )}
            {selectedArtifact.description && (
              <div className="pt-2 border-t border-border dark:border-border">
                <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.description')}:</span>
                <p className="mt-1 text-fg dark:text-fg break-words">{selectedArtifact.description}</p>
              </div>
            )}
            {selectedArtifact.tags && selectedArtifact.tags.length > 0 && (
              <div className="pt-2 border-t border-border dark:border-border">
                <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.tags')}:</span>
                <div className="flex flex-wrap gap-1 mt-1">
                  {selectedArtifact.tags.map(tag => (
                    <span key={tag} className="px-2 py-1 bg-info-100 dark:bg-info-900/20 text-info-600 dark:text-info-400 rounded text-xs">
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {selectedArtifact.metadata && Object.keys(selectedArtifact.metadata).length > 0 && (
              <div className="pt-2 border-t border-border dark:border-border">
                <span className="text-fg-muted dark:text-fg-muted">{t('artifacts.metadata')}:</span>
                <div className="space-y-1 mt-1">
                  {Object.entries(selectedArtifact.metadata).map(([key, value]) => (
                    <div key={key} className="flex justify-between">
                      <span className="font-mono text-fg-muted dark:text-fg-muted">{key}:</span>
                      <span className="font-mono text-fg dark:text-fg">{value}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Hook for fetching artifact data.
 *
 * @doc.type hook
 * @doc.purpose Artifact data fetching
 */
export function useArtifacts(productUnitId?: string, runId?: string) {
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchArtifacts = React.useCallback(async () => {
    if (!productUnitId) return;

    setIsLoading(true);
    setError(null);

    try {
      // This would integrate with the kernel artifact provider
      // For now, we'll simulate with mock data
      const mockArtifacts: Artifact[] = [
        {
          id: 'artifact-1',
          name: 'yappc-web-app',
          version: '1.2.3',
          type: 'docker-image',
          status: 'deployed',
          size: 256 * 1024 * 1024, // 256MB
          checksum: 'sha256:abc123def456...',
          surfaceId: 'build-surface-1',
          createdAt: new Date().toISOString(),
          description: 'Main web application Docker image',
          tags: ['web', 'production', 'frontend'],
          metadata: {
            'registry': 'ghatana.io',
            'architecture': 'amd64',
            'os': 'linux',
          },
        },
        {
          id: 'artifact-2',
          name: 'k8s-deployment',
          version: 'v1.2.3',
          type: 'kubernetes-manifest',
          status: 'validated',
          size: 12 * 1024, // 12KB
          checksum: 'sha256:xyz789uvw012...',
          surfaceId: 'deploy-surface-1',
          createdAt: new Date(Date.now() - 300000).toISOString(),
          description: 'Kubernetes deployment manifests',
          tags: ['k8s', 'deployment'],
        },
        {
          id: 'artifact-3',
          name: 'test-results',
          version: '1.2.3',
          type: 'test-report',
          status: 'created',
          size: 256 * 1024, // 256KB
          checksum: 'sha256:tests345678...',
          surfaceId: 'test-surface-1',
          createdAt: new Date(Date.now() - 600000).toISOString(),
          description: 'Automated test execution results',
          tags: ['tests', 'quality'],
        },
      ];

      setArtifacts(mockArtifacts);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch artifacts');
    } finally {
      setIsLoading(false);
    }
  }, [productUnitId, runId]);

  useEffect(() => {
    fetchArtifacts();
  }, [fetchArtifacts]);

  return {
    artifacts,
    isLoading,
    error,
    refetch: fetchArtifacts,
  };
}
