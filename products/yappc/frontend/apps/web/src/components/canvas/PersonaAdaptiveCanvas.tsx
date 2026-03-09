/**
 * Persona-Adaptive Canvas Component
 *
 * Adapts Canvas UI based on active persona, showing relevant tools,
 * panels, and actions specific to each role.
 *
 * @doc.type component
 * @doc.purpose Persona-specific Canvas customization
 * @doc.layer presentation
 * @doc.pattern Adapter Pattern
 */

import React, { useMemo, ReactNode } from 'react';
import { Box, Tooltip, IconButton } from '@ghatana/ui';
import { ClipboardList as Assignment, BarChart3 as BarChart, Bug as BugReport, CheckCircle, Code, ClipboardCheck as FactCheck, Flag, Folder, Image, Layers, ListOrdered as ListAlt, Lock, Map, Palette, Rocket as RocketLaunch, Search, Settings, Shield, Terminal, TrendingUp } from 'lucide-react';
import {
  usePersona,
  type PersonaType,
  PERSONA_DEFINITIONS,
} from '../../context/PersonaContext';
import { usePersonaURLState } from '../../hooks/useCanvasURLState';

/**
 * Persona-specific tool configuration
 */
interface PersonaToolConfig {
  id: string;
  label: string;
  icon: ReactNode;
  action: () => void;
  shortcut?: string;
  category: 'primary' | 'secondary' | 'advanced';
}

/**
 * Persona-specific panel configuration
 */
interface PersonaPanelConfig {
  id: string;
  label: string;
  icon: ReactNode;
  defaultOpen: boolean;
  position: 'left' | 'right' | 'bottom';
}

/**
 * Component props
 */
export interface PersonaAdaptiveCanvasProps {
  /** Canvas content */
  children: ReactNode;
  /** Project identifier */
  projectId: string;
  /** Custom tool configurations */
  customTools?: PersonaToolConfig[];
  /** Callback when persona changes */
  onPersonaChange?: (persona: PersonaType) => void;
  /** Whether to show persona indicator */
  showPersonaIndicator?: boolean;
}

/**
 * Get tools for specific persona
 */
function getPersonaTools(persona: PersonaType): PersonaToolConfig[] {
  const toolsByPersona: Record<PersonaType, PersonaToolConfig[]> = {
    'product-owner': [
      {
        id: 'define-requirements',
        label: 'Define Requirements',
        icon: <ListAlt size={16} />,
        action: () => console.log('Define requirements'),
        shortcut: 'Cmd+R',
        category: 'primary',
      },
      {
        id: 'prioritize-backlog',
        label: 'Prioritize Backlog',
        icon: <Flag size={16} />,
        action: () => console.log('Prioritize backlog'),
        shortcut: 'Cmd+P',
        category: 'primary',
      },
      {
        id: 'review-progress',
        label: 'Review Progress',
        icon: <BarChart size={16} />,
        action: () => console.log('Review progress'),
        category: 'secondary',
      },
      {
        id: 'accept-deliverable',
        label: 'Accept Deliverable',
        icon: <CheckCircle size={16} />,
        action: () => console.log('Accept deliverable'),
        category: 'primary',
      },
    ],
    developer: [
      {
        id: 'write-code',
        label: 'Write Code',
        icon: <Code size={16} />,
        action: () => console.log('Write code'),
        shortcut: 'Cmd+N',
        category: 'primary',
      },
      {
        id: 'run-tests',
        label: 'Run Tests',
        icon: <FactCheck size={16} />,
        action: () => console.log('Run tests'),
        shortcut: 'Cmd+T',
        category: 'primary',
      },
      {
        id: 'debug',
        label: 'Debug',
        icon: <BugReport size={16} />,
        action: () => console.log('Debug'),
        shortcut: 'Cmd+D',
        category: 'secondary',
      },
      {
        id: 'refactor',
        label: 'Refactor',
        icon: <Settings size={16} />,
        action: () => console.log('Refactor'),
        category: 'advanced',
      },
    ],
    designer: [
      {
        id: 'create-wireframe',
        label: 'Create Wireframe',
        icon: <Palette size={16} />,
        action: () => console.log('Create wireframe'),
        shortcut: 'Cmd+W',
        category: 'primary',
      },
      {
        id: 'design-component',
        label: 'Design Component',
        icon: <Palette size={16} />,
        action: () => console.log('Design component'),
        shortcut: 'Cmd+C',
        category: 'primary',
      },
      {
        id: 'create-prototype',
        label: 'Create Prototype',
        icon: <Image size={16} />,
        action: () => console.log('Create prototype'),
        category: 'secondary',
      },
      {
        id: 'design-system',
        label: 'Design System',
        icon: <Layers size={16} />,
        action: () => console.log('Design system'),
        category: 'advanced',
      },
    ],
    devops: [
      {
        id: 'configure-pipeline',
        label: 'Configure Pipeline',
        icon: <Settings size={16} />,
        action: () => console.log('Configure pipeline'),
        shortcut: 'Cmd+P',
        category: 'primary',
      },
      {
        id: 'deploy',
        label: 'Deploy',
        icon: <RocketLaunch size={16} />,
        action: () => console.log('Deploy'),
        shortcut: 'Cmd+D',
        category: 'primary',
      },
      {
        id: 'monitor',
        label: 'Monitor',
        icon: <TrendingUp size={16} />,
        action: () => console.log('Monitor'),
        category: 'secondary',
      },
      {
        id: 'scale',
        label: 'Scale Infrastructure',
        icon: <BarChart size={16} />,
        action: () => console.log('Scale'),
        category: 'advanced',
      },
    ],
    qa: [
      {
        id: 'create-test-plan',
        label: 'Create Test Plan',
        icon: <Assignment size={16} />,
        action: () => console.log('Create test plan'),
        shortcut: 'Cmd+T',
        category: 'primary',
      },
      {
        id: 'run-tests',
        label: 'Run Tests',
        icon: <FactCheck size={16} />,
        action: () => console.log('Run tests'),
        shortcut: 'Cmd+R',
        category: 'primary',
      },
      {
        id: 'report-bug',
        label: 'Report Bug',
        icon: <BugReport size={16} />,
        action: () => console.log('Report bug'),
        category: 'secondary',
      },
      {
        id: 'verify-fix',
        label: 'Verify Fix',
        icon: <CheckCircle size={16} />,
        action: () => console.log('Verify fix'),
        category: 'secondary',
      },
    ],
    security: [
      {
        id: 'security-audit',
        label: 'Security Audit',
        icon: <Shield size={16} />,
        action: () => console.log('Security audit'),
        shortcut: 'Cmd+S',
        category: 'primary',
      },
      {
        id: 'scan-vulnerabilities',
        label: 'Scan Vulnerabilities',
        icon: <Search size={16} />,
        action: () => console.log('Scan vulnerabilities'),
        shortcut: 'Cmd+V',
        category: 'primary',
      },
      {
        id: 'review-permissions',
        label: 'Review Permissions',
        icon: <Lock size={16} />,
        action: () => console.log('Review permissions'),
        category: 'secondary',
      },
      {
        id: 'compliance-check',
        label: 'Compliance Check',
        icon: <ListAlt size={16} />,
        action: () => console.log('Compliance check'),
        category: 'advanced',
      },
    ],
  };

  return toolsByPersona[persona] || [];
}

