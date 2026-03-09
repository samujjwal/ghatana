/**
 * Spatial Zone Layout Component
 * 
 * Renders lifecycle phase zones as colored background regions on the canvas.
 * Zones are spatial areas that represent the journey from idea to enhancement.
 * 
 * @doc.type component
 * @doc.purpose Visual lifecycle phase zones
 * @doc.layer product
 * @doc.pattern Canvas Background Layer
 */

import React from 'react';
import { Box, Typography } from '@ghatana/ui';
import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_COLORS, PHASE_LABELS, PHASE_ICONS } from '../../../styles/design-tokens';

// Zone configuration
export interface PhaseZone {
    phase: LifecyclePhase;
    name: string;
    icon: string;
    color: string;
    x: number;        // Starting x position in pixels
    width: number;    // Width in pixels
}

// Colors, labels, and icons now from design tokens
const DEFAULT_ZONES: PhaseZone[] = [
    {
        phase: LifecyclePhase.INTENT,
        name: PHASE_LABELS.INTENT,
        icon: PHASE_ICONS.INTENT,
        color: PHASE_COLORS.INTENT.background,
        x: 0,
        width: 800,
    },
    {
        phase: LifecyclePhase.SHAPE,
        name: PHASE_LABELS.SHAPE,
        icon: PHASE_ICONS.SHAPE,
        color: PHASE_COLORS.SHAPE.background,
        x: 800,
        width: 900,
    },
    {
        phase: LifecyclePhase.VALIDATE,
        name: PHASE_LABELS.VALIDATE,
        icon: PHASE_ICONS.VALIDATE,
        color: PHASE_COLORS.VALIDATE.background,
        x: 1700,
        width: 1000,
    },
    {
        phase: LifecyclePhase.GENERATE,
        name: PHASE_LABELS.GENERATE,
        icon: PHASE_ICONS.GENERATE,
        color: PHASE_COLORS.GENERATE.background,
        x: 2700,
        width: 800,
    },
    {
        phase: LifecyclePhase.RUN,
        name: PHASE_LABELS.RUN,
        icon: PHASE_ICONS.RUN,
        color: PHASE_COLORS.RUN.background,
        x: 3500,
        width: 700,
    },
    {
        phase: LifecyclePhase.OBSERVE,
        name: PHASE_LABELS.OBSERVE,
        icon: PHASE_ICONS.OBSERVE,
        color: PHASE_COLORS.OBSERVE.background,
        x: 4200,
        width: 700,
    },
    {
        phase: LifecyclePhase.IMPROVE,
        name: PHASE_LABELS.IMPROVE,
        icon: PHASE_ICONS.IMPROVE,
        color: PHASE_COLORS.IMPROVE.background,
        x: 4900,
        width: 800,
    },
];

export interface SpatialZonesProps {
    zones?: PhaseZone[];
    currentPhase?: LifecyclePhase;
    viewportHeight?: number;
    onZoneClick?: (phase: LifecyclePhase) => void;
}

/**
 * SpatialZones - Background layer showing lifecycle phase zones
 * 
 * Renders as absolutely positioned background elements that provide
 * spatial context for the lifecycle journey.
 */
export const SpatialZones: React.FC<SpatialZonesProps> = ({
    zones = DEFAULT_ZONES,
    currentPhase,
    viewportHeight = 2000,
    onZoneClick,
}) => {
    const currentIndex = currentPhase == null
        ? -1
        : zones.findIndex((zone) => zone.phase === currentPhase);
    const progressPercent = currentIndex >= 0
        ? ((currentIndex + 1) / zones.length) * 100
        : 0;

    return (
        <Box
            className="absolute w-full pointer-events-none top-[0px] left-[0px] z-0"
            style={{ height: viewportHeight }}
        >
            {zones.map((zone) => {
                const isActive = currentPhase === zone.phase;

                return (
                    <Box
                        key={zone.phase}
                        onClick={() => onZoneClick?.(zone.phase)}
                        className="absolute top-[0px] h-full" style={{ left: zone.x, width: zone.width, filter: 'grayscale(100%)' }}
                    >
                        {/* Large Watermark Icon in Background */}
                        <Box className="absolute pointer-events-none top-[10%] right-[5%] text-[24rem] opacity-[0.03] select-none rotate-[-10deg]" >
                            {/* We just render the icon text representation if it's a string, or rely on distinct icons per phase */}
                            {zone.name.charAt(0)}
                        </Box>

                        {/* Zone Label */}
                        <Box
                            onClick={(e) => {
                                console.log('Zone label clicked:', zone.phase);
                                e.stopPropagation();
                                onZoneClick?.(zone.phase);
                            }}
                            className="sticky top-[120px] left-[24px] inline-flex flex-col items-center gap-2 p-4"
                            style={{ backgroundColor: isActive ? 'rgba(255, 255, 255, 0.92)' : 'transparent' }}
                        >
                            <Typography as="h3" component="div">
                                {zone.icon}
                            </Typography>
                            <Typography
                                as="span"
                                fontWeight="bold"
                                color="text.secondary"
                                className="text-xs text-gray-500 uppercase tracking-[1.5px]"
                            >
                                {zone.name}
                            </Typography>
                            {isActive && (
                                <Typography
                                    as="span"
                                    tone="primary"
                                    fontWeight="bold"
                                    className="text-xs text-gray-500 text-[0.65rem]"
                                >
                                    ◀ CURRENT
                                </Typography>
                            )}
                        </Box>
                    </Box>
                );
            })}

            {/* Progress Arrow */}
            {currentPhase !== undefined && (
                <Box
                    className="absolute top-[60px] left-[0px] w-full h-[4px]"
                    style={{
                        background: `linear-gradient(to right, rgba(59, 130, 246, 0.6) ${progressPercent}%, rgba(148, 163, 184, 0.25) ${progressPercent}%)`,
                    }}
                />
            )}
        </Box>
    );
};

/**
 * Hook to calculate zone boundaries for artifact placement
 */
export function useZoneBoundaries(zones: PhaseZone[] = DEFAULT_ZONES) {
    return React.useMemo(() => {
        return zones.reduce((acc, zone) => {
            acc[zone.phase] = {
                start: zone.x,
                end: zone.x + zone.width,
                center: zone.x + zone.width / 2,
                zone,
            };
            return acc;
        }, {} as Record<LifecyclePhase, { start: number; end: number; center: number; zone: PhaseZone }>);
    }, [zones]);
}

/**
 * Calculate ideal position for new artifact in a zone
 */
export function getZonePlacementPosition(
    phase: LifecyclePhase,
    existingCount: number,
    zones: PhaseZone[] = DEFAULT_ZONES
): { x: number; y: number } {
    const zone = zones.find(z => z.phase === phase);
    if (!zone) return { x: 0, y: 100 };

    // Calculate grid position within zone
    const padding = 80;
    const itemWidth = 320;
    const itemHeight = 240;
    const availableWidth = zone.width - padding * 2;
    const columns = Math.max(1, Math.floor(availableWidth / itemWidth));

    const row = Math.floor(existingCount / columns);
    const col = existingCount % columns;

    return {
        x: zone.x + padding + col * itemWidth,
        y: 200 + row * itemHeight,
    };
}
