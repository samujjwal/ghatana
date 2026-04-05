import React, { useMemo } from 'react';
import { FileText as Description } from 'lucide-react';
import { cn } from '@/lib/utils';

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
    label: 'Intent',
    color: 'bg-purple-100 dark:bg-purple-900/30 border-purple-500',
    description: 'Define goals and requirements',
  },
  SHAPE: {
    label: 'Shape',
    color: 'bg-blue-100 dark:bg-blue-900/30 border-blue-500',
    description: 'Design architecture and components',
  },
  VALIDATE: {
    label: 'Validate',
    color: 'bg-green-100 dark:bg-green-900/30 border-green-500',
    description: 'Verify design and requirements',
  },
  GENERATE: {
    label: 'Generate',
    color: 'bg-yellow-100 dark:bg-yellow-900/30 border-yellow-500',
    description: 'Generate code and assets',
  },
  BUILD: {
    label: 'Build',
    color: 'bg-orange-100 dark:bg-orange-900/30 border-orange-500',
    description: 'Build and compile',
  },
  RUN: {
    label: 'Run',
    color: 'bg-red-100 dark:bg-red-900/30 border-red-500',
    description: 'Execute and test',
  },
  IMPROVE: {
    label: 'Improve',
    color: 'bg-pink-100 dark:bg-pink-900/30 border-pink-500',
    description: 'Refine and optimize',
  },
};

function PhaseZoneCard({
  zone,
  zoom,
  isActive,
  onClick,
}: {
  zone: PhaseZone;
  zoom: number;
  isActive: boolean;
  onClick?: () => void;
}) {
  const config = PHASE_CONFIG[zone.phase];
  const showDetails = zoom > 0.5;
  const showArtifacts = zoom > 0.75;

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
      onClick={onClick}
    >
      <div className="p-4 h-full flex flex-col">
        {/* Phase Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-bold text-text-primary">
            {config.label}
          </h3>
          {zone.status === 'completed' && (
            <span className="text-green-600 dark:text-green-400">✓</span>
          )}
          {zone.status === 'active' && (
            <span className="text-blue-600 dark:text-blue-400 animate-pulse">
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
          <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 transition-all duration-300"
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
}

export function ZoomableLifecycleZones({
  zones,
  zoom,
  activePhase,
  onPhaseClick,
  className,
}: ZoomableLifecycleZonesProps) {
  const visibleZones = useMemo(() => {
    // At low zoom, show only active and adjacent phases
    if (zoom < 0.3) {
      const activeIndex = zones.findIndex((z) => z.phase === activePhase);
      if (activeIndex === -1) return zones;

      return zones.filter((_, index) => Math.abs(index - activeIndex) <= 1);
    }
    return zones;
  }, [zones, zoom, activePhase]);

  return (
    <div className={cn('relative w-full h-full', className)}>
      {/* Connection Lines */}
      <svg
        className="absolute inset-0 pointer-events-none"
        style={{ zIndex: 0 }}
      >
        {visibleZones.slice(0, -1).map((zone, index) => {
          const nextZone = visibleZones[index + 1];
          if (!nextZone) return null;

          const startX = zone.x + zone.width;
          const startY = zone.y + zone.height / 2;
          const endX = nextZone.x;
          const endY = nextZone.y + nextZone.height / 2;

          return (
            <g key={`${zone.phase}-${nextZone.phase}`}>
              <line
                x1={startX}
                y1={startY}
                x2={endX}
                y2={endY}
                stroke="currentColor"
                strokeWidth="2"
                strokeDasharray="5,5"
                className="text-gray-300 dark:text-gray-700"
              />
              <polygon
                points={`${endX},${endY} ${endX - 10},${endY - 5} ${endX - 10},${endY + 5}`}
                fill="currentColor"
                className="text-gray-300 dark:text-gray-700"
              />
            </g>
          );
        })}
      </svg>

      {/* Phase Zones */}
      {visibleZones.map((zone) => (
        <PhaseZoneCard
          key={zone.phase}
          zone={zone}
          zoom={zoom}
          isActive={zone.phase === activePhase}
          onClick={() => onPhaseClick?.(zone.phase)}
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
