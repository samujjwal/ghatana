/**
 * Language Insights Page
 * Comprehensive view of language evolution and patterns
 * Week 13 Day 62-63 - Surface evolution insights with filters
 */

import React, { useState } from 'react';
import { LanguageEvolutionPanel } from '../components/LanguageEvolution/LanguageEvolutionPanel';
import { LanguageEvolutionFilters } from '../components/LanguageEvolution/LanguageEvolutionFilters';
import { Sparkles, ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const LanguageInsightsPage: React.FC = () => {
  const navigate = useNavigate();
  const [selectedSphereId] = useState<string | undefined>();

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="border-b border-gray-200 bg-white shadow-sm">
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                onClick={() => navigate('/app')}
                className="rounded-lg p-2 hover:bg-gray-100 transition-colors"
              >
                <ArrowLeft className="h-5 w-5 text-gray-600" />
              </button>
              <div className="flex items-center gap-3">
                <div className="rounded-full bg-purple-100 p-2">
                  <Sparkles className="h-6 w-6 text-purple-600" />
                </div>
                <div>
                  <h1 className="text-2xl font-bold text-gray-900">Language Insights</h1>
                  <p className="text-sm text-gray-600">
                    Explore how your language evolves over time
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="space-y-6">
          {/* Filters */}
          <LanguageEvolutionFilters
            onFiltersChange={() => {}}
            availableEmotions={[
              'joy',
              'sadness',
              'anger',
              'fear',
              'surprise',
              'love',
              'gratitude',
              'anxiety',
              'excitement',
              'contentment',
            ]}
          />

          {/* Evolution Panel */}
          <LanguageEvolutionPanel
            sphereId={selectedSphereId}
            periodDays={30}
          />
        </div>
      </div>
    </div>
  );
};
