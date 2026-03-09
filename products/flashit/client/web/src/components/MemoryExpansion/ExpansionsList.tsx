/**
 * ExpansionsList - Display user's memory expansions
 * Phase 1 Week 12 (Day 58): Wire UI entry points
 */

import { useState } from 'react';
import { useUserExpansions } from '../../hooks/use-api';
import {
  Sparkles,
  FileText,
  TrendingUp,
  Activity,
  Link2,
  Plus,
  Loader2,
  Calendar,
  AlertCircle,
} from 'lucide-react';
import { format } from 'date-fns';
import MemoryExpansionDialog from './MemoryExpansionDialog';
import ExpansionResultCard from './ExpansionResultCard';

export default function ExpansionsList() {
  const [showDialog, setShowDialog] = useState(false);
  const [selectedExpansionId, setSelectedExpansionId] = useState<string | null>(null);
  const { data, isLoading, error } = useUserExpansions({ limit: 20 });

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'summarize':
        return <FileText className="h-5 w-5" />;
      case 'extract_themes':
        return <TrendingUp className="h-5 w-5" />;
      case 'identify_patterns':
        return <Activity className="h-5 w-5" />;
      case 'find_connections':
        return <Link2 className="h-5 w-5" />;
      default:
        return <Sparkles className="h-5 w-5" />;
    }
  };

  const getTypeLabel = (type: string) => {
    switch (type) {
      case 'summarize':
        return 'Summary';
      case 'extract_themes':
        return 'Themes';
      case 'identify_patterns':
        return 'Patterns';
      case 'find_connections':
        return 'Connections';
      default:
        return 'Expansion';
    }
  };

  const getTypeColor = (type: string) => {
    switch (type) {
      case 'summarize':
        return 'bg-blue-100 text-blue-700';
      case 'extract_themes':
        return 'bg-green-100 text-green-700';
      case 'identify_patterns':
        return 'bg-purple-100 text-purple-700';
      case 'find_connections':
        return 'bg-indigo-100 text-indigo-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  if (selectedExpansionId) {
    return (
      <div className="space-y-6">
        <button
          onClick={() => setSelectedExpansionId(null)}
          className="text-sm text-gray-600 hover:text-gray-900"
        >
          ← Back to all expansions
        </button>
        <ExpansionResultCard
          expansionId={selectedExpansionId}
          onClose={() => setSelectedExpansionId(null)}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Memory Expansions</h2>
          <p className="mt-1 text-gray-600">
            AI-powered deeper analysis of your moment collections
          </p>
        </div>
        <button
          onClick={() => setShowDialog(true)}
          className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-4 py-2 text-white hover:bg-purple-700"
        >
          <Plus className="h-4 w-4" />
          New Expansion
        </button>
      </div>

      {/* Info Banner */}
      <div className="rounded-lg border-2 border-purple-200 bg-purple-50 p-4">
        <div className="flex items-start gap-3">
          <Sparkles className="h-5 w-5 flex-shrink-0 text-purple-600" />
          <div>
            <p className="font-medium text-purple-900">What are Memory Expansions?</p>
            <p className="mt-1 text-sm text-purple-700">
              Memory expansions use AI to analyze collections of your moments and provide deeper
              insights like summaries, recurring themes, behavioral patterns, and meaningful
              connections. Each expansion includes explainability showing how conclusions were
              reached.
            </p>
          </div>
        </div>
      </div>

      {/* Expansions List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-purple-500" />
        </div>
      ) : error ? (
        <div className="card">
          <div className="flex items-center gap-3 text-red-600">
            <AlertCircle className="h-5 w-5" />
            <span>Failed to load expansions</span>
          </div>
        </div>
      ) : data?.expansions.length === 0 ? (
        <div className="card text-center py-12">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-purple-100">
            <Sparkles className="h-8 w-8 text-purple-600" />
          </div>
          <h3 className="mb-2 text-lg font-semibold text-gray-900">No expansions yet</h3>
          <p className="mb-4 text-gray-600">
            Create your first memory expansion to gain deeper insights into your moments
          </p>
          <button
            onClick={() => setShowDialog(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-6 py-3 text-white hover:bg-purple-700"
          >
            <Plus className="h-5 w-5" />
            Create First Expansion
          </button>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data?.expansions.map((expansion) => (
            <button
              key={expansion.id}
              onClick={() => setSelectedExpansionId(expansion.id)}
              className="card text-left transition-shadow hover:shadow-md"
            >
              <div className="mb-3 flex items-start justify-between">
                <div
                  className={`rounded-lg p-2 ${getTypeColor(expansion.type)}`}
                >
                  {getTypeIcon(expansion.type)}
                </div>
                <span className="text-xs text-gray-500">
                  {format(new Date(expansion.createdAt), 'MMM d')}
                </span>
              </div>
              <h3 className="mb-1 font-semibold text-gray-900">
                {getTypeLabel(expansion.type)}
              </h3>
              <div className="flex items-center gap-1 text-xs text-gray-500">
                <Calendar className="h-3 w-3" />
                {format(new Date(expansion.createdAt), 'MMM d, yyyy h:mm a')}
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Dialog */}
      <MemoryExpansionDialog
        isOpen={showDialog}
        onClose={() => setShowDialog(false)}
        onExpansionRequested={(jobId) => {
          console.log('Expansion requested:', jobId);
          setShowDialog(false);
        }}
      />
    </div>
  );
}
