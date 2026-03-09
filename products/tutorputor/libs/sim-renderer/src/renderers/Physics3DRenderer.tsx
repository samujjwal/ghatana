/**
 * Physics 3D Renderer - React Three Fiber Integration
 *
 * @doc.type module
 * @doc.purpose Render physics simulation entities in 3D using React Three Fiber
 * @doc.layer product
 * @doc.pattern Renderer
 */

import React, { useRef, useMemo, useEffect, useState } from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import {
    OrbitControls,
    PerspectiveCamera,
    Environment,
    Grid,
    Html,
    Line,
    Trail,
    Float,
} from "@react-three/drei";
import * as THREE from "three";
import type {
    PhysicsBodyEntity,
    PhysicsSpringEntity,
    PhysicsVectorEntity,
    PhysicsParticleEntity,
    SimEntityBase,
} from "@ghatana/tutorputor-contracts/v1/simulation";

// =============================================================================
// Types
// =============================================================================

export interface Physics3DRendererProps {
    entities: Map<string, SimEntityBase>;
    selectedEntityId?: string;
    hoveredEntityId?: string;
    onEntityClick?: (entityId: string) => void;
    onEntityHover?: (entityId: string | null) => void;
    showGrid?: boolean;
    showAxes?: boolean;
    showLabels?: boolean;
    showTrails?: boolean;
    cameraPosition?: [number, number, number];
    ambientIntensity?: number;
    theme?: Physics3DTheme;
}

export interface Physics3DTheme {
    primary: string;
    secondary: string;
    success: string;
    warning: string;
    danger: string;
    background: string;
    grid: string;
}

const DEFAULT_THEME: Physics3DTheme = {
    primary: "#3b82f6",
    secondary: "#8b5cf6",
    success: "#22c55e",
    warning: "#f59e0b",
    danger: "#ef4444",
    background: "#0f172a",
    grid: "#334155",
};

// =============================================================================
// Utility Hooks
// =============================================================================

function useEntityColor(
    entity: SimEntityBase,
    theme: Physics3DTheme,
    isHovered: boolean,
    isSelected: boolean
): THREE.Color {
    return useMemo(() => {
        const baseColor = entity.color ?? theme.primary;
        const color = new THREE.Color(baseColor);

        if (isSelected) {
            color.multiplyScalar(1.3);
        } else if (isHovered) {
            color.multiplyScalar(1.15);
        }

        return color;
    }, [entity.color, theme.primary, isHovered, isSelected]);
}

// =============================================================================
// Rigid Body Component
// =============================================================================

interface RigidBody3DProps {
    entity: PhysicsBodyEntity;
    isHovered: boolean;
    isSelected: boolean;
    theme: Physics3DTheme;
    showLabels: boolean;
    showTrails: boolean;
    onClick?: () => void;
    onHover?: (hovered: boolean) => void;
}

