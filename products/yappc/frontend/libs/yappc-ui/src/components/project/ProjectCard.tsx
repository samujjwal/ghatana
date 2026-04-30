/**
 * ProjectCard
 *
 * Displays a project as a compact summary card.
 *
 * @doc.type component
 * @doc.purpose Display a project summary with name, status, type, and AI health
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  LinearProgress,
  Tooltip,
  IconButton,
} from '@mui/material';
import { Settings as SettingsIcon, Brain as BrainIcon } from 'lucide-react';
import React from 'react';

import type { Project, ProjectStatus } from 'yappc-core/types';

const STATUS_COLORS: Record<
  ProjectStatus,
  'default' | 'success' | 'warning' | 'error' | 'info'
> = {
  DRAFT: 'default',
  ACTIVE: 'success',
  ARCHIVED: 'warning',
  COMPLETED: 'info',
};

export interface ProjectCardProps {
  project: Project;
  isSelected?: boolean;
  onSelect?: (project: Project) => void;
  onSettings?: (project: Project) => void;
  className?: string;
}

/**
 * Card component for a single project.
 */
export const ProjectCard: React.FC<ProjectCardProps> = ({
  project,
  isSelected = false,
  onSelect,
  onSettings,
  className,
}) => {
  const healthScore = project.aiHealthScore ?? null;

  return (
    <Card
      className={className}
      variant="outlined"
      sx={{
        cursor: onSelect ? 'pointer' : 'default',
        border: isSelected ? 2 : 1,
        borderColor: isSelected ? 'primary.main' : 'divider',
        transition: 'all 0.15s ease',
        '&:hover': onSelect
          ? { borderColor: 'primary.light', boxShadow: 1 }
          : {},
      }}
      onClick={onSelect ? () => onSelect(project) : undefined}
    >
      <CardContent sx={{ pb: '12px !important' }}>
        <Box display="flex" alignItems="flex-start" gap={1}>
          <Box flexGrow={1} minWidth={0}>
            <Box display="flex" alignItems="center" gap={0.75} mb={0.25}>
              <Typography
                variant="subtitle2"
                fontWeight={600}
                noWrap
                sx={{ flexGrow: 1 }}
              >
                {project.name}
              </Typography>
              <Chip
                label={project.status}
                size="small"
                color={STATUS_COLORS[project.status]}
                variant="outlined"
                sx={{ height: 18, fontSize: 10 }}
              />
            </Box>

            {project.description && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: '-webkit-box',
                  WebkitLineClamp: 1,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                }}
              >
                {project.description}
              </Typography>
            )}

            <Box display="flex" alignItems="center" gap={1} mt={0.75}>
              <Chip
                label={project.type.replace('_', ' ')}
                size="small"
                variant="outlined"
                sx={{ height: 16, fontSize: 10 }}
              />
              {project.lifecyclePhase && (
                <Chip
                  label={project.lifecyclePhase}
                  size="small"
                  variant="outlined"
                  sx={{ height: 16, fontSize: 10 }}
                />
              )}
            </Box>

            {healthScore !== null && (
              <Tooltip title={`AI health score: ${healthScore}%`}>
                <Box display="flex" alignItems="center" gap={0.5} mt={0.75}>
                  <BrainIcon size={11} />
                  <LinearProgress
                    variant="determinate"
                    value={healthScore}
                    color={
                      healthScore >= 70
                        ? 'success'
                        : healthScore >= 40
                          ? 'warning'
                          : 'error'
                    }
                    sx={{ flexGrow: 1, height: 4, borderRadius: 2 }}
                  />
                  <Typography
                    variant="caption"
                    sx={{ minWidth: 28, textAlign: 'right' }}
                  >
                    {healthScore}%
                  </Typography>
                </Box>
              </Tooltip>
            )}
          </Box>

          {onSettings && (
            <Tooltip title="Project settings">
              <IconButton
                size="small"
                onClick={(e) => {
                  e.stopPropagation();
                  onSettings(project);
                }}
              >
                <SettingsIcon size={15} />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      </CardContent>
    </Card>
  );
};
