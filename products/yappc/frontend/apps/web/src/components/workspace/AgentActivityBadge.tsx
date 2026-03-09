/**
 * AgentActivityBadge Component
 * 
 * @doc.type class
 * @doc.purpose Show active AI agent count in sidebar/header
 * @doc.layer product
 * @doc.pattern Component
 * 
 * Displays a badge indicating how many virtual personas (AI agents)
 * are currently filling roles in the workspace.
 */

import { Bot as AgentIcon, Circle as StatusIcon } from 'lucide-react';
import {
  Box,
  Tooltip,
  Typography,
  Badge,
} from '@ghatana/ui';
import React, { useState, useCallback } from 'react';

import { usePersona, PERSONA_DEFINITIONS } from '../../context/PersonaContext';

import type { PersonaType } from '../../context/PersonaContext';

export interface AgentActivityBadgeProps {
  /** Size variant */
  size?: 'small' | 'medium' | 'large';
  /** Show tooltip with agent details */
  showTooltip?: boolean;
  /** Show expanded view on click */
  expandable?: boolean;
  /** Callback when badge is clicked */
  onClick?: () => void;
}

/**
 * Agent activity indicator sizes
 */
const SIZE_CONFIG = {
  small: {
    iconSize: 16,
    badgeSize: 8,
    containerSize: 24,
    fontSize: '0.65rem',
  },
  medium: {
    iconSize: 20,
    badgeSize: 10,
    containerSize: 32,
    fontSize: '0.75rem',
  },
  large: {
    iconSize: 24,
    badgeSize: 12,
    containerSize: 40,
    fontSize: '0.85rem',
  },
};

/**
 * AgentActivityBadge - Shows active AI agent count
 */
export const AgentActivityBadge: React.FC<AgentActivityBadgeProps> = ({
  size = 'medium',
  showTooltip = true,
  expandable = true,
  onClick,
}) => {
  const { virtualPersonas, getPersonaDefinition } = usePersona();
  const [isExpanded, setIsExpanded] = useState(false);
  
  const sizeConfig = SIZE_CONFIG[size];
  const activeAgentCount = virtualPersonas.length;

  const handleClick = useCallback(() => {
    if (expandable) {
      setIsExpanded(!isExpanded);
    }
    onClick?.();
  }, [expandable, isExpanded, onClick]);

  // Don't show if no agents are active
  if (activeAgentCount === 0) {
    return null;
  }

  const tooltipContent = (
    <Box>
      <Typography variant="caption" fontWeight={600}>
        Active AI Agents ({activeAgentCount})
      </Typography>
      <Box component="ul" className="m-0 pl-4 mt-1">
        {virtualPersonas.map(personaId => {
          const definition = getPersonaDefinition(personaId);
          return (
            <Typography 
              key={personaId} 
              component="li" 
              variant="caption"
              className="text-inherit"
            >
              {definition.icon} {definition.name}
            </Typography>
          );
        })}
      </Box>
    </Box>
  );

  const badge = (
    <Box
      onClick={handleClick}
      className="flex items-center gap-1 px-2 py-1 rounded-lg bg-[rgba(99,_102,_241,_0.1)] border border-solid border-[rgba(99,_102,_241,_0.2)] transition-all duration-200 hover:bg-[rgba(99,_102,_241,_0.15)] hover:border-[rgba(99,_102,_241,_0.3)]" style={{ cursor: expandable || onClick ? 'pointer' : 'default' }}
    >
      <Badge
        badgeContent={
          <StatusIcon 
            className="text-[#22c55e]" style={{ fontSize: sizeConfig.badgeSize }} 
          />
        }
        overlap="circular"
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
      >
        <AgentIcon 
          className="text-[#6366f1]" style={{ fontSize: sizeConfig.iconSize }} 
        />
      </Badge>
      
      <Typography 
        variant="caption" 
        fontWeight={600}
        className="text-[#6366f1]" style={{ fontSize: sizeConfig.fontSize }}
      >
        {activeAgentCount} AI
      </Typography>
    </Box>
  );

  if (showTooltip && !isExpanded) {
    return (
      <Tooltip title={tooltipContent} arrow placement="bottom">
        {badge}
      </Tooltip>
    );
  }

  return (
    <Box className="relative">
      {badge}
      
      {/* Expanded panel */}
      {isExpanded && (
        <Box
          className="absolute mt-2 p-3 rounded-lg top-[100%] right-[0px] min-w-[200px] bg-white dark:bg-gray-900 shadow-md z-[1000]"
        >
          <Typography variant="subtitle2" gutterBottom>
            Active AI Agents
          </Typography>
          
          {virtualPersonas.map(personaId => {
            const definition = getPersonaDefinition(personaId);
            return (
              <Box
                key={personaId}
                className="flex items-center gap-2 py-1 px-2 rounded hover:bg-gray-100"
              >
                <Typography className="text-base">
                  {definition.icon}
                </Typography>
                <Box className="flex-1">
                  <Typography variant="body2" fontWeight={500}>
                    {definition.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    AI Agent Active
                  </Typography>
                </Box>
                <StatusIcon 
                  className="text-[8px] text-[#22c55e]" 
                />
              </Box>
            );
          })}
          
          <Typography 
            variant="caption" 
            color="text.secondary"
            className="block mt-2 text-center"
          >
            AI agents fill unfilled persona roles
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default AgentActivityBadge;