function RigidBody3D({
    entity,
    isHovered,
    isSelected,
    theme,
    showLabels,
    showTrails,
    onClick,
    onHover,
}: RigidBody3DProps): React.ReactElement {
    const meshRef = useRef<THREE.Mesh>(null);
    const color = useEntityColor(entity, theme, isHovered, isSelected);
    const scale = entity.scale ?? 1;

    // Animate rotation if body is rotating
    useFrame((_state, delta) => {
        const entityExt = entity as PhysicsBodyEntity & { angularVelocity?: number };
        if (meshRef.current && entityExt.angularVelocity) {
            meshRef.current.rotation.z += entityExt.angularVelocity * delta;
        }
    });

    const position: [number, number, number] = [
        entity.x / 50,
        entity.y / 50,
        entity.z ?? 0,
    ];

    const geometry = useMemo(() => {
        switch (entity.shape ?? "circle") {
            case "rect":
                return (
                    <boxGeometry
                        args={[
                            ((entity.width ?? 50) / 50) * scale,
                            ((entity.height ?? 30) / 50) * scale,
                            0.3 * scale,
                        ]}
                    />
                );
            case "polygon":
                // Create extruded polygon shape
                if (entity.vertices && entity.vertices.length >= 3) {
                    const shape = new THREE.Shape();
                    shape.moveTo(entity.vertices[0]!.x / 50, entity.vertices[0]!.y / 50);
                    for (let i = 1; i < entity.vertices.length; i++) {
                        shape.lineTo(entity.vertices[i]!.x / 50, entity.vertices[i]!.y / 50);
                    }
                    shape.closePath();

                    const extrudeSettings = { depth: 0.3 * scale, bevelEnabled: false };
                    return <extrudeGeometry args={[shape, extrudeSettings]} />;
                }
                return <sphereGeometry args={[0.5 * scale, 32, 32]} />;
            case "circle":
            default:
                return <sphereGeometry args={[0.5 * scale, 32, 32]} />;
        }
    }, [entity.shape, entity.width, entity.height, entity.vertices, scale]);

    const meshContent = (
        <mesh
            ref={meshRef}
            position={position}
            rotation={[0, 0, entity.rotation ?? 0]}
            onClick={(e) => {
                e.stopPropagation();
                onClick?.();
            }}
            onPointerOver={(e) => {
                e.stopPropagation();
                onHover?.(true);
            }}
            onPointerOut={() => onHover?.(false)}
            castShadow
            receiveShadow
        >
            {geometry}
            <meshStandardMaterial
                color={color}
                metalness={0.3}
                roughness={0.4}
                emissive={isSelected ? color : undefined}
                emissiveIntensity={isSelected ? 0.3 : 0}
            />
        </mesh>
    );

    return (
        <group>
            {showTrails && entity.velocityX !== undefined ? (
                <Trail
                    width={5}
                    length={20}
                    color={theme.success}
                    attenuation={(t) => t * t}
                >
                    {meshContent}
                </Trail>
            ) : (
                meshContent
            )}

            {/* Velocity vector */}
            {entity.velocityX !== undefined && entity.velocityY !== undefined && (
                <VelocityArrow
                    position={position}
                    velocity={[entity.velocityX / 50, entity.velocityY / 50, 0]}
                    color={theme.success}
                />
            )}

            {/* Label */}
            {showLabels && (entity.label || entity.mass > 0) && (
                <Html
                    position={[position[0], position[1] + 0.8, position[2]]}
                    center
                    distanceFactor={10}
                >
                    <div
                        style={{
                            background: "rgba(0,0,0,0.7)",
                            color: "white",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            fontSize: "12px",
                            whiteSpace: "nowrap",
                        }}
                    >
                        {entity.label ?? `${entity.mass}kg`}
                    </div>
                </Html>
            )}
        </group>
    );
}

// =============================================================================
// Velocity Arrow Component
// =============================================================================

interface VelocityArrowProps {
    position: [number, number, number];
    velocity: [number, number, number];
    color: string;
}

function VelocityArrow({
    position,
    velocity,
    color,
}: VelocityArrowProps): React.ReactElement | null {
    const magnitude = Math.sqrt(
        velocity[0] ** 2 + velocity[1] ** 2 + velocity[2] ** 2
    );

    if (magnitude < 0.01) return null;

    const arrowRef = useRef<THREE.ArrowHelper>(null);

    useEffect(() => {
        if (arrowRef.current) {
            const dir = new THREE.Vector3(...velocity).normalize();
            arrowRef.current.setDirection(dir);
            arrowRef.current.setLength(magnitude * 2, 0.15, 0.08);
        }
    }, [velocity, magnitude]);

    return (
        <arrowHelper
            ref={arrowRef}
            args={[
                new THREE.Vector3(1, 0, 0),
                new THREE.Vector3(...position),
                magnitude * 2,
                new THREE.Color(color).getHex(),
                0.15,
                0.08,
            ]}
        />
    );
}

