import React, { useMemo, useRef, useCallback } from 'react';
import { FileText as Description } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useI18n } from '../../i18n/I18nProvider';

/**
 * Zoomable Lifecycle Zones component.
 *
 * Renders lifecycle phases as spatial zones on the canvas.
 * Zoom level determines visibility and detail level.
 *
 * @doc.type component
 * @doc.purpose Lifecycle phase visualization on canvas
 * @doc.layer ui
 */

export type LifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'BUILD'
  | 'RUN'
  | 'IMPROVE';

export interface PhaseZone {
  phase: LifecyclePhase;
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
  status: 'pending' | 'active' | 'completed';
  progress: number;
  artifacts: number;
}

export interface ZoomableLifecycleZonesProps {
  zones: PhaseZone[];
  zoom: number;
  activePhase?: LifecyclePhase;
  onPhaseClick?: (phase: LifecyclePhase) => void;
  className?: string;
}

const PHASE_CONFIG: Record<
  LifecyclePhase,
  { label: string; color: string; description: string }
> = {
  INTENT: {
    label: 'INTENT',
    color: 'bg-info-bg dark:bg-info-bg/30 border-info-border',
    description: 'Define goals and requirements',
  },
  SHAPE: {
    label: 'SHAPE',
    color: 'bg-info-bg dark:bg-info-bg/30 border-info-border',
    description: 'Design architecture and components',
  },
  VALIDATE: {
    label: 'VALIDATE',
    color: 'bg-success-bg dark:bg-success-bg/30 border-success-border',
    description: 'Verify design and requirements',
  },
  GENERATE: {
    label: 'GENERATE',
    color: 'bg-warning-bg dark:bg-warning-bg/30 border-warning-border',
    description: 'Generate code and assets',
  },
  BUILD: {
    label: 'BUILD',
    color: 'bg-warning-bg dark:bg-warning-bg/30 border-warning-border',
    description: 'Build and compile',
  },
  RUN: {
    label: 'RUN',
    color: 'bg-destructive-bg dark:bg-destructive-bg/30 border-destructive-border',
    description: 'Execute and test',
  },
  IMPROVE: {
    label: 'IMPROVE',
    color: 'bg-info-bg dark:bg-info-bg/30 border-info-border',
    description: 'Refine and optimize',
  },
};

