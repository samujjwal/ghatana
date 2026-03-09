/**
 * Deploy Mode Renderer
 * 
 * @doc.type class
 * @doc.purpose Provides deployment canvas for infrastructure and CI/CD
 * @doc.layer product
 * @doc.pattern ModeRenderer
 * 
 * Deploy mode is focused on infrastructure with:
 * - System level: Infrastructure diagram (AWS/GCP/Azure resources)
 * - Component level: Container orchestration (Pods, Services, Volumes)
 * - File level: Config file browser (Dockerfile, k8s YAML)
 * - Code level: Config editor (YAML/JSON validation)
 * 
 * Uses DevSecOps deployment phase canvas for pipeline visualization.
 */

import { Rocket as DeployIcon, Sparkles as AIIcon, Cloud as CloudIcon, Box as ContainerIcon, FileText as ConfigIcon, Code as EditorIcon, Plus as AddIcon, Shield as SecurityIcon } from 'lucide-react';
import {
  Box,
  Button,
  Chip,
  Stack,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import React from 'react';

import type { ModeRendererProps } from './types';

// Level-specific configurations for deploy mode
const LEVEL_CONFIG = {
  system: {
    title: 'Infrastructure',
    description: 'Design cloud infrastructure',
    tools: ['AWS/GCP/Azure Resources', 'Networks', 'Storage'],
    emptyMessage: 'Design infrastructure',
    aiAction: 'Auto-Examine: Find security holes',
    icon: <CloudIcon />,
    color: '#9C27B0',
  },
  component: {
    title: 'Container Orchestration',
    description: 'Configure Kubernetes resources',
    tools: ['Pods', 'Services', 'Volumes'],
    emptyMessage: 'Configure containers',
    aiAction: 'Optimize container config',
    icon: <ContainerIcon />,
    color: '#9C27B0',
  },
  file: {
    title: 'Config Files',
    description: 'Browse deployment configs',
    tools: ['Dockerfile', 'k8s YAML', 'Terraform'],
    emptyMessage: 'View config files',
    aiAction: 'Generate Dockerfile',
    icon: <ConfigIcon />,
    color: '#9C27B0',
  },
  code: {
    title: 'Config Editor',
    description: 'Edit deployment configurations',
    tools: ['YAML/JSON Validation', 'Schema Checking'],
    emptyMessage: 'Edit deployment config',
    aiAction: 'Validate YAML syntax',
    icon: <EditorIcon />,
    color: '#9C27B0',
  },
};

/**
 * Deploy Mode Empty State Component
 */
const DeployEmptyState: React.FC<{
  level: ModeRendererProps['level'];
  onAskAI?: () => void;
  onGetStarted?: () => void;
}> = ({ level, onAskAI, onGetStarted }) => {
  const config = LEVEL_CONFIG[level];

  return (
    <Box
      className="flex flex-col items-center justify-center h-full p-8 min-h-[400px]"
    >
      <Paper
        elevation={0}
        className="p-8 text-center max-w-[480px] rounded-xl" >
        <Box
          className="mb-6 rounded-full flex items-center justify-center w-[80px] h-[80px] mx-auto" style={{ backgroundColor: config.color, backgroundColor: 'rgba(156' }} >
          {React.cloneElement(config.icon as React.ReactElement, { 
            sx: { fontSize: 40, color: '#fff' } 
          })}
        </Box>

        <Typography variant="h5" gutterBottom fontWeight={600}>
          {config.title}
        </Typography>
        
        <Typography variant="body1" color="text.secondary" className="mb-6">
          {config.emptyMessage}
        </Typography>

        <Stack direction="row" spacing={1} justifyContent="center" flexWrap="wrap" className="mb-6">
          {config.tools.map((tool) => (
            <Chip key={tool} label={tool} size="small" variant="outlined" />
          ))}
        </Stack>

        <Stack direction="row" spacing={2} justifyContent="center">
          {onGetStarted && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={onGetStarted}
              className="hover:bg-[#7B1FA2]" style={{ backgroundColor: config.color, transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }}
            >
              Add Resource
            </Button>
          )}
          {onAskAI && (
            <Button
              variant="outlined"
              startIcon={<AIIcon />}
              onClick={onAskAI}
              style={{ color: config.color, borderColor: config.color }}
            >
              {config.aiAction}
            </Button>
          )}
        </Stack>
      </Paper>
    </Box>
  );
};

/**
 * Deploy Mode Content Renderer
 * 
 * Integrates DevSecOps deployment phase canvas for pipeline visualization.
 */
export const DeployModeRenderer: React.FC<ModeRendererProps> = ({
  level,
  hasContent,
  onAskAI,
  onGetStarted,
  children,
  readOnly = false,
}) => {
  const config = LEVEL_CONFIG[level];

  // If no content, show empty state
  if (!hasContent) {
    return (
      <DeployEmptyState
        level={level}
        onAskAI={onAskAI}
        onGetStarted={onGetStarted}
      />
    );
  }

  // Render children with deploy-specific toolbar
  return (
    <Box
      className="relative h-full w-full"
    >
      {/* Mode-specific toolbar overlay */}
      {!readOnly && (
        <Paper
          elevation={1}
          className="absolute px-4 py-2 flex gap-2 rounded-lg top-[8px] left-[50%] z-20" >
          <Chip
            icon={<DeployIcon />}
            label="Deploy"
            size="small"
            clickable
            className="bg-[rgba(76,_175,_80,_0.2)] hover:bg-[rgba(76,_175,_80,_0.3)]"
          />
          <Chip
            icon={<SecurityIcon />}
            label="Scan"
            size="small"
            clickable
            className="bg-[rgba(255,_152,_0,_0.2)] hover:bg-[rgba(255,_152,_0,_0.3)]"
          />
          <Chip
            icon={<AIIcon />}
            label="Auto-Examine"
            size="small"
            clickable
            className="text-[#fff] [&_.MuiChip-icon]:text-white" style={{ backgroundColor: config.color, backgroundColor: 'rgba(255' }}
            onClick={onAskAI}
          />
        </Paper>
      )}

      {/* Level indicator */}
      <Box
        className="absolute flex items-center gap-2 px-4 py-1 rounded bottom-[48px] left-[16px] z-20 shadow-sm" >
        <DeployIcon className="text-base" style={{ color: config.color }} />
        <Typography variant="caption" fontWeight={500}>
          {config.title}
        </Typography>
      </Box>

      {/* Render canvas children */}
      {children}
    </Box>
  );
};

export default DeployModeRenderer;