// =============================================================================
// Spring Component
// =============================================================================

interface Spring3DProps {
    entity: PhysicsSpringEntity;
    entities: Map<string, SimEntityBase>;
    isHovered: boolean;
    theme: Physics3DTheme;
    showLabels: boolean;
    onHover?: (hovered: boolean) => void;
}

function Spring3D({
    entity,
    entities,
    isHovered,
    theme,
    showLabels,
    onHover,
}: Spring3DProps): React.ReactElement | null {
    const anchorId = entity.anchorId ?? entity.body1Id;
    const attachId = entity.attachId ?? entity.body2Id;
    const anchorEntity = anchorId ? entities.get(anchorId) : undefined;
    const attachEntity = attachId ? entities.get(attachId) : undefined;

    if (!anchorEntity || !attachEntity) return null;

    const start: [number, number, number] = [
        anchorEntity.x / 50,
        anchorEntity.y / 50,
        0,
    ];
    const end: [number, number, number] = [
        attachEntity.x / 50,
        attachEntity.y / 50,
        0,
    ];

    // Calculate stretch for color
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
    const currentLength = Math.sqrt(dx * dx + dy * dy);
    const stretch = currentLength / (entity.restLength / 50);

    const springColor = useMemo(() => {
        if (stretch > 1.2) return theme.danger;
        if (stretch < 0.8) return theme.warning;
        return entity.color ?? theme.secondary;
    }, [stretch, entity.color, theme]);

    // Generate spring coil points
    const points = useMemo(() => {
        const coils = Math.max(8, Math.floor(entity.restLength / 8));
        const amplitude = 0.15;
        const pts: THREE.Vector3[] = [];

        for (let i = 0; i <= coils * 4; i++) {
            const t = i / (coils * 4);
            const x = start[0] + (end[0] - start[0]) * t;
            const y = start[1] + (end[1] - start[1]) * t;

            // Add perpendicular offset for coil effect
            const perpX = -(end[1] - start[1]);
            const perpY = end[0] - start[0];
            const perpLen = Math.sqrt(perpX * perpX + perpY * perpY) || 1;
            const offset = Math.sin(i * Math.PI * 0.5) * amplitude;

            pts.push(
                new THREE.Vector3(
                    x + (perpX / perpLen) * offset,
                    y + (perpY / perpLen) * offset,
                    0
                )
            );
        }

        return pts;
    }, [start, end, entity.restLength]);

    return (
        <group>
            <Line
                points={points}
                color={springColor}
                lineWidth={isHovered ? 4 : 2}
                onPointerOver={() => onHover?.(true)}
                onPointerOut={() => onHover?.(false)}
            />

            {/* Anchor points */}
            <mesh position={start}>
                <sphereGeometry args={[0.08, 16, 16]} />
                <meshStandardMaterial color={theme.primary} />
            </mesh>
            <mesh position={end}>
                <sphereGeometry args={[0.08, 16, 16]} />
                <meshStandardMaterial color={theme.primary} />
            </mesh>

            {/* Label */}
            {showLabels && entity.label && (
                <Html
                    position={[
                        (start[0] + end[0]) / 2,
                        (start[1] + end[1]) / 2 + 0.3,
                        0,
                    ]}
                    center
                >
                    <div
                        style={{
                            background: "rgba(0,0,0,0.7)",
                            color: "white",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            fontSize: "10px",
                        }}
                    >
                        {entity.label}
                    </div>
                </Html>
            )}
        </group>
    );
}

// =============================================================================
// Vector Component
// =============================================================================

interface Vector3DProps {
    entity: PhysicsVectorEntity;
    entities: Map<string, SimEntityBase>;
    isHovered: boolean;
    theme: Physics3DTheme;
    showLabels: boolean;
    onHover?: (hovered: boolean) => void;
}