/**
 * Get panels for specific persona
 */
function getPersonaPanels(persona: PersonaType): PersonaPanelConfig[] {
  const panelsByPersona: Record<PersonaType, PersonaPanelConfig[]> = {
    'product-owner': [
      {
        id: 'backlog',
        label: 'Backlog',
        icon: <ListAlt size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'roadmap',
        label: 'Roadmap',
        icon: <Map size={16} />,
        defaultOpen: false,
        position: 'left',
      },
      {
        id: 'metrics',
        label: 'Metrics',
        icon: <BarChart size={16} />,
        defaultOpen: true,
        position: 'right',
      },
    ],
    developer: [
      {
        id: 'files',
        label: 'Files',
        icon: <Folder size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'console',
        label: 'Console',
        icon: <Terminal size={16} />,
        defaultOpen: true,
        position: 'bottom',
      },
      {
        id: 'tests',
        label: 'Tests',
        icon: <FactCheck size={16} />,
        defaultOpen: false,
        position: 'right',
      },
    ],
    designer: [
      {
        id: 'layers',
        label: 'Layers',
        icon: <Layers size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'properties',
        label: 'Properties',
        icon: <Settings size={16} />,
        defaultOpen: true,
        position: 'right',
      },
      {
        id: 'assets',
        label: 'Assets',
        icon: <Image size={16} />,
        defaultOpen: false,
        position: 'left',
      },
    ],
    devops: [
      {
        id: 'pipelines',
        label: 'Pipelines',
        icon: <Settings size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'logs',
        label: 'Logs',
        icon: <Assignment size={16} />,
        defaultOpen: true,
        position: 'bottom',
      },
      {
        id: 'monitoring',
        label: 'Monitoring',
        icon: <TrendingUp size={16} />,
        defaultOpen: true,
        position: 'right',
      },
    ],
    qa: [
      {
        id: 'test-cases',
        label: 'Test Cases',
        icon: <Assignment size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'test-results',
        label: 'Results',
        icon: <CheckCircle size={16} />,
        defaultOpen: true,
        position: 'right',
      },
      {
        id: 'bugs',
        label: 'Bugs',
        icon: <BugReport size={16} />,
        defaultOpen: false,
        position: 'bottom',
      },
    ],
    security: [
      {
        id: 'vulnerabilities',
        label: 'Vulnerabilities',
        icon: <Search size={16} />,
        defaultOpen: true,
        position: 'left',
      },
      {
        id: 'audit-log',
        label: 'Audit Log',
        icon: <ListAlt size={16} />,
        defaultOpen: true,
        position: 'right',
      },
      {
        id: 'compliance',
        label: 'Compliance',
        icon: <Shield size={16} />,
        defaultOpen: false,
        position: 'bottom',
      },
    ],
  };

  return panelsByPersona[persona] || [];
}

