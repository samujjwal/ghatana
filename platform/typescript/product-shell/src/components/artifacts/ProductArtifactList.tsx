/**
 * ProductArtifactList
 * 
 * Displays a list of artifacts for a product version.
 * 
 * @doc.type component
 * @doc.purpose Display product artifacts
 * @doc.layer platform
 */

import type { ProductArtifact } from '../../contracts/product-artifact';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductArtifactListProps {
  readonly artifacts: readonly ProductArtifact[];
  readonly onViewArtifact?: (artifactId: string) => void;
}

export function ProductArtifactList({ artifacts, onViewArtifact }: ProductArtifactListProps) {
  const getArtifactTypeColor = (type: string) => {
    switch (type) {
      case 'jar':
        return 'blue';
      case 'static-web-bundle':
        return 'green';
      case 'docker-image':
        return 'purple';
      case 'mobile-ios-app':
        return 'orange';
      case 'mobile-android-app':
        return 'green';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <h3 className="text-lg font-semibold mb-4">Artifacts</h3>

      {artifacts.length === 0 ? (
        <div className="text-sm text-gray-500 py-4">No artifacts available</div>
      ) : (
        <div className="space-y-2">
          {artifacts.map((artifact) => (
            <div
              key={artifact.id}
              className="flex items-center justify-between p-3 bg-gray-50 rounded hover:bg-gray-100"
            >
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">{artifact.surface}</span>
                  <Badge color={getArtifactTypeColor(artifact.type)} size="sm">
                    {artifact.type}
                  </Badge>
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  {artifact.path} • {artifact.checksumAlgorithm}: {artifact.checksum.slice(0, 12)}...
                </div>
                {artifact.sizeBytes && (
                  <div className="text-xs text-gray-500">
                    {(artifact.sizeBytes / 1024 / 1024).toFixed(2)} MB
                  </div>
                )}
              </div>
              {onViewArtifact && (
                <button
                  onClick={() => onViewArtifact(artifact.id)}
                  className="text-sm text-blue-600 hover:text-blue-700"
                >
                  View
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