function Vector3D({
    entity,
    entities,
    isHovered,
    theme,
    showLabels,
    onHover,
}: Vector3DProps): React.ReactElement {
    let startX = entity.x / 50;
    let startY = entity.y / 50;

    if (entity.attachId) {
        const attachEntity = entities.get(entity.attachId);
        if (attachEntity) {
            startX = attachEntity.x / 50;
            startY = attachEntity.y / 50;
        }
    }

    const color = useMemo(() => {
        switch (entity.vectorType) {
            case "velocity":
                return theme.success;
            case "acceleration":
                return theme.warning;
            case "force":
                return theme.danger;
            case "displacement":
                return theme.secondary;
            default:
                return entity.color ?? theme.primary;
        }
    }, [entity.vectorType, entity.color, theme]);

    const direction = useMemo(
        () =>
            new THREE.Vector3(
                Math.cos(entity.angle ?? 0),
                Math.sin(entity.angle ?? 0),
                0
            ).normalize(),
        [entity.angle]
    );

    const origin = new THREE.Vector3(startX, startY, 0);
    const length = (entity.magnitude ?? 0) / 25;

    return (
        <group>
            <arrowHelper
                args={[
                    direction,
                    origin,
                    length,
                    new THREE.Color(color).getHex(),
                    length * 0.2,
                    length * 0.1,
                ]}
            />

            {showLabels && (
                <Html
                    position={[
                        startX + direction.x * length * 0.5,
                        startY + direction.y * length * 0.5 + 0.2,
                        0,
                    ]}
                    center
                >
                    <div
                        style={{
                            background: "rgba(0,0,0,0.7)",
                            color,
                            padding: "2px 6px",
                            borderRadius: "4px",
                            fontSize: "10px",
                        }}
                    >
                        {entity.label ?? entity.vectorType}
                    </div>
                </Html>
            )}
        </group>
    );
}

// =============================================================================
// Particle Component
// =============================================================================

interface Particle3DProps {
    entity: PhysicsParticleEntity;
    theme: Physics3DTheme;
}

function Particle3D({ entity, theme }: Particle3DProps): React.ReactElement {
    const meshRef = useRef<THREE.Mesh>(null);

    // Calculate opacity based on lifetime
    const opacity = useMemo(() => {
        if (entity.lifetime !== undefined && entity.age !== undefined) {
            return Math.max(0, 1 - entity.age / entity.lifetime);
        }
        return entity.opacity ?? 1;
    }, [entity.lifetime, entity.age, entity.opacity]);

    const color = entity.color ?? theme.primary;
    const size = (entity.scale ?? 1) * 0.15;

    return (
        <Float speed={2} rotationIntensity={0.5} floatIntensity={0.5}>
            <mesh
                ref={meshRef}
                position={[entity.x / 50, entity.y / 50, entity.z ?? 0]}
            >
                <sphereGeometry args={[size, 16, 16]} />
                <meshStandardMaterial
                    color={color}
                    transparent
                    opacity={opacity}
                    emissive={color}
                    emissiveIntensity={0.5}
                />
            </mesh>
        </Float>
    );
}

// =============================================================================
// Scene Component
// =============================================================================

interface Physics3DSceneProps {
    entities: Map<string, SimEntityBase>;
    selectedEntityId?: string;
    hoveredEntityId?: string;
    onEntityClick?: (entityId: string) => void;
    onEntityHover?: (entityId: string | null) => void;
    showGrid: boolean;
    showAxes: boolean;
    showLabels: boolean;
    showTrails: boolean;
    theme: Physics3DTheme;
}

