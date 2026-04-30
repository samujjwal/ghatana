/**
 * ProjectList
 *
 * Renders a scrollable list of ProjectCards.
 *
 * @doc.type component
 * @doc.purpose Display a list of projects with selection, filtering, and actions
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Box, Typography, CircularProgress, Alert } from '@mui/material';
import React from 'react';

import type { Project } from 'yappc-core/types';

import { ProjectCard } from './ProjectCard';

export interface ProjectListProps {
  projects: Project[];
  selectedId?: string | null;
  isLoading?: boolean;
  error?: Error | null;
  onSelect?: (project: Project) => void;
  onSettings?: (project: Project) => void;
  emptyMessage?: string;
  className?: string;
}

/**
 * Renders a list of projects.
 */
export const ProjectList: React.FC<ProjectListProps> = ({
  projects,
  selectedId,
  isLoading,
  error,
  onSelect,
  onSettings,
  emptyMessage = 'No projects found.',
  className,
}) => {
  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" py={4}>
        <CircularProgress size={32} />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 1 }}>
        {error.message}
      </Alert>
    );
  }

  if (projects.length === 0) {
    return (
      <Box display="flex" justifyContent="center" py={4}>
        <Typography variant="body2" color="text.secondary">
          {emptyMessage}
        </Typography>
      </Box>
    );
  }

  return (
    <Box
      className={className}
      display="flex"
      flexDirection="column"
      gap={1}
      sx={{ overflowY: 'auto' }}
    >
      {projects.map((project) => (
        <ProjectCard
          key={project.id}
          project={project}
          isSelected={project.id === selectedId}
          onSelect={onSelect}
          onSettings={onSettings}
        />
      ))}
    </Box>
  );
};
