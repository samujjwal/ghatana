/**
 * Auto Mapper Component
 *
 * AI-powered schema mapping with automatic field detection and suggestions.
 * Part of Journey 4: Co-Pilot Building and Journey 8: Workflow Builder
 *
 * @doc.type component
 * @doc.purpose Automatic schema mapping with AI assistance
 * @doc.layer frontend
 */

import React, { useState, useEffect } from 'react';
import { ArrowRight, Sparkles, CheckCircle, AlertCircle, Zap, RefreshCw } from 'lucide-react';
import { Button } from '../common/Button';
import BaseCard from '../cards/BaseCard';

interface SchemaField {
  name: string;
  type: string;
  description?: string;
  required?: boolean;
}

interface Schema {
  name: string;
  fields: SchemaField[];
}

interface FieldMapping {
  sourceField: string;
  targetField: string;
  confidence: number;
  transformation?: string;
  status: 'AUTO' | 'MANUAL' | 'PENDING';
}

interface AutoMapperProps {
  sourceSchema: Schema;
  targetSchema: Schema;
  onMappingChange?: (mappings: FieldMapping[]) => void;
  onApply?: (mappings: FieldMapping[]) => void;
  autoMap?: boolean;
}

export function AutoMapper({
  sourceSchema,
  targetSchema,
  onMappingChange,
  onApply,
  autoMap = true,
}: AutoMapperProps) {
  const [mappings, setMappings] = useState<FieldMapping[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [selectedSource, setSelectedSource] = useState<string | null>(null);
  const [selectedTarget, setSelectedTarget] = useState<string | null>(null);

  // Auto-generate mappings on mount
  useEffect(() => {
    if (autoMap && sourceSchema && targetSchema) {
      performAutoMapping();
    }
  }, [sourceSchema, targetSchema, autoMap]);

  // Notify parent of mapping changes
  useEffect(() => {
    onMappingChange?.(mappings);
  }, [mappings, onMappingChange]);

  const performAutoMapping = () => {
    setIsAnalyzing(true);

    // Simulate AI analysis
    setTimeout(() => {
      const autoMappings = generateAutoMappings(sourceSchema, targetSchema);
      setMappings(autoMappings);
      setIsAnalyzing(false);
    }, 1500);
  };

  const handleManualMapping = () => {
    if (!selectedSource || !selectedTarget) return;

    const existingIndex = mappings.findIndex(m => m.targetField === selectedTarget);
    const newMapping: FieldMapping = {
      sourceField: selectedSource,
      targetField: selectedTarget,
      confidence: 1.0,
      status: 'MANUAL',
    };

    if (existingIndex >= 0) {
      // Update existing mapping
      setMappings(prev => [
        ...prev.slice(0, existingIndex),
        newMapping,
        ...prev.slice(existingIndex + 1),
      ]);
    } else {
      // Add new mapping
      setMappings(prev => [...prev, newMapping]);
    }

    setSelectedSource(null);
    setSelectedTarget(null);
  };

  const handleRemoveMapping = (targetField: string) => {
    setMappings(prev => prev.filter(m => m.targetField !== targetField));
  };

  const handleApplyMappings = () => {
    onApply?.(mappings);
  };

  const getMappedSourceFields = () => {
    return new Set(mappings.map(m => m.sourceField));
  };

  const getMappedTargetFields = () => {
    return new Set(mappings.map(m => m.targetField));
  };

  const getConfidenceColor = (confidence: number): string => {
    if (confidence >= 0.9) return 'text-green-600';
    if (confidence >= 0.7) return 'text-yellow-600';
    return 'text-orange-600';
  };

  const mappedCount = mappings.length;
  const totalTargetFields = targetSchema.fields.length;
  const completionRate = (mappedCount / totalTargetFields) * 100;

  return (
    <BaseCard
      title="Auto Schema Mapper"
      subtitle={`${mappedCount}/${totalTargetFields} fields mapped (${completionRate.toFixed(0)}%)`}
      actions={
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={performAutoMapping}
            isLoading={isAnalyzing}
          >
            <RefreshCw className="h-4 w-4" />
            Re-analyze
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleApplyMappings}
            disabled={mappedCount === 0}
          >
            <CheckCircle className="h-4 w-4" />
            Apply Mappings
          </Button>
        </div>
      }
    >
      {/* Progress Bar */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-gray-700">Mapping Progress</span>
          <span className="text-sm font-semibold text-primary-600">
            {completionRate.toFixed(0)}%
          </span>
        </div>
        <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-primary-500 to-purple-500 transition-all duration-500"
            style={{ width: `${completionRate}%` }}
          />
        </div>
      </div>

      {/* Analyzing State */}
      {isAnalyzing && (
        <div className="flex items-center justify-center py-8">
          <div className="text-center">
            <Sparkles className="h-12 w-12 text-primary-600 mx-auto mb-3 animate-pulse" />
            <p className="text-sm font-semibold text-gray-900">
              AI Analyzing Schemas...
            </p>
            <p className="text-xs text-gray-600 mt-1">
              Detecting field patterns and similarities
            </p>
          </div>
        </div>
      )}

      {/* Mapping View */}
      {!isAnalyzing && (
        <div className="space-y-6">
          {/* Existing Mappings */}
          {mappings.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-gray-900 mb-3">
                Auto-Generated Mappings
              </h3>
              <div className="space-y-2">
                {mappings.map((mapping) => (
                  <div
                    key={mapping.targetField}
                    className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg border border-gray-200"
                  >
                    {/* Source Field */}
                    <div className="flex-1">
                      <div className="text-sm font-medium text-gray-900">
                        {mapping.sourceField}
                      </div>
                      <div className="text-xs text-gray-500">
                        {sourceSchema.fields.find(f => f.name === mapping.sourceField)?.type}
                      </div>
                    </div>

                    {/* Arrow */}
                    <ArrowRight className="h-5 w-5 text-gray-400 flex-shrink-0" />

                    {/* Target Field */}
                    <div className="flex-1">
                      <div className="text-sm font-medium text-gray-900">
                        {mapping.targetField}
                      </div>
                      <div className="text-xs text-gray-500">
                        {targetSchema.fields.find(f => f.name === mapping.targetField)?.type}
                      </div>
                    </div>

                    {/* Confidence & Status */}
                    <div className="flex items-center gap-2">
                      {mapping.status === 'AUTO' ? (
                        <div
                          className={`text-xs font-semibold ${getConfidenceColor(
                            mapping.confidence
                          )}`}
                        >
                          {Math.round(mapping.confidence * 100)}%
                        </div>
                      ) : (
                        <CheckCircle className="h-4 w-4 text-green-600" />
                      )}

                      {mapping.transformation && (
                        <div className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded text-xs font-semibold">
                          {mapping.transformation}
                        </div>
                      )}

                      <button
                        onClick={() => handleRemoveMapping(mapping.targetField)}
                        className="text-red-600 hover:text-red-700 text-xs"
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Manual Mapping Interface */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-3">
              Manual Mapping
            </h3>
            <div className="grid grid-cols-2 gap-4">
              {/* Source Fields */}
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-2">
                  Source Schema: {sourceSchema.name}
                </label>
                <div className="space-y-1 max-h-64 overflow-y-auto border border-gray-200 rounded-lg p-2">
                  {sourceSchema.fields.map((field) => {
                    const isMapped = getMappedSourceFields().has(field.name);
                    return (
                      <button
                        key={field.name}
                        onClick={() => setSelectedSource(field.name)}
                        className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                          selectedSource === field.name
                            ? 'bg-primary-100 border-primary-300 border'
                            : isMapped
                            ? 'bg-green-50 text-green-700 border border-green-200'
                            : 'bg-white hover:bg-gray-50 border border-gray-200'
                        }`}
                      >
                        <div className="font-medium">{field.name}</div>
                        <div className="text-xs opacity-70">{field.type}</div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Target Fields */}
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-2">
                  Target Schema: {targetSchema.name}
                </label>
                <div className="space-y-1 max-h-64 overflow-y-auto border border-gray-200 rounded-lg p-2">
                  {targetSchema.fields.map((field) => {
                    const isMapped = getMappedTargetFields().has(field.name);
                    return (
                      <button
                        key={field.name}
                        onClick={() => setSelectedTarget(field.name)}
                        className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                          selectedTarget === field.name
                            ? 'bg-primary-100 border-primary-300 border'
                            : isMapped
                            ? 'bg-green-50 text-green-700 border border-green-200'
                            : 'bg-white hover:bg-gray-50 border border-gray-200'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="font-medium">{field.name}</div>
                          {field.required && (
                            <span className="text-xs text-red-600">*</span>
                          )}
                        </div>
                        <div className="text-xs opacity-70">{field.type}</div>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Map Button */}
            <div className="mt-3 text-center">
              <Button
                variant="outline"
                size="md"
                onClick={handleManualMapping}
                disabled={!selectedSource || !selectedTarget}
              >
                <Zap className="h-4 w-4" />
                Map Selected Fields
              </Button>
            </div>
          </div>

          {/* Unmapped Required Fields Warning */}
          {targetSchema.fields.some(
            f => f.required && !getMappedTargetFields().has(f.name)
          ) && (
            <div className="p-3 bg-orange-50 border border-orange-200 rounded-lg">
              <div className="flex items-start gap-2">
                <AlertCircle className="h-5 w-5 text-orange-600 mt-0.5" />
                <div className="flex-1">
                  <div className="text-sm font-semibold text-orange-900 mb-1">
                    Required Fields Missing
                  </div>
                  <div className="text-xs text-orange-800">
                    The following required fields are not mapped:{' '}
                    {targetSchema.fields
                      .filter(f => f.required && !getMappedTargetFields().has(f.name))
                      .map(f => f.name)
                      .join(', ')}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </BaseCard>
  );
}

// AI-powered auto-mapping logic
function generateAutoMappings(sourceSchema: Schema, targetSchema: Schema): FieldMapping[] {
  const mappings: FieldMapping[] = [];

  for (const targetField of targetSchema.fields) {
    let bestMatch: { field: SchemaField; score: number } | undefined;

    for (const sourceField of sourceSchema.fields) {
      const score = calculateFieldSimilarity(sourceField, targetField);
      if (!bestMatch || score > bestMatch.score) {
        bestMatch = { field: sourceField, score };
      }
    }

    if (bestMatch && bestMatch.score > 0.6) {
      const mapping: FieldMapping = {
        sourceField: bestMatch.field.name,
        targetField: targetField.name,
        confidence: bestMatch.score,
        status: 'AUTO',
      };

      if (bestMatch.field.type !== targetField.type) {
        mapping.transformation = `${bestMatch.field.type} → ${targetField.type}`;
      }

      mappings.push(mapping);
    }
  }

  return mappings;
}

// Calculate similarity between two fields (0-1)
function calculateFieldSimilarity(source: SchemaField, target: SchemaField): number {
  let score = 0;

  // Exact name match
  if (source.name.toLowerCase() === target.name.toLowerCase()) {
    score += 0.8;
  }

  // Partial name match
  const sourceName = source.name.toLowerCase();
  const targetName = target.name.toLowerCase();
  if (sourceName.includes(targetName) || targetName.includes(sourceName)) {
    score += 0.4;
  }

  // Similar name patterns (e.g., user_id vs userId)
  const sourceNormalized = sourceName.replace(/[_-]/g, '');
  const targetNormalized = targetName.replace(/[_-]/g, '');
  if (sourceNormalized === targetNormalized) {
    score += 0.6;
  }

  // Type match
  if (source.type === target.type) {
    score += 0.2;
  }

  // Common patterns
  const patterns = [
    ['id', 'identifier', 'key'],
    ['name', 'title', 'label'],
    ['email', 'mail', 'address'],
    ['created', 'timestamp', 'date'],
    ['user', 'account', 'profile'],
  ];

  patterns.forEach(pattern => {
    if (pattern.some(p => sourceName.includes(p)) &&
        pattern.some(p => targetName.includes(p))) {
      score += 0.3;
    }
  });

  return Math.min(score, 1.0);
}

export default AutoMapper;
