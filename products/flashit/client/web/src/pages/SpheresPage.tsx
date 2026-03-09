/**
 * Spheres Page
 * Manage Spheres (privacy boundaries)
 */

import { useState } from 'react';
import { useSpheres, useCreateSphere, useDeleteSphere } from '../hooks/use-api';
import Layout from '../components/Layout';
import { Plus, Layers, Trash2, Users, Lock, Eye } from 'lucide-react';

export default function SpheresPage() {
  const { data: spheres, refetch } = useSpheres();
  const createSphere = useCreateSphere();
  const deleteSphere = useDeleteSphere();

  const [isCreating, setIsCreating] = useState(false);
  const [newSphereName, setNewSphereName] = useState('');
  const [newSphereDescription, setNewSphereDescription] = useState('');
  const [newSphereType, setNewSphereType] = useState('PERSONAL');
  const [newSphereVisibility, setNewSphereVisibility] = useState('PRIVATE');

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createSphere.mutateAsync({
        name: newSphereName,
        description: newSphereDescription || undefined,
        type: newSphereType,
        visibility: newSphereVisibility,
      });
      setIsCreating(false);
      setNewSphereName('');
      setNewSphereDescription('');
      refetch();
    } catch (error) {
      console.error('Failed to create sphere:', error);
      alert('Failed to create sphere');
    }
  };

  const handleDelete = async (id: string, name: string) => {
    if (confirm(`Are you sure you want to delete "${name}"? This will also delete all moments in this sphere.`)) {
      try {
        await deleteSphere.mutateAsync(id);
        refetch();
      } catch (error) {
        console.error('Failed to delete sphere:', error);
        alert('Failed to delete sphere');
      }
    }
  };

  const getVisibilityIcon = (visibility: string) => {
    switch (visibility) {
      case 'PRIVATE':
        return <Lock className="w-4 h-4" />;
      case 'INVITE_ONLY':
        return <Users className="w-4 h-4" />;
      default:
        return <Eye className="w-4 h-4" />;
    }
  };

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Your Spheres</h1>
            <p className="mt-2 text-gray-600">
              Organize your moments into privacy boundaries
            </p>
          </div>
          <button
            onClick={() => setIsCreating(true)}
            className="btn-primary inline-flex items-center"
          >
            <Plus className="w-4 h-4 mr-2" />
            Create Sphere
          </button>
        </div>

        {/* Create Sphere Form */}
        {isCreating && (
          <div className="card">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Create New Sphere</h2>
            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
                  Name *
                </label>
                <input
                  id="name"
                  type="text"
                  required
                  className="input"
                  placeholder="e.g., Work, Family, Personal Growth"
                  value={newSphereName}
                  onChange={(e) => setNewSphereName(e.target.value)}
                />
              </div>

              <div>
                <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-1">
                  Description
                </label>
                <textarea
                  id="description"
                  rows={3}
                  className="input resize-none"
                  placeholder="Optional description..."
                  value={newSphereDescription}
                  onChange={(e) => setNewSphereDescription(e.target.value)}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="type" className="block text-sm font-medium text-gray-700 mb-1">
                    Type
                  </label>
                  <select
                    id="type"
                    className="input"
                    value={newSphereType}
                    onChange={(e) => setNewSphereType(e.target.value)}
                  >
                    <option value="PERSONAL">Personal</option>
                    <option value="WORK">Work</option>
                    <option value="FAMILY">Family</option>
                    <option value="PROJECT">Project</option>
                    <option value="CUSTOM">Custom</option>
                  </select>
                </div>

                <div>
                  <label htmlFor="visibility" className="block text-sm font-medium text-gray-700 mb-1">
                    Visibility
                  </label>
                  <select
                    id="visibility"
                    className="input"
                    value={newSphereVisibility}
                    onChange={(e) => setNewSphereVisibility(e.target.value)}
                  >
                    <option value="PRIVATE">Private</option>
                    <option value="INVITE_ONLY">Invite Only</option>
                    <option value="LINK_SHARED">Link Shared</option>
                  </select>
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  type="button"
                  onClick={() => setIsCreating(false)}
                  className="btn-secondary flex-1"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary flex-1"
                  disabled={createSphere.isPending}
                >
                  {createSphere.isPending ? 'Creating...' : 'Create Sphere'}
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Spheres Grid */}
        {spheres && spheres.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {spheres.map((sphere) => (
              <div key={sphere.id} className="card hover:shadow-md transition-shadow">
                <div className="flex justify-between items-start mb-3">
                  <div className="flex items-center gap-2">
                    <Layers className="w-5 h-5 text-primary-600" />
                    <h3 className="text-lg font-semibold text-gray-900">{sphere.name}</h3>
                  </div>
                  {sphere.userRole === 'OWNER' && (
                    <button
                      onClick={() => handleDelete(sphere.id, sphere.name)}
                      className="text-gray-400 hover:text-red-600 transition-colors"
                      title="Delete sphere"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>

                {sphere.description && (
                  <p className="text-sm text-gray-600 mb-3">{sphere.description}</p>
                )}

                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Type:</span>
                    <span className="font-medium">{sphere.type}</span>
                  </div>

                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">Visibility:</span>
                    <span className="inline-flex items-center gap-1 font-medium">
                      {getVisibilityIcon(sphere.visibility)}
                      {sphere.visibility}
                    </span>
                  </div>

                  <div className="flex justify-between">
                    <span className="text-gray-600">Role:</span>
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-primary-100 text-primary-700">
                      {sphere.userRole}
                    </span>
                  </div>

                  <div className="flex justify-between pt-2 border-t">
                    <span className="text-gray-600">Moments:</span>
                    <span className="font-semibold text-primary-600">{sphere.momentCount}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="card text-center py-12">
            <div className="text-gray-500">
              <Layers className="w-12 h-12 mx-auto mb-3 opacity-50" />
              <p className="text-lg font-medium">No spheres yet</p>
              <p className="text-sm mt-1">Create your first sphere to organize your moments</p>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}