/**
 * Persona-Adaptive Canvas Component
 */
export const PersonaAdaptiveCanvas: React.FC<PersonaAdaptiveCanvasProps> = ({
  children,
  customTools = [],
  onPersonaChange,
  showPersonaIndicator = true,
}) => {
  const { primaryPersona, setPrimaryPersona } = usePersona();
  const { persona: urlPersona } = usePersonaURLState();

  // Sync URL persona with context
  React.useEffect(() => {
    if (urlPersona && urlPersona !== primaryPersona) {
      setPrimaryPersona(urlPersona);
      if (onPersonaChange) {
        onPersonaChange(urlPersona);
      }
    }
  }, [urlPersona, primaryPersona, setPrimaryPersona, onPersonaChange]);

  // Get persona-specific configuration
  const personaConfig = useMemo(() => {
    if (!primaryPersona) return null;
    return PERSONA_DEFINITIONS[primaryPersona];
  }, [primaryPersona]);

  const personaTools = useMemo(() => {
    if (!primaryPersona) return [];
    return [...getPersonaTools(primaryPersona), ...customTools];
  }, [primaryPersona, customTools]);

  const personaPanels = useMemo(() => {
    if (!primaryPersona) return [];
    return getPersonaPanels(primaryPersona);
  }, [primaryPersona]);

  const personaIconMap = useMemo(
    () => ({
      'product-owner': <ListAlt size={16} />,
      developer: <Code size={16} />,
      designer: <Palette size={16} />,
      devops: <RocketLaunch size={16} />,
      qa: <FactCheck size={16} />,
      security: <Shield size={16} />,
    }),
    []
  );

  if (!primaryPersona || !personaConfig) {
    return <>{children}</>;
  }

  return (
    <Box
      className="relative w-full h-full"
      data-persona={primaryPersona}
    >
      {/* Persona Indicator */}
      {showPersonaIndicator && (
        <Box
          className="absolute flex items-center gap-2 px-4 py-2 rounded-lg top-[16px] right-[16px] z-[1000] bg-white dark:bg-gray-900 shadow border-[2px_solid]" style={{ borderColor: 'personaConfig.color' }} >
          <Box component="span" className="flex items-center">
            {personaIconMap[primaryPersona]}
          </Box>
          <Box>
            <Box className="font-semibold text-sm">
              {personaConfig.name}
            </Box>
            <Box className="text-xs text-gray-500 dark:text-gray-400">
              {personaConfig.shortName}
            </Box>
          </Box>
        </Box>
      )}

      {/* Persona-Specific Toolbar */}
      <Box
        className="absolute flex flex-col gap-2 p-2 rounded-lg top-[80px] right-[16px] z-[999] bg-white dark:bg-gray-900 shadow"
      >
        {personaTools
          .filter((tool) => tool.category === 'primary')
          .map((tool) => (
            <Tooltip
              key={tool.id}
              title={`${tool.label}${tool.shortcut ? ` (${tool.shortcut})` : ''}`}
              placement="left"
            >
              <IconButton
                onClick={tool.action}
                size="small"
                className="text-[20px] hover:bg-gray-100 hover:dark:bg-gray-800"
              >
                {tool.icon}
              </IconButton>
            </Tooltip>
          ))}
      </Box>

      {/* Canvas Content */}
      <Box
        className="w-full h-full"
      >
        {children}
      </Box>

      {/* Persona Context Data (for debugging) */}
      {process.env.NODE_ENV === 'development' && (
        <Box
          className="absolute p-2 rounded text-xs bottom-[16px] left-[16px] z-[999] bg-white dark:bg-gray-900 shadow-sm font-mono opacity-[0.7]"
        >
          <div>Persona: {primaryPersona}</div>
          <div>Tools: {personaTools.length}</div>
          <div>Panels: {personaPanels.length}</div>
        </Box>
      )}
    </Box>
  );
};

/**
 * Hook to access persona-adaptive canvas context
 */
export function usePersonaAdaptiveCanvas() {
  const { primaryPersona } = usePersona();
  const { persona: urlPersona } = usePersonaURLState();

  const tools = useMemo(() => {
    if (!primaryPersona) return [];
    return getPersonaTools(primaryPersona);
  }, [primaryPersona]);

  const panels = useMemo(() => {
    if (!primaryPersona) return [];
    return getPersonaPanels(primaryPersona);
  }, [primaryPersona]);

  return {
    activePersona: primaryPersona || urlPersona,
    tools,
    panels,
    hasPersona: !!(primaryPersona || urlPersona),
  };
}
