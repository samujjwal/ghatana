/**
 * Reflection Page
 * AI-powered insights, patterns, and connections across moments
 */

import { useState } from 'react';
import { useSpheres } from '../hooks/use-api';
import Layout from '../components/Layout';
import { Brain, Sparkles, GitBranch, Calendar, Loader2, AlertCircle } from 'lucide-react';
import { apiClient } from '../lib/api-client';

type ReflectionTab = 'insights' | 'patterns' | 'connections' | 'weekly' | 'monthly';

interface InsightResult {
  insights?: string[];
  summary?: string;
  themes?: string[];
  error?: string;
}

export default function ReflectionPage() {
  const [activeTab, setActiveTab] = useState<ReflectionTab>('insights');
  const [selectedSphere, setSelectedSphere] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<InsightResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: spheres } = useSpheres();

  const tabs = [
    { id: 'insights' as ReflectionTab, label: 'Insights', icon: Sparkles },
    { id: 'patterns' as ReflectionTab, label: 'Patterns', icon: Brain },
    { id: 'connections' as ReflectionTab, label: 'Connections', icon: GitBranch },
    { id: 'weekly' as ReflectionTab, label: 'Weekly', icon: Calendar },
    { id: 'monthly' as ReflectionTab, label: 'Monthly', icon: Calendar },
  ];

  const fetchReflection = async (endpoint: string) => {
    if (!selectedSphere) {
      setError('Please select a sphere');
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const data = await apiClient.request(`/api/reflection/${endpoint}`, {
        method: endpoint === 'weekly' || endpoint === 'monthly' ? 'GET' : 'POST',
        ...(endpoint !== 'weekly' && endpoint !== 'monthly' && {
          body: JSON.stringify({ sphereId: selectedSphere, timeWindowDays: endpoint === 'insights' ? 30 : 90 }),
        }),
        ...(endpoint === 'weekly' || endpoint === 'monthly') && {
          params: { sphereId: selectedSphere },
        },
      });
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate reflection');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = () => {
    const endpointMap: Record<ReflectionTab, string> = {
      insights: 'insights',
      patterns: 'patterns',
      connections: 'connections',
      weekly: `weekly?sphereId=${selectedSphere}`,
      monthly: `monthly?sphereId=${selectedSphere}`,
    };
    fetchReflection(endpointMap[activeTab]);
  };

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">AI Reflection</h1>
          <p className="mt-1 text-sm text-gray-500">
            Discover insights, patterns, and connections across your captured moments
          </p>
        </div>

        {/* Sphere selector */}
        <div className="flex items-center gap-4">
          <select
            value={selectedSphere}
            onChange={(e) => setSelectedSphere(e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm"
            aria-label="Select sphere"
          >
            <option value="">Select a sphere...</option>
            {spheres?.map((sphere: { id: string; name: string }) => (
              <option key={sphere.id} value={sphere.id}>{sphere.name}</option>
            ))}
          </select>
          <button
            onClick={handleGenerate}
            disabled={!selectedSphere || loading}
            className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
            Generate
          </button>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="flex -mb-px space-x-6" aria-label="Reflection tabs">
            {tabs.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => { setActiveTab(id); setResult(null); setError(null); }}
                className={`flex items-center gap-2 px-1 py-3 border-b-2 text-sm font-medium ${
                  activeTab === id
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                aria-current={activeTab === id ? 'page' : undefined}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </nav>
        </div>

        {/* Error */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-3">
            <AlertCircle className="h-5 w-5 text-red-500 flex-shrink-0" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {/* Loading */}
        {loading && (
          <div className="text-center py-16">
            <Brain className="h-12 w-12 text-primary-400 mx-auto animate-pulse" />
            <p className="mt-4 text-sm text-gray-500">Analyzing your moments...</p>
            <p className="text-xs text-gray-400 mt-1">This may take 10-30 seconds</p>
          </div>
        )}

        {/* Results */}
        {result && !loading && (
          <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
            {result.summary && (
              <div>
                <h3 className="text-sm font-medium text-gray-900 mb-2">Summary</h3>
                <p className="text-sm text-gray-700 leading-relaxed">{result.summary}</p>
              </div>
            )}

            {result.themes && result.themes.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-gray-900 mb-2">Themes</h3>
                <div className="flex flex-wrap gap-2">
                  {result.themes.map((theme: string, idx: number) => (
                    <span
                      key={idx}
                      className="px-3 py-1 bg-primary-50 text-primary-700 rounded-full text-xs font-medium"
                    >
                      {theme}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {result.insights && result.insights.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-gray-900 mb-2">Insights</h3>
                <ul className="space-y-2">
                  {result.insights.map((insight: string, idx: number) => (
                    <li key={idx} className="flex items-start gap-2">
                      <Sparkles className="h-4 w-4 text-primary-500 mt-0.5 flex-shrink-0" />
                      <p className="text-sm text-gray-700">{insight}</p>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* Empty state */}
        {!result && !loading && !error && (
          <div className="text-center py-16">
            <Brain className="h-12 w-12 text-gray-300 mx-auto" />
            <h3 className="mt-4 text-lg font-medium text-gray-900">
              {activeTab === 'insights' && 'Generate AI Insights'}
              {activeTab === 'patterns' && 'Detect Patterns'}
              {activeTab === 'connections' && 'Find Connections'}
              {activeTab === 'weekly' && 'Weekly Reflection'}
              {activeTab === 'monthly' && 'Monthly Reflection'}
            </h3>
            <p className="mt-1 text-sm text-gray-500">
              Select a sphere and click Generate to analyze your moments
            </p>
          </div>
        )}
      </div>
    </Layout>
  );
}
