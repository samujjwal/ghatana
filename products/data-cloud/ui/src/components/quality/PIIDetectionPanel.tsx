/**
 * PII Detection Panel Component
 *
 * Displays detected PII in datasets with masking options.
 * Part of Journey 6: Data Quality & Validation
 *
 * @doc.type component
 * @doc.purpose PII detection and masking interface
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Shield, Eye, EyeOff, AlertTriangle, CheckCircle } from 'lucide-react';
import { qualityService, PIIDetection } from '../../api/quality.service';
import BaseCard from '../cards/BaseCard';
import { Button } from '../common/Button';

interface PIIDetectionPanelProps {
  datasetId?: string;
}

export function PIIDetectionPanel({ datasetId }: PIIDetectionPanelProps) {
  const queryClient = useQueryClient();
  const [selectedFields, setSelectedFields] = useState<string[]>([]);

  const { data: detections, isLoading } = useQuery({
    queryKey: ['pii-detections', datasetId],
    queryFn: () => qualityService.getPIIDetections(datasetId),
  });

  const maskMutation = useMutation({
    mutationFn: ({ datasetId, fields }: { datasetId: string; fields: string[] }) =>
      qualityService.maskPII(datasetId, fields),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pii-detections'] });
      setSelectedFields([]);
    },
  });

  const scanMutation = useMutation({
    mutationFn: (datasetId: string) => qualityService.scanForPII(datasetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pii-detections'] });
    },
  });

  const handleToggleField = (fieldKey: string) => {
    setSelectedFields((prev) =>
      prev.includes(fieldKey)
        ? prev.filter((f) => f !== fieldKey)
        : [...prev, fieldKey]
    );
  };

  const handleMaskSelected = () => {
    if (selectedFields.length === 0) return;

    const fieldsByDataset = new Map<string, string[]>();
    selectedFields.forEach((key) => {
      const [datasetId, fieldName] = key.split(':');
      if (!fieldsByDataset.has(datasetId)) {
        fieldsByDataset.set(datasetId, []);
      }
      fieldsByDataset.get(datasetId)!.push(fieldName);
    });

    fieldsByDataset.forEach((fields, datasetId) => {
      maskMutation.mutate({ datasetId, fields });
    });
  };

  const getPIITypeColor = (type: string): string => {
    switch (type) {
      case 'SSN':
      case 'CREDIT_CARD':
        return 'text-red-600 bg-red-50';
      case 'EMAIL':
      case 'PHONE':
        return 'text-orange-600 bg-orange-50';
      default:
        return 'text-yellow-600 bg-yellow-50';
    }
  };

  const getPIITypeIcon = (type: string) => {
    switch (type) {
      case 'SSN':
      case 'CREDIT_CARD':
        return <AlertTriangle className="h-4 w-4" />;
      default:
        return <Shield className="h-4 w-4" />;
    }
  };

  if (isLoading) {
    return (
      <BaseCard title="PII Detection">
        <div className="animate-pulse space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-12 bg-gray-200 rounded"></div>
          ))}
        </div>
      </BaseCard>
    );
  }

  const unmaskedDetections = detections?.filter((d) => !d.masked) || [];
  const maskedDetections = detections?.filter((d) => d.masked) || [];

  return (
    <BaseCard
      title="PII Detection"
      subtitle={`${unmaskedDetections.length} unmasked, ${maskedDetections.length} masked`}
      actions={
        <div className="flex items-center gap-2">
          {datasetId && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => scanMutation.mutate(datasetId)}
              isLoading={scanMutation.isPending}
            >
              Scan Dataset
            </Button>
          )}
          {selectedFields.length > 0 && (
            <Button
              variant="primary"
              size="sm"
              onClick={handleMaskSelected}
              isLoading={maskMutation.isPending}
            >
              Mask Selected ({selectedFields.length})
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4">
        {/* Unmasked PII */}
        {unmaskedDetections.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold text-gray-900 mb-2 flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-orange-500" />
              Unmasked PII
            </h4>
            <div className="space-y-2">
              {unmaskedDetections.map((detection) => {
                const fieldKey = `${detection.datasetId}:${detection.fieldName}`;
                const isSelected = selectedFields.includes(fieldKey);

                return (
                  <div
                    key={fieldKey}
                    className={`flex items-center justify-between p-3 rounded-lg border-2 transition-colors cursor-pointer ${
                      isSelected
                        ? 'border-primary-500 bg-primary-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                    onClick={() => handleToggleField(fieldKey)}
                  >
                    <div className="flex items-center gap-3 flex-1">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => handleToggleField(fieldKey)}
                        className="h-4 w-4 text-primary-600 rounded"
                        onClick={(e) => e.stopPropagation()}
                      />

                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-gray-900">
                            {detection.fieldName}
                          </span>
                          <span
                            className={`px-2 py-0.5 rounded text-xs font-semibold flex items-center gap-1 ${getPIITypeColor(
                              detection.piiType
                            )}`}
                          >
                            {getPIITypeIcon(detection.piiType)}
                            {detection.piiType}
                          </span>
                        </div>
                        <div className="text-xs text-gray-500 mt-1">
                          Confidence: {(detection.confidence * 100).toFixed(0)}% •
                          Samples: {detection.sampleCount}
                        </div>
                      </div>

                      <Eye className="h-4 w-4 text-gray-400" />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Masked PII */}
        {maskedDetections.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold text-gray-900 mb-2 flex items-center gap-2">
              <CheckCircle className="h-4 w-4 text-green-500" />
              Masked PII
            </h4>
            <div className="space-y-2">
              {maskedDetections.map((detection) => (
                <div
                  key={`${detection.datasetId}:${detection.fieldName}`}
                  className="flex items-center justify-between p-3 rounded-lg border border-gray-200 bg-gray-50"
                >
                  <div className="flex items-center gap-3 flex-1">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-700">
                          {detection.fieldName}
                        </span>
                        <span className="px-2 py-0.5 bg-gray-200 rounded text-xs font-semibold text-gray-600">
                          {detection.piiType}
                        </span>
                      </div>
                    </div>
                    <EyeOff className="h-4 w-4 text-gray-400" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Empty State */}
        {detections?.length === 0 && (
          <div className="text-center py-8 text-gray-500">
            <Shield className="h-12 w-12 mx-auto mb-2 opacity-50" />
            <p>No PII detected</p>
            {datasetId && (
              <button
                onClick={() => scanMutation.mutate(datasetId)}
                className="mt-2 text-sm text-primary-600 hover:text-primary-700"
              >
                Run PII Scan
              </button>
            )}
          </div>
        )}
      </div>
    </BaseCard>
  );
}

export default PIIDetectionPanel;

