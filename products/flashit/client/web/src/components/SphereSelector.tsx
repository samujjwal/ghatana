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
      <select
        value={selectedSphereId || ''}
        onChange={(e) => setSelectedSphereId(e.target.value)}
        className="input appearance-none pr-10"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3E%3Cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3E%3C/svg%3E")`,
          backgroundPosition: 'right 0.5rem center',
          backgroundRepeat: 'no-repeat',
          backgroundSize: '1.5em 1.5em',
        }}
      >
        <option value="">Select a Sphere...</option>
        {spheresData?.map((sphere) => (
          <option key={sphere.id} value={sphere.id}>
            {sphere.name} ({sphere.type}) - {sphere.momentCount} moments
          </option>
        ))}
      </select>

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

