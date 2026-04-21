/**
 * Requirement Extractor
 *
 * AI-powered requirement extraction from IntentConfig.
 *
 * @packageDocumentation
 */

import { Sparkles as AutoAwesome, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  Alert,
  Divider,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

interface IntentConfig {
  id: string;
  intent: string;
  author: string;
  tags: string[];
}

interface AcceptanceCriteria {
  id: string;
  criteria: string;
  priority: 'must' | 'should' | 'could';
  status: 'pending' | 'in-progress' | 'completed' | 'blocked';
}

interface RequirementConfig {
  id: string;
  version: string;
  title: string;
  description: string;
  type: 'functional' | 'non-functional' | 'constraint' | 'user-story';
  priority: 'critical' | 'high' | 'medium' | 'low';
  status: 'draft' | 'proposed' | 'approved' | 'in-progress' | 'completed' | 'rejected';
  acceptanceCriteria: AcceptanceCriteria[];
  intentId?: string;
  linkedPageIds: string[];
  linkedComponentIds: string[];
  createdAt: string;
  updatedAt: string;
  author: string;
  assignee?: string;
  tags: string[];
}

function validateTransformation(intentConfig: IntentConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!intentConfig.id) {
    errors.push('IntentConfig must have an id');
  }

  if (!intentConfig.intent) {
    errors.push('IntentConfig must have an intent');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

async function transformIntentToRequirements(intentConfig: IntentConfig): Promise<RequirementConfig[]> {
  const now = new Date().toISOString();

  return [
    {
      id: `req-${Date.now()}`,
      version: '1.0.0',
      title: `Requirement for: ${intentConfig.intent.substring(0, 50)}${intentConfig.intent.length > 50 ? '...' : ''}`,
      description: `Derived from intent: ${intentConfig.intent}`,
      type: 'functional',
      priority: 'high',
      status: 'draft',
      acceptanceCriteria: [
        {
          id: `ac-${Date.now()}`,
          criteria: 'Implement the described functionality',
          priority: 'must',
          status: 'pending',
        },
      ],
      intentId: intentConfig.id,
      linkedPageIds: [],
      linkedComponentIds: [],
      createdAt: now,
      updatedAt: now,
      author: intentConfig.author,
      tags: intentConfig.tags,
    },
  ];
}

/**
 * @doc.type component
 * @doc.purpose AI-powered requirement extraction from IntentConfig
 * @doc.layer product
 * @doc.pattern Widget
 */
interface RequirementExtractorProps {
  intentConfig: IntentConfig | null;
  onRequirementsExtracted?: (requirements: RequirementConfig[]) => void;
}

export const RequirementExtractor: React.FC<RequirementExtractorProps> = ({
  intentConfig,
  onRequirementsExtracted,
}) => {
  const [isExtracting, setIsExtracting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [requirements, setRequirements] = useState<RequirementConfig[]>([]);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const handleExtractRequirements = useCallback(async () => {
    if (!intentConfig) return;

    setError(null);
    setIsExtracting(true);

    const validation = validateTransformation(intentConfig);
    if (!validation.valid) {
      setError(validation.errors.join(', '));
      setIsExtracting(false);
      return;
    }

    try {
      const extracted = await transformIntentToRequirements(intentConfig);
      setRequirements(extracted);
      setExpandedIds(new Set(extracted.map((requirement: RequirementConfig) => requirement.id)));
      onRequirementsExtracted?.(extracted);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to extract requirements');
    } finally {
      setIsExtracting(false);
    }
  }, [intentConfig, onRequirementsExtracted]);

  const toggleExpanded = useCallback((id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  if (!intentConfig) {
    return (
      <Alert severity="info">
        Parse an intent first to extract requirements.
      </Alert>
    );
  }

  return (
    <Box data-testid="requirement-extractor">
      <Typography variant="h6" gutterBottom>
        Requirement Extractor
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Extract structured requirements from the parsed intent using AI.
      </Typography>

      <Button
        variant="contained"
        onClick={handleExtractRequirements}
        disabled={isExtracting}
        startIcon={<AutoAwesome size={16} />}
        sx={{ mt: 2 }}
        data-testid="extract-requirements-button"
      >
        {isExtracting ? 'Extracting...' : 'Extract Requirements'}
      </Button>

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}

      {requirements.length > 0 && (
        <Stack spacing={2} className="mt-3">
          <Typography variant="subtitle1">
            Extracted Requirements ({requirements.length})
          </Typography>
          {requirements.map((req) => (
            <Paper key={req.id} variant="outlined" className="p-3">
              <Stack spacing={2}>
                <Box
                  className="flex justify-between items-center cursor-pointer"
                  onClick={() => toggleExpanded(req.id)}
                >
                  <Typography variant="subtitle2" className="font-medium">
                    {req.title}
                  </Typography>
                  {expandedIds.has(req.id) ? (
                    <ExpandLess size={16} />
                  ) : (
                    <ExpandMore size={16} />
                  )}
                </Box>

                <Stack direction="row" spacing={1}>
                  <Typography
                    variant="caption"
                    className="px-2 py-0.5 rounded bg-blue-100 text-blue-700"
                  >
                    {req.type}
                  </Typography>
                  <Typography
                    variant="caption"
                    className="px-2 py-0.5 rounded bg-purple-100 text-purple-700"
                  >
                    {req.priority}
                  </Typography>
                  <Typography
                    variant="caption"
                    className="px-2 py-0.5 rounded bg-green-100 text-green-700"
                  >
                    {req.status}
                  </Typography>
                </Stack>

                {expandedIds.has(req.id) && (
                  <>
                    <Divider />
                    <Typography variant="body2">{req.description}</Typography>

                    {req.acceptanceCriteria && req.acceptanceCriteria.length > 0 && (
                      <>
                        <Typography variant="subtitle2" className="font-medium">
                          Acceptance Criteria
                        </Typography>
                        <Stack spacing={1}>
                          {req.acceptanceCriteria.map((ac: AcceptanceCriteria, idx: number) => (
                            <Typography key={ac.id || idx} variant="body2">
                              • {ac.criteria}
                            </Typography>
                          ))}
                        </Stack>
                      </>
                    )}

                    {req.tags && req.tags.length > 0 && (
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {req.tags.map((tag: string) => (
                          <Typography
                            key={tag}
                            variant="caption"
                            className="px-2 py-0.5 rounded bg-gray-100 text-gray-700"
                          >
                            {tag}
                          </Typography>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  );
};
