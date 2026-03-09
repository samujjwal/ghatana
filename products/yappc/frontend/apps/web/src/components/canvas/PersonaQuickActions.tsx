/**
 * PersonaQuickActions Component
 * 
 * @doc.type class
 * @doc.purpose Display quick actions based on active personas
 * @doc.layer product
 * @doc.pattern Component
 * 
 * Shows persona-specific quick actions in the canvas.
 * Virtual (AI agent) personas are marked with a badge.
 */

import { Bot as AgentIcon, User as HumanIcon } from 'lucide-react';
import {
  Box,
  Chip,
  Stack,
  Tooltip,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import React, { useCallback, useState } from 'react';

import { usePersonaAdaptiveCanvas } from '../../hooks/usePersonaAdaptiveCanvas';

import type { CanvasMode } from '../../types/canvas';

export interface PersonaQuickActionsProps {
  /** Current canvas mode */
  currentMode: CanvasMode;
  /** Callback when an action is triggered */
  onAction?: (action: string, mode?: CanvasMode) => void;
  /** Whether to show virtual persona info */
  showVirtualInfo?: boolean;
  /** Collapsed state */
  collapsed?: boolean;
}

/**
 * PersonaQuickActions - Displays persona-based quick actions
 */
export const PersonaQuickActions: React.FC<PersonaQuickActionsProps> = ({
  currentMode,
  onAction,
  showVirtualInfo = true,
  collapsed = false,
}) => {
  const { quickActions, isModeRelevant } = usePersonaAdaptiveCanvas();
  const [expandedPersona, setExpandedPersona] = useState<string | null>(null);

  const handleActionClick = useCallback((action: string, mode?: CanvasMode) => {
    onAction?.(action, mode);
  }, [onAction]);

  const togglePersonaExpand = useCallback((personaId: string) => {
    setExpandedPersona(prev => prev === personaId ? null : personaId);
  }, []);

  // Filter actions relevant to current mode if specified
  const relevantActions = quickActions.filter(persona => 
    persona.actions.some(action => 
      !action.mode || action.mode === currentMode || isModeRelevant(action.mode as CanvasMode)
    )
  );

  if (collapsed || relevantActions.length === 0) {
    return null;
  }

  return (
    <Paper
      elevation={1}
      className="absolute p-3 rounded-lg bottom-[52px] right-[16px] z-20 max-w-[320px] backdrop-blur-[8px]" style={{ backgroundColor: 'rgba(255' }} >
      <Typography variant="caption" color="text.secondary" className="mb-2 block">
        Quick Actions
      </Typography>
      
      <Stack spacing={1}>
        {relevantActions.map((persona) => (
          <Box key={persona.personaId}>
            {/* Persona header */}
            <Box
              onClick={() => togglePersonaExpand(persona.personaId)}
              className="flex items-center gap-2 cursor-pointer py-1 px-2 rounded hover:bg-gray-100"
            >
              <Typography variant="body2" className="text-base">
                {persona.personaIcon}
              </Typography>
              <Typography variant="body2" fontWeight={500} className="flex-1">
                {persona.personaName}
              </Typography>
              
              {persona.isVirtual && showVirtualInfo && (
                <Tooltip title={`AI Agent: ${persona.agentName}`}>
                  <Chip
                    icon={<AgentIcon className="text-sm" />}
                    label={persona.agentName}
                    size="small"
                    variant="outlined"
                    className="h-[20px] text-[0.65rem] bg-[rgba(99,_102,_241,_0.1)] border-[rgba(99,_102,_241,_0.3)] ml-1 mr-[-2px]" />
                </Tooltip>
              )}
              
              {!persona.isVirtual && (
                <Tooltip title="You">
                  <HumanIcon className="text-gray-500 dark:text-gray-400 text-base" />
                </Tooltip>
              )}
            </Box>

            {/* Actions (expanded) */}
            {expandedPersona === persona.personaId && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap" className="pl-8 pt-1">
                {persona.actions.map((action) => (
                  <Chip
                    key={action.action}
                    label={`${action.icon} ${action.label}`}
                    size="small"
                    clickable
                    onClick={() => handleActionClick(action.action, action.mode)}
                    className="mb-1 text-[0.7rem] h-[24px]" style={{ backgroundColor: action.mode === currentMode 
                        ? 'primary.main' 
                        : 'action.selected', color: action.mode === currentMode 
                        ? 'primary.contrastText' 
                        : 'text.primary' }}
                  />
                ))}
              </Stack>
            )}
          </Box>
        ))}
      </Stack>
    </Paper>
  );
};

export default PersonaQuickActions;