function Physics3DScene({
    entities,
    selectedEntityId,
    hoveredEntityId,
    onEntityClick,
    onEntityHover,
    showGrid,
    showAxes,
    showLabels,
    showTrails,
    theme,
}: Physics3DSceneProps): React.ReactElement {
    const { camera } = useThree();

    // Auto-fit camera to entities
    useEffect(() => {
        if (entities.size === 0) return;

        let minX = Infinity,
            maxX = -Infinity;
        let minY = Infinity,
            maxY = -Infinity;

        entities.forEach((entity) => {
            minX = Math.min(minX, entity.x / 50);
            maxX = Math.max(maxX, entity.x / 50);
            minY = Math.min(minY, entity.y / 50);
            maxY = Math.max(maxY, entity.y / 50);
        });

        const centerX = (minX + maxX) / 2;
        const centerY = (minY + maxY) / 2;
        const size = Math.max(maxX - minX, maxY - minY, 5);

        camera.position.set(centerX, centerY, size * 1.5);
        camera.lookAt(centerX, centerY, 0);
    }, [entities, camera]);

    const renderEntity = (id: string, entity: SimEntityBase) => {
        const isHovered = hoveredEntityId === id;
        const isSelected = selectedEntityId === id;

        switch (entity.type) {
            case "rigidBody":
                return (
                    <RigidBody3D
                        key={id}
                        entity={entity as PhysicsBodyEntity}
                        isHovered={isHovered}
                        isSelected={isSelected}
                        theme={theme}
                        showLabels={showLabels}
                        showTrails={showTrails}
                        onClick={() => onEntityClick?.(id)}
                        onHover={(h) => onEntityHover?.(h ? id : null)}
                    />
                );

            case "spring":
                return (
                    <Spring3D
                        key={id}
                        entity={entity as PhysicsSpringEntity}
                        entities={entities}
                        isHovered={isHovered}
                        theme={theme}
                        showLabels={showLabels}
                        onHover={(h) => onEntityHover?.(h ? id : null)}
                    />
                );

            case "vector":
                return (
                    <Vector3D
                        key={id}
                        entity={entity as PhysicsVectorEntity}
                        entities={entities}
                        isHovered={isHovered}
                        theme={theme}
                        showLabels={showLabels}
                        onHover={(h) => onEntityHover?.(h ? id : null)}
                    />
                );

            case "particle":
                return (
                    <Particle3D
                        key={id}
                        entity={entity as PhysicsParticleEntity}
                        theme={theme}
                    />
                );

            default:
                return null;
        }
    };

    return (
        <>
            {/* Lighting */}
            <ambientLight intensity={0.4} />
            <directionalLight
                position={[10, 10, 10]}
                intensity={1}
                castShadow
                shadow-mapSize={[2048, 2048]}
            />
            <directionalLight position={[-5, 5, -5]} intensity={0.3} />

            {/* Environment */}
            <Environment preset="city" />

            {/* Grid */}
            {showGrid && (
                <Grid
                    infiniteGrid
                    cellSize={1}
                    cellThickness={0.5}
                    sectionSize={5}
                    sectionThickness={1}
                    sectionColor={theme.grid}
                    fadeDistance={50}
                />
            )}

            {/* Axes */}
            {showAxes && <axesHelper args={[5]} />}

            {/* Entities */}
            {Array.from(entities.entries()).map(([id, entity]) =>
                renderEntity(id, entity)
            )}
        </>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function Physics3DRenderer({
    entities,
    selectedEntityId,
    hoveredEntityId,
    onEntityClick,
    onEntityHover,
    showGrid = true,
    showAxes = false,
    showLabels = true,
    showTrails = false,
    cameraPosition = [0, 0, 10],
    ambientIntensity = 0.4,
    theme = DEFAULT_THEME,
}: Physics3DRendererProps): React.ReactElement {
    return (
        <div style={{ width: "100%", height: "100%", background: theme.background }}>
            <Canvas shadows>
                <PerspectiveCamera
                    makeDefault
                    position={cameraPosition}
                    fov={50}
                />
                <OrbitControls
                    enableDamping
                    dampingFactor={0.05}
                    minDistance={2}
                    maxDistance={100}
                />

                <Physics3DScene
                    entities={entities}
                    selectedEntityId={selectedEntityId}
                    hoveredEntityId={hoveredEntityId}
                    onEntityClick={onEntityClick}
                    onEntityHover={onEntityHover}
                    showGrid={showGrid}
                    showAxes={showAxes}
                    showLabels={showLabels}
                    showTrails={showTrails}
                    theme={theme}
                />
            </Canvas>
        </div>
    );
}

// =============================================================================
// Wrapper with 2D/3D Toggle
// =============================================================================

export interface PhysicsRendererWithToggleProps extends Physics3DRendererProps {
    mode: "2d" | "3d";
    onModeChange?: (mode: "2d" | "3d") => void;
    render2D: () => React.ReactElement;
}

export function PhysicsRendererWithToggle({
    mode,
    onModeChange,
    render2D,
    ...props3D
}: PhysicsRendererWithToggleProps): React.ReactElement {
    return (
        <div style={{ position: "relative", width: "100%", height: "100%" }}>
            {/* Mode Toggle */}
            <div
                style={{
                    position: "absolute",
                    top: 10,
                    right: 10,
                    zIndex: 100,
                    display: "flex",
                    gap: 4,
                    background: "rgba(0,0,0,0.5)",
                    borderRadius: 6,
                    padding: 4,
                }}
            >
                <button
                    onClick={() => onModeChange?.("2d")}
                    style={{
                        padding: "6px 12px",
                        border: "none",
                        borderRadius: 4,
                        cursor: "pointer",
                        background: mode === "2d" ? "#3b82f6" : "transparent",
                        color: "white",
                        fontSize: 12,
                        fontWeight: 500,
                    }}
                >
                    2D
                </button>
                <button
                    onClick={() => onModeChange?.("3d")}
                    style={{
                        padding: "6px 12px",
                        border: "none",
                        borderRadius: 4,
                        cursor: "pointer",
                        background: mode === "3d" ? "#3b82f6" : "transparent",
                        color: "white",
                        fontSize: 12,
                        fontWeight: 500,
                    }}
                >
                    3D
                </button>
            </div>

            {/* Renderer */}
            {mode === "2d" ? render2D() : <Physics3DRenderer {...props3D} />}
        </div>
    );
}

// =============================================================================
// Hook for Physics 3D State
// =============================================================================

export interface UsePhysics3DStateReturn {
    hoveredEntityId: string | null;
    selectedEntityId: string | null;
    mode: "2d" | "3d";
    showGrid: boolean;
    showAxes: boolean;
    showLabels: boolean;
    showTrails: boolean;
    setHoveredEntityId: (id: string | null) => void;
    setSelectedEntityId: (id: string | null) => void;
    setMode: (mode: "2d" | "3d") => void;
    toggleGrid: () => void;
    toggleAxes: () => void;
    toggleLabels: () => void;
    toggleTrails: () => void;
}

export function usePhysics3DState(): UsePhysics3DStateReturn {
    const [hoveredEntityId, setHoveredEntityId] = useState<string | null>(null);
    const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null);
    const [mode, setMode] = useState<"2d" | "3d">("2d");
    const [showGrid, setShowGrid] = useState(true);
    const [showAxes, setShowAxes] = useState(false);
    const [showLabels, setShowLabels] = useState(true);
    const [showTrails, setShowTrails] = useState(false);

    return {
        hoveredEntityId,
        selectedEntityId,
        mode,
        showGrid,
        showAxes,
        showLabels,
        showTrails,
        setHoveredEntityId,
        setSelectedEntityId,
        setMode,
        toggleGrid: () => setShowGrid((v) => !v),
        toggleAxes: () => setShowAxes((v) => !v),
        toggleLabels: () => setShowLabels((v) => !v),
        toggleTrails: () => setShowTrails((v) => !v),
    };
}

export default Physics3DRenderer;
