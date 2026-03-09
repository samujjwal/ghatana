/**
 * Canvas Quick Actions FAB
 * 
 * Floating Action Button with speed dial for quick component additions.
 * Provides one-click access to frequently used components.
 * 
 * @doc.type component
 * @doc.purpose Quick access to common canvas components
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React, { useState } from 'react';
import { Box } from '@ghatana/ui';
import { SpeedDial, SpeedDialAction, SpeedDialIcon } from '@ghatana/ui';
import { Plus as AddIcon, Plug as ApiIcon, HardDrive as DatabaseIcon, Monitor as FrontendIcon, Sparkles as AIIcon, Building2 as ArchitectureIcon, Cable as IntegrationIcon } from 'lucide-react';

import { Z_INDEX } from '../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface QuickAction {
  id: string;
  icon: React.ReactNode;
  name: string;
  onClick: () => void;
  color?: string;
}

export interface CanvasQuickActionsProps {
  /** Callback when user wants to add a specific component type */
  onAddComponent?: (componentType: string) => void;
  /** Callback when AI assist is triggered */
  onAIAssist?: () => void;
  /** Whether the speed dial is open by default */
  defaultOpen?: boolean;
  /** Hide FAB when canvas is empty */
  hideWhenEmpty?: boolean;
  /** Number of nodes on canvas */
  nodeCount?: number;
  /** Additional CSS classes */
  className?: string;
}

// ============================================================================
// Main Component
// ============================================================================

export const CanvasQuickActions: React.FC<CanvasQuickActionsProps> = ({
  onAddComponent,
  onAIAssist,
  defaultOpen = false,
  hideWhenEmpty = false,
  nodeCount = 0,
  className = '',
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [open, setOpen] = useState(defaultOpen);

  // Hide FAB when canvas is empty (empty state handles initial actions)
  if (hideWhenEmpty && nodeCount === 0) {
    return null;
  }

  // Define quick actions
  const actions: QuickAction[] = [
    {
      id: 'add-api',
      icon: <ApiIcon />,
      name: 'Add API',
      onClick: () => onAddComponent?.('api'),
      color: theme.palette.primary.main,
    },
    {
      id: 'add-database',
      icon: <DatabaseIcon />,
      name: 'Add Database',
      onClick: () => onAddComponent?.('database'),
      color: theme.palette.success.main,
    },
    {
      id: 'add-frontend',
      icon: <FrontendIcon />,
      name: 'Add Frontend',
      onClick: () => onAddComponent?.('frontend'),
      color: theme.palette.info.main,
    },
    {
      id: 'add-integration',
      icon: <IntegrationIcon />,
      name: 'Add Integration',
      onClick: () => onAddComponent?.('integration'),
      color: theme.palette.warning.main,
    },
    {
      id: 'architecture-suggest',
      icon: <ArchitectureIcon />,
      name: 'Architecture Template',
      onClick: () => onAddComponent?.('architecture-template'),
      color: theme.palette.secondary.main,
    },
    {
      id: 'ai-suggest',
      icon: <AIIcon />,
      name: 'AI Suggest',
      onClick: () => onAIAssist?.(),
      color: theme.palette.secondary.main,
    },
  ];

  return (
    <Box
      className={className}
       style={{ bottom: isMobile ? 80 : 24 }}
      data-testid="canvas-quick-actions"
    >
      <SpeedDial
        ariaLabel="Quick actions"
        icon={<SpeedDialIcon icon={<AddIcon />} />}
        onClose={() => setOpen(false)}
        onOpen={() => setOpen(true)}
        open={open}
        direction="up"
        className="[&_.MuiSpeedDial-fab]:bg-blue-600 [&_.MuiSpeedDial-fab:hover]:bg-blue-800 [&_.MuiSpeedDial-fab]:shadow-lg"
      >
        {actions.map((action) => (
          <SpeedDialAction
            key={action.id}
            icon={action.icon}
            tooltipTitle={action.name}
            tooltipOpen
            onClick={() => {
              action.onClick();
              setOpen(false);
            }}
            FabProps={{
              style: {
                backgroundColor: action.color || 'white',
                color: 'white',
              },
              className: 'hover:brightness-90',
            }}
            data-testid={`quick-action-${action.id}`}
          />
        ))}
      </SpeedDial>
    </Box>
  );
};

export default CanvasQuickActions;
