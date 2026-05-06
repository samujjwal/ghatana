/**
 * Sphere Selector Component
 * Dropdown to select active Sphere and create new ones
 */

import { useAtom } from 'jotai';
import { selectedSphereIdAtom, isCreateSphereModalOpenAtom } from '../store/atoms';
import { useSpheres } from '../hooks/use-api';
import { ChevronDown, Plus } from 'lucide-react';

export default function SphereSelector() {
  const [selectedSphereId, setSelectedSphereId] = useAtom(selectedSphereIdAtom);
  const [, setIsCreateModalOpen] = useAtom(isCreateSphereModalOpenAtom);
  const { data: spheresData, isLoading } = useSpheres();

  const selectedSphere = spheresData?.find(s => s.id === selectedSphereId);

  if (isLoading) {
    return (
      <div className="animate-pulse bg-gray-200 h-10 rounded-lg"></div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="relative">
        <select
          value={selectedSphereId || ''}
          onChange={(e) => setSelectedSphereId(e.target.value)}
          className="input appearance-none pr-10"
        >
          <option value="">Select a Sphere...</option>
          {spheresData?.map((sphere) => (
            <option key={sphere.id} value={sphere.id}>
              {sphere.name} ({sphere.type}) - {sphere.momentCount} moments
            </option>
          ))}
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500"
        />
      </div>

      {selectedSphere && (
        <div className="text-sm text-gray-600">
          <p>
            <span className="font-medium">Type:</span> {selectedSphere.type}
          </p>
          <p>
            <span className="font-medium">Visibility:</span> {selectedSphere.visibility}
          </p>
          {selectedSphere.description && (
            <p className="mt-1">{selectedSphere.description}</p>
          )}
        </div>
      )}

      <button
        type="button"
        onClick={() => setIsCreateModalOpen(true)}
        className="inline-flex items-center text-sm text-primary-600 hover:text-primary-700 font-medium"
      >
        <Plus className="w-4 h-4 mr-1" />
        Create new Sphere
      </button>
    </div>
  );
}
