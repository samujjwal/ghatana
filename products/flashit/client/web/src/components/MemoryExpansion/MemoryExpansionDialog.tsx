/**
 * MemoryExpansionDialog - Request memory expansion analysis
 * Phase 1 Week 12 (Day 56-58): AI Memory Expansion UI
 */

import { useState } from 'react';
import { useRequestMemoryExpansion } from '../../hooks/use-api';
import {
  Sparkles,
  X,
  Calendar,
  Tag,
  Loader2,
  AlertCircle,
  Check,
} from 'lucide-react';
import { format, subDays } from 'date-fns';

export interface MemoryExpansionDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onExpansionRequested?: (jobId: string) => void;
  selectedMomentIds?: string[];
  sphereId?: string;
}

type ExpansionType = 'summarize' | 'extract_themes' | 'identify_patterns' | 'find_connections';

const EXPANSION_TYPES: Array<{
  value: ExpansionType;
  label: string;
  description: string;
  icon: string;
}> = [
  {
    value: 'summarize',
    label: 'Summarize',
    description: 'Create a comprehensive summary of the selected period',
    icon: '📝',
  },
  {
    value: 'extract_themes',
    label: 'Extract Themes',
    description: 'Identify recurring themes and topics',
    icon: '🔍',
  },
  {
    value: 'identify_patterns',
    label: 'Identify Patterns',
    description: 'Find behavioral and emotional patterns',
    icon: '🧩',
  },
  {
    value: 'find_connections',
    label: 'Find Connections',
    description: 'Discover meaningful connections between moments',
    icon: '🔗',
  },
];

