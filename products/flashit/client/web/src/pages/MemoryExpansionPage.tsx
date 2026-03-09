/**
 * Memory Expansion Page
 * Request and view AI-powered memory expansions of captured moments
 */

import { useState } from 'react';
import {
  useSpheres,
  useRequestMemoryExpansion,
  useUserExpansions,
  useExpansionById,
} from '../hooks/use-api';
import Layout from '../components/Layout';
import { Brain, Sparkles, Clock, CheckCircle, AlertCircle, Loader2 } from 'lucide-react';

export default function MemoryExpansionPage() {
  const [selectedSphere, setSelectedSphere] = useState('');
  const [selectedExpansionId, setSelectedExpansionId] = useState<string | null>(null);

  const { data: spheres } = useSpheres();
  const { data: expansions, isLoading: expansionsLoading, refetch } = useUserExpansions({ limit: 20 });
  const requestExpansion = useRequestMemoryExpansion();
  const { data: expansionDetail } = useExpansionById(selectedExpansionId || '', !!selectedExpansionId);

  const handleRequest = async () => {
    if (!selectedSphere) return;
    try {
      await requestExpansion.mutateAsync({ sphereId: selectedSphere });
      refetch();
    } catch (err) {
      console.error('Failed to request expansion:', err);
    }
  };

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Memory Expansion</h1>
          <p className="mt-1 text-sm text-gray-500">
            AI-powered summaries, themes, patterns, and connections extracted from your moments
          </p>
        </div>

        {/* Request new expansion */}
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h3 className="text-sm font-medium text-gray-900 mb-3">Request New Expansion</h3>
          <div className="flex items-center gap-3">
            <select
              value={selectedSphere}
              onChange={(e) => setSelectedSphere(e.target.value)}
              className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm"
              aria-label="Select sphere"
            >
              <option value="">Select a sphere...</option>
              {spheres?.map((sphere: { id: string; name: string }) => (
                <option key={sphere.id} value={sphere.id}>{sphere.name}</option>
              ))}
            </select>
            <button
              onClick={handleRequest}
              disabled={!selectedSphere || requestExpansion.isPending}
              className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {requestExpansion.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              <Sparkles className="h-4 w-4" />
              Expand
            </button>
          </div>
          {requestExpansion.isSuccess && (
            <p className="text-sm text-green-600 mt-2 flex items-center gap-1">
              <CheckCircle className="h-4 w-4" /> Expansion requested. It will appear below when ready.
            </p>
          )}
          {requestExpansion.isError && (
            <p className="text-sm text-red-600 mt-2 flex items-center gap-1">
              <AlertCircle className="h-4 w-4" /> Failed to request expansion. Please try again.
            </p>
          )}
        </div>

        {/* Expansions list */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1">
            <h3 className="text-sm font-medium text-gray-900 mb-3">Previous Expansions</h3>
            {expansionsLoading ? (
              <div className="space-y-2">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 animate-pulse">
                    <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
                    <div className="h-3 bg-gray-200 rounded w-1/2"></div>
                  </div>
                ))}
              </div>
            ) : expansions && Array.isArray(expansions) && expansions.length > 0 ? (
              <div className="space-y-2 max-h-[60vh] overflow-y-auto">
                {expansions.map((exp: { id: string; status?: string; createdAt?: string; sphereId?: string }) => (
                  <button
                    key={exp.id}
                    onClick={() => setSelectedExpansionId(exp.id)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedExpansionId === exp.id
                        ? 'border-primary-300 bg-primary-50'
                        : 'border-gray-200 bg-white hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium text-gray-900 truncate">
                        {exp.sphereId ? `Sphere ${exp.sphereId.slice(0, 8)}...` : 'Expansion'}
                      </span>
                      <StatusBadge status={exp.status || 'pending'} />
                    </div>
                    {exp.createdAt && (
                      <span className="flex items-center text-xs text-gray-400 mt-1">
                        <Clock className="h-3 w-3 mr-1" />
                        {new Date(exp.createdAt).toLocaleDateString()}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 bg-white rounded-lg border border-gray-200">
                <Brain className="h-8 w-8 text-gray-300 mx-auto" />
                <p className="mt-2 text-sm text-gray-500">No expansions yet</p>
              </div>
            )}
          </div>

          {/* Expansion detail */}
          <div className="lg:col-span-2">
            {selectedExpansionId && expansionDetail ? (
              <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
                <h3 className="text-lg font-medium text-gray-900">Expansion Result</h3>

                {expansionDetail.summary && (
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-1">Summary</h4>
                    <p className="text-sm text-gray-600 leading-relaxed">{expansionDetail.summary}</p>
                  </div>
                )}

                {expansionDetail.themes && Array.isArray(expansionDetail.themes) && expansionDetail.themes.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-2">Themes</h4>
                    <div className="flex flex-wrap gap-2">
                      {expansionDetail.themes.map((theme: string, idx: number) => (
                        <span key={idx} className="px-3 py-1 bg-purple-50 text-purple-700 rounded-full text-xs font-medium">
                          {theme}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {expansionDetail.patterns && Array.isArray(expansionDetail.patterns) && expansionDetail.patterns.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-2">Patterns</h4>
                    <ul className="space-y-2">
                      {expansionDetail.patterns.map((pattern: string, idx: number) => (
                        <li key={idx} className="flex items-start gap-2 text-sm text-gray-600">
                          <Brain className="h-4 w-4 text-purple-500 mt-0.5 flex-shrink-0" />
                          {pattern}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {expansionDetail.connections && Array.isArray(expansionDetail.connections) && expansionDetail.connections.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-2">Connections</h4>
                    <ul className="space-y-2">
                      {expansionDetail.connections.map((conn: string, idx: number) => (
                        <li key={idx} className="flex items-start gap-2 text-sm text-gray-600">
                          <Sparkles className="h-4 w-4 text-primary-500 mt-0.5 flex-shrink-0" />
                          {conn}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-16 bg-white rounded-lg border border-gray-200">
                <Brain className="h-12 w-12 text-gray-300 mx-auto" />
                <h3 className="mt-4 text-lg font-medium text-gray-900">Select an expansion</h3>
                <p className="mt-1 text-sm text-gray-500">
                  Choose an expansion from the list to view its details
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    completed: 'bg-green-50 text-green-700',
    processing: 'bg-yellow-50 text-yellow-700',
    pending: 'bg-gray-50 text-gray-600',
    failed: 'bg-red-50 text-red-700',
  };

  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${styles[status.toLowerCase()] || styles.pending}`}>
      {status}
    </span>
  );
}
