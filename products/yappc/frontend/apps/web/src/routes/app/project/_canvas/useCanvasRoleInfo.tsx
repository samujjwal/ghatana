/**
 * useCanvasRoleInfo Hook
 *
 * Derives persona/role and phase information for canvas header and left rail.
 *
 * @doc.type hook
 * @doc.purpose Canvas role and phase info derivation
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useMemo } from 'react';
import {
  Lightbulb as LightbulbOutlined,
  Network as Schema,
  Paintbrush as Brush,
  Building2 as Architecture,
  Code,
  ClipboardCheck as FactCheck,
  Eye as Visibility,
  Rocket as RocketLaunch,
  ClipboardList as Assignment,
} from 'lucide-react';
import type { CanvasMode } from '../../../../types/canvasMode';

interface RoleInfo {
  label: string;
  icon: React.ReactNode;
  color: string;
}

const ROLE_MAP: Record<string, RoleInfo> = {
  brainstorm: { label: 'Brainstormer', icon: <LightbulbOutlined size={16} />, color: '#ff6f00' },
  diagram: { label: 'Diagrammer', icon: <Schema size={16} />, color: '#7b1fa2' },
  design: { label: 'Designer', icon: <Brush size={16} />, color: '#8e24aa' },
  architecture: { label: 'Architect', icon: <Architecture size={16} />, color: '#1976d2' },
  code: { label: 'Developer', icon: <Code size={16} />, color: '#388e3c' },
  test: { label: 'QA Engineer', icon: <FactCheck size={16} />, color: '#d32f2f' },
  observe: { label: 'Observer', icon: <Visibility size={16} />, color: '#0097a7' },
  deploy: { label: 'DevOps', icon: <RocketLaunch size={16} />, color: '#d32f2f' },
  plan: { label: 'Product Owner', icon: <Assignment size={16} />, color: '#f57c00' },
};

export function useCanvasRoleInfo(currentMode: CanvasMode, project: unknown) {
  const roleInfo = useMemo(() => ROLE_MAP[currentMode], [currentMode]);

  const phaseInfo = useMemo(
    () =>
      project?.currentPhase
        ? {
            phase: project.currentPhase,
            label:
              project.currentPhase.charAt(0).toUpperCase() +
              project.currentPhase.slice(1),
            progress: project.phaseProgress || 0,
            status: 'active' as const,
          }
        : undefined,
    [project?.currentPhase, project?.phaseProgress]
  );

  return { roleInfo, phaseInfo, roleMap: ROLE_MAP };
}