export default function MemoryExpansionDialog({
  isOpen,
  onClose,
  onExpansionRequested,
  selectedMomentIds,
  sphereId,
}: MemoryExpansionDialogProps) {
  const [expansionType, setExpansionType] = useState<ExpansionType>('summarize');
  const [selectionMode, setSelectionMode] = useState<'selected' | 'timeRange'>(
    selectedMomentIds && selectedMomentIds.length > 0 ? 'selected' : 'timeRange'
  );
  const [startDate, setStartDate] = useState(format(subDays(new Date(), 30), 'yyyy-MM-dd'));
  const [endDate, setEndDate] = useState(format(new Date(), 'yyyy-MM-dd'));
  const [priority, setPriority] = useState<'high' | 'normal' | 'low'>('normal');
  const [showConfirmation, setShowConfirmation] = useState(false);

  const requestExpansion = useRequestMemoryExpansion();

  const handleSubmit = async () => {
    try {
      const data: any = {
        expansionType,
        priority,
      };

      if (selectionMode === 'selected' && selectedMomentIds && selectedMomentIds.length > 0) {
        data.momentIds = selectedMomentIds;
      } else if (selectionMode === 'timeRange') {
        data.timeRange = {
          startDate: new Date(startDate).toISOString(),
          endDate: new Date(endDate).toISOString(),
        };
      }

      if (sphereId) {
        data.sphereId = sphereId;
      }

      const result = await requestExpansion.mutateAsync(data);
      setShowConfirmation(true);
      onExpansionRequested?.(result.jobId);

      // Auto-close after 2 seconds
      setTimeout(() => {
        setShowConfirmation(false);
        onClose();
      }, 2000);
    } catch (error) {
      console.error('Failed to request expansion:', error);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="w-full max-w-2xl rounded-lg bg-white shadow-xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <div className="flex items-center gap-3">
            <Sparkles className="h-6 w-6 text-purple-600" />
            <div>
              <h2 className="text-xl font-semibold text-gray-900">Memory Expansion</h2>
              <p className="text-sm text-gray-500">AI-powered deeper analysis of your moments</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          {showConfirmation ? (
            <div className="flex flex-col items-center justify-center py-12">
              <div className="mb-4 rounded-full bg-green-100 p-4">
                <Check className="h-8 w-8 text-green-600" />
              </div>
              <h3 className="mb-2 text-lg font-semibold text-gray-900">Expansion Requested</h3>
              <p className="text-center text-gray-600">
                Your memory expansion is being processed. You'll be able to view the results shortly.
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Expansion Type Selection */}
              <div>
                <label className="mb-3 block text-sm font-medium text-gray-700">
                  Expansion Type
                </label>
                <div className="grid grid-cols-2 gap-3">
                  {EXPANSION_TYPES.map((type) => (
                    <button
                      key={type.value}
                      onClick={() => setExpansionType(type.value)}
                      className={`rounded-lg border-2 p-4 text-left transition-colors ${
                        expansionType === type.value
                          ? 'border-purple-500 bg-purple-50'
                          : 'border-gray-200 bg-white hover:border-gray-300'
                      }`}
                    >
                      <div className="mb-2 text-2xl">{type.icon}</div>
                      <div className="mb-1 font-medium text-gray-900">{type.label}</div>
                      <div className="text-xs text-gray-500">{type.description}</div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Selection Mode */}
              <div>
                <label className="mb-3 block text-sm font-medium text-gray-700">
                  What to analyze
                </label>
                <div className="flex gap-3">
                  {selectedMomentIds && selectedMomentIds.length > 0 && (
                    <button
                      onClick={() => setSelectionMode('selected')}
                      className={`flex flex-1 items-center gap-2 rounded-lg border-2 px-4 py-3 ${
                        selectionMode === 'selected'
                          ? 'border-purple-500 bg-purple-50'
                          : 'border-gray-200 bg-white hover:border-gray-300'
                      }`}
                    >
                      <Tag className="h-5 w-5" />
                      <div className="text-left">
                        <div className="font-medium">Selected Moments</div>
                        <div className="text-xs text-gray-500">
                          {selectedMomentIds.length} moment{selectedMomentIds.length !== 1 ? 's' : ''}
                        </div>
                      </div>
                    </button>
                  )}
                  <button
                    onClick={() => setSelectionMode('timeRange')}
                    className={`flex flex-1 items-center gap-2 rounded-lg border-2 px-4 py-3 ${
                      selectionMode === 'timeRange'
                        ? 'border-purple-500 bg-purple-50'
                        : 'border-gray-200 bg-white hover:border-gray-300'
                    }`}
                  >
                    <Calendar className="h-5 w-5" />
                    <div className="text-left">
                      <div className="font-medium">Time Range</div>
                      <div className="text-xs text-gray-500">Select date range</div>
                    </div>
                  </button>
                </div>
              </div>

              {/* Time Range Selection */}
              {selectionMode === 'timeRange' && (
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="mb-2 block text-sm font-medium text-gray-700">
                      Start Date
                    </label>
                    <input
                      type="date"
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2"
                    />
                  </div>
                  <div>
                    <label className="mb-2 block text-sm font-medium text-gray-700">End Date</label>
                    <input
                      type="date"
                      value={endDate}
                      onChange={(e) => setEndDate(e.target.value)}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2"
                    />
                  </div>
                </div>
              )}

              {/* Priority Selection */}
              <div>
                <label className="mb-3 block text-sm font-medium text-gray-700">Priority</label>
                <div className="flex gap-3">
                  {(['high', 'normal', 'low'] as const).map((p) => (
                    <button
                      key={p}
                      onClick={() => setPriority(p)}
                      className={`flex-1 rounded-lg border-2 px-4 py-2 capitalize ${
                        priority === p
                          ? 'border-purple-500 bg-purple-50 text-purple-700'
                          : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300'
                      }`}
                    >
                      {p}
                    </button>
                  ))}
                </div>
              </div>

              {/* Info Banner */}
              <div className="flex items-start gap-3 rounded-lg bg-blue-50 p-4">
                <AlertCircle className="h-5 w-5 flex-shrink-0 text-blue-600" />
                <div className="text-sm text-blue-800">
                  <p className="font-medium">How it works</p>
                  <p className="mt-1">
                    AI will analyze your moments and generate insights based on the selected type.
                    Processing typically takes 10-30 seconds depending on the number of moments.
                  </p>
                </div>
              </div>

              {/* Error Message */}
              {requestExpansion.isError && (
                <div className="rounded-lg bg-red-50 p-4 text-sm text-red-800">
                  Failed to request expansion. Please try again.
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        {!showConfirmation && (
          <div className="flex items-center justify-end gap-3 border-t border-gray-200 px-6 py-4">
            <button onClick={onClose} className="rounded-lg px-4 py-2 text-gray-600 hover:bg-gray-100">
              Cancel
            </button>
            <button
              onClick={handleSubmit}
              disabled={requestExpansion.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-6 py-2 text-white hover:bg-purple-700 disabled:opacity-50"
            >
              {requestExpansion.isPending ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Requesting...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4" />
                  Request Expansion
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