const PhaseZoneCard = React.memo(function PhaseZoneCard({
  zone,
  showDetails,
  showArtifacts,
  isActive,
  onPhaseClick,
}: {
  zone: PhaseZone;
  showDetails: boolean;
  showArtifacts: boolean;
  isActive: boolean;
  onPhaseClick?: (phase: LifecyclePhase) => void;
}) {
  const config = PHASE_CONFIG[zone.phase] ?? {
    label: zone.phase,
    color: 'bg-surface-muted dark:bg-surface/30 border-border',
    description: 'Lifecycle phase',
  };

  return (
    <div
      className={cn(
        'absolute rounded-lg border-2 transition-all duration-300',
        'cursor-pointer hover:shadow-lg',
        config.color,
        isActive && 'ring-4 ring-blue-500 ring-opacity-50',
        zone.status === 'completed' && 'opacity-60',
        zone.status === 'active' && 'shadow-xl'
      )}
      style={{
        left: `${zone.x}px`,
        top: `${zone.y}px`,
        width: `${zone.width}px`,
        height: `${zone.height}px`,
      }}
      tabIndex={0}
      role="button"
      aria-label={`Lifecycle phase: ${config.label}`}
      onClick={() => onPhaseClick?.(zone.phase)}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onPhaseClick?.(zone.phase); }}
    >
      <div className="p-4 h-full flex flex-col">
        {/* Phase Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-bold text-text-primary" tabIndex={0}>
            {config.label}
          </h3>
          {zone.status === 'completed' && (
            <span className="text-success-color dark:text-success-color">✓</span>
          )}
          {zone.status === 'active' && (
            <span className="text-info-color dark:text-info-color animate-pulse">
              ●
            </span>
          )}
        </div>

        {/* Description (visible at medium zoom) */}
        {showDetails && (
          <p className="text-sm text-text-secondary mb-3">
            {config.description}
          </p>
        )}

        {/* Progress Bar */}
        <div className="mb-3">
          <div className="flex items-center justify-between text-xs text-text-secondary mb-1">
            <span>Progress</span>
            <span>{Math.round(zone.progress)}%</span>
          </div>
          <div className="h-2 bg-surface-muted dark:bg-surface-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-info-bg transition-all duration-300"
              style={{ width: `${zone.progress}%` }}
            />
          </div>
        </div>

        {/* Artifacts Count (visible at high zoom) */}
        {showArtifacts && (
          <div className="mt-auto">
            <div className="flex items-center gap-2 text-sm text-text-secondary">
              <Description size={16} />
              <span>{zone.artifacts} artifacts</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

const MAX_RENDERED_ZONES = 100;

export function ZoomableLifecycleZones({
  zones,
  zoom,
  activePhase,
  onPhaseClick,
  className,
}: ZoomableLifecycleZonesProps) {
  const { t } = useI18n();
  // Stable callback ref to avoid re-creating click handlers each render
  const onPhaseClickRef = useRef(onPhaseClick);
  onPhaseClickRef.current = onPhaseClick;
  const handlePhaseClick = useCallback((phase: LifecyclePhase) => {
    onPhaseClickRef.current?.(phase);
  }, []);

  const cappedZones = useMemo(() =>
    zones.length > MAX_RENDERED_ZONES ? zones.slice(0, MAX_RENDERED_ZONES) : zones,
  [zones]);

  // Use ref for activePhase in useMemo to avoid recomputing visibleZones on every activePhase change
  const activePhaseRef = useRef(activePhase);
  activePhaseRef.current = activePhase;

  // Only recompute when zoom crosses the 0.3 threshold (not on every zoom/activePhase change)
  const isLowZoom = zoom < 0.3;
  const visibleZones = useMemo(() => {
    if (isLowZoom) {
      const activeIndex = cappedZones.findIndex((z) => z.phase === activePhaseRef.current);
      if (activeIndex === -1) return cappedZones;
      return cappedZones.filter((_, index) => Math.abs(index - activeIndex) <= 1);
    }
    return cappedZones;
  // eslint-disable-next-line react-hooks/exhaustive-deps -- activePhase used via ref to avoid unnecessary recomputes
  }, [cappedZones, isLowZoom]);

  // Stable boolean flags - only change when zoom crosses meaningful thresholds
  const showDetails = zoom > 0.5;
  const showArtifacts = zoom > 0.75;

  const connectionLines = useMemo(() =>
    visibleZones.slice(0, -1).map((zone, index) => {
      const nextZone = visibleZones[index + 1];
      if (!nextZone) return null;
      const startX = zone.x + zone.width;
      const startY = zone.y + zone.height / 2;
      const endX = nextZone.x;
      const endY = nextZone.y + nextZone.height / 2;
      return (
        <g key={`${zone.phase}-${nextZone.phase}`}>
          <line x1={startX} y1={startY} x2={endX} y2={endY}
            stroke="currentColor" strokeWidth="2" strokeDasharray="5,5"
            className="text-fg-muted dark:text-fg" />
          <polygon
            points={`${endX},${endY} ${endX - 10},${endY - 5} ${endX - 10},${endY + 5}`}
            fill="currentColor" className="text-fg-muted dark:text-fg" />
        </g>
      );
    }),
  [visibleZones]);

  return (
    <div
      data-testid="lifecycle-zones"
      aria-label={t('canvas.zoomableLifecycleZones.phases')}
      className={cn('relative w-full h-full', className)}
    >
      {/* Connection Lines */}
      <svg className="absolute inset-0 pointer-events-none" style={{ zIndex: 0 }}>
        {connectionLines}
      </svg>

      {/* Phase Zones */}
      {visibleZones.map((zone) => (
        <PhaseZoneCard
          key={zone.phase}
          zone={zone}
          showDetails={showDetails}
          showArtifacts={showArtifacts}
          isActive={zone.phase === activePhase}
          onPhaseClick={handlePhaseClick}
        />
      ))}
    </div>
  );
}

/**
 * Hook for managing lifecycle zones layout.
 *
 * @doc.type hook
 * @doc.purpose Calculate zone positions and sizes
 */
export function useLifecycleZones(
  canvasWidth: number,
  canvasHeight: number,
  phases: LifecyclePhase[]
): PhaseZone[] {
  return useMemo(() => {
    const zoneWidth = 300;
    const zoneHeight = 200;
    const horizontalGap = 150;
    const verticalOffset = 100;

    return phases.map((phase, index) => ({
      phase,
      x: index * (zoneWidth + horizontalGap) + 50,
      y: verticalOffset + (index % 2) * 100,
      width: zoneWidth,
      height: zoneHeight,
      color: PHASE_CONFIG[phase].color,
      status: index === 0 ? 'completed' : index === 1 ? 'active' : 'pending',
      progress: index === 0 ? 100 : index === 1 ? 65 : 0,
      artifacts: Math.floor(Math.random() * 10) + 1,
    }));
  }, [canvasWidth, canvasHeight, phases]);
}
