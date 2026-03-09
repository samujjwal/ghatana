/**
 * Chemistry 3D Renderer - NGL Integration
 *
 * @doc.type module
 * @doc.purpose Render molecular structures in 3D using NGL Viewer
 * @doc.layer product
 * @doc.pattern Renderer
 */

import React, {
    useRef,
    useEffect,
    useCallback,
    useState,
    useMemo,
    forwardRef,
    useImperativeHandle,
} from "react";
import type {
    ChemAtomEntity,
    ChemBondEntity,
    ChemMoleculeEntity,
    SimEntityBase,
} from "@ghatana/tutorputor-contracts/v1/simulation";

// =============================================================================
// Types
// =============================================================================

export interface NGLMoleculeRendererProps {
    /** PDB or SDF file content, or SMILES string */
    moleculeData: string;
    /** Format of the molecule data */
    format: "pdb" | "sdf" | "mol2" | "smiles";
    /** Representation style */
    representation?: RepresentationType;
    /** Color scheme */
    colorScheme?: ColorScheme;
    /** Background color */
    backgroundColor?: string;
    /** Show spin animation */
    spin?: boolean;
    /** Spin speed (rotations per second) */
    spinSpeed?: number;
    /** Highlight specific atoms by index */
    highlightAtoms?: number[];
    /** Highlight color */
    highlightColor?: string;
    /** Show labels for atoms */
    showLabels?: boolean;
    /** Label type */
    labelType?: "element" | "serial" | "atomname" | "residue";
    /** Camera position preset */
    cameraPreset?: "front" | "side" | "top" | "isometric";
    /** Enable zoom controls */
    enableZoom?: boolean;
    /** Enable rotation controls */
    enableRotation?: boolean;
    /** Enable panning controls */
    enablePan?: boolean;
    /** Quality level */
    quality?: "low" | "medium" | "high";
    /** On atom click callback */
    onAtomClick?: (atomIndex: number, atomInfo: AtomInfo) => void;
    /** On atom hover callback */
    onAtomHover?: (atomIndex: number | null, atomInfo: AtomInfo | null) => void;
    /** On load complete callback */
    onLoad?: () => void;
    /** On error callback */
    onError?: (error: Error) => void;
    /** Custom class name */
    className?: string;
    /** Custom style */
    style?: React.CSSProperties;
}

export type RepresentationType =
    | "ball+stick"
    | "spacefill"
    | "cartoon"
    | "licorice"
    | "hyperball"
    | "surface"
    | "ribbon"
    | "rope"
    | "tube"
    | "trace"
    | "backbone"
    | "line"
    | "point";

export type ColorScheme =
    | "element"
    | "chainid"
    | "residueindex"
    | "bfactor"
    | "occupancy"
    | "hydrophobicity"
    | "electrostatic"
    | "uniform";

export interface AtomInfo {
    index: number;
    element: string;
    name: string;
    residueName?: string;
    chainId?: string;
    position: { x: number; y: number; z: number };
}

export interface NGLMoleculeRendererRef {
    /** Center the view on the molecule */
    centerView: () => void;
    /** Rotate the molecule */
    rotate: (x: number, y: number, z: number) => void;
    /** Zoom to specific level */
    zoom: (level: number) => void;
    /** Capture screenshot */
    screenshot: (params?: ScreenshotParams) => Promise<Blob>;
    /** Update representation */
    setRepresentation: (type: RepresentationType) => void;
    /** Update color scheme */
    setColorScheme: (scheme: ColorScheme) => void;
    /** Highlight atoms */
    highlightAtoms: (indices: number[], color?: string) => void;
    /** Clear highlights */
    clearHighlights: () => void;
    /** Toggle spin animation */
    toggleSpin: (enabled: boolean) => void;
    /** Get current orientation */
    getOrientation: () => Float32Array | null;
    /** Set orientation */
    setOrientation: (orientation: Float32Array) => void;
}

export interface ScreenshotParams {
    factor?: number;
    antialias?: boolean;
    transparent?: boolean;
    trim?: boolean;
}

// =============================================================================
// NGL Stage Wrapper (handles dynamic import)
// =============================================================================

// NGL types (simplified for TypeScript)
interface NGLStage {
    viewer: {
        container: HTMLElement;
        setBackground: (color: string) => void;
    };
    setQuality: (quality: string) => void;
    autoView: (duration?: number) => void;
    dispose: () => void;
    spinAnimation: {
        axis: { x: number; y: number; z: number };
        angle: number;
    };
    setSpin: (enabled: boolean) => void;
    makeImage: (params?: Record<string, unknown>) => Promise<Blob>;
    loadFile: (
        data: string | Blob,
        params?: Record<string, unknown>
    ) => Promise<NGLStructureComponent>;
    handleResize: () => void;
    viewerControls: {
        zoom: (level: number) => void;
        rotate: (quaternion: number[]) => void;
        getOrientation: () => Float32Array;
        orient: (orientation: Float32Array) => void;
    };
    signals: {
        clicked: { add: (callback: (pickingProxy: NGLPickingProxy) => void) => void };
        hovered: { add: (callback: (pickingProxy: NGLPickingProxy) => void) => void };
    };
}

interface NGLStructureComponent {
    addRepresentation: (
        type: string,
        params?: Record<string, unknown>
    ) => NGLRepresentation;
    removeAllRepresentations: () => void;
    structure: {
        atomStore: { count: number };
        getAtomProxy: (index: number) => NGLAtomProxy;
    };
    autoView: (selection?: string, duration?: number) => void;
}

interface NGLRepresentation {
    setVisibility: (visible: boolean) => void;
    setParameters: (params: Record<string, unknown>) => void;
    dispose: () => void;
}

interface NGLAtomProxy {
    index: number;
    element: string;
    atomname: string;
    resname: string;
    chainid: string;
    x: number;
    y: number;
    z: number;
}

interface NGLPickingProxy {
    atom?: NGLAtomProxy;
    closestBondAtom?: NGLAtomProxy;
}

// =============================================================================
// Component Implementation
// =============================================================================

export const NGLMoleculeRenderer = forwardRef<
    NGLMoleculeRendererRef,
    NGLMoleculeRendererProps
>(function NGLMoleculeRenderer(
    {
        moleculeData,
        format,
        representation = "ball+stick",
        colorScheme = "element",
        backgroundColor = "#0f172a",
        spin = false,
        spinSpeed = 0.01,
        highlightAtoms = [],
        highlightColor = "#ffff00",
        showLabels = false,
        labelType = "element",
        cameraPreset = "isometric",
        enableZoom = true,
        enableRotation = true,
        enablePan = true,
        quality = "medium",
        onAtomClick,
        onAtomHover,
        onLoad,
        onError,
        className,
        style,
    },
    ref
) {
    const containerRef = useRef<HTMLDivElement>(null);
    const stageRef = useRef<NGLStage | null>(null);
    const componentRef = useRef<NGLStructureComponent | null>(null);
    const representationRef = useRef<NGLRepresentation | null>(null);
    const highlightRepRef = useRef<NGLRepresentation | null>(null);
    const [isLoaded, setIsLoaded] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    // Initialize NGL Stage
    const initStage = useCallback(async () => {
        if (!containerRef.current) return;

        try {
            // Dynamic import of NGL
            const NGL = await import("ngl");

            // Create stage
            const stage = new NGL.Stage(containerRef.current, {
                backgroundColor,
                quality,
                impostor: true,
                workerDefault: true,
                sampleLevel: quality === "high" ? 2 : quality === "medium" ? 1 : 0,
            });

            // Configure controls
            stage.mouseControls.remove("scroll-zoom");
            stage.mouseControls.remove("scroll-ctrl");
            if (enableZoom) {
                stage.mouseControls.add("scroll", NGL.MouseActions.zoomScroll);
            }

            // Set up event handlers
            stage.signals.clicked.add((pickingProxy: NGLPickingProxy) => {
                if (pickingProxy?.atom && onAtomClick) {
                    const atom = pickingProxy.atom;
                    onAtomClick(atom.index, {
                        index: atom.index,
                        element: atom.element,
                        name: atom.atomname,
                        residueName: atom.resname,
                        chainId: atom.chainid,
                        position: { x: atom.x, y: atom.y, z: atom.z },
                    });
                }
            });

            stage.signals.hovered.add((pickingProxy: NGLPickingProxy) => {
                if (onAtomHover) {
                    if (pickingProxy?.atom) {
                        const atom = pickingProxy.atom;
                        onAtomHover(atom.index, {
                            index: atom.index,
                            element: atom.element,
                            name: atom.atomname,
                            residueName: atom.resname,
                            chainId: atom.chainid,
                            position: { x: atom.x, y: atom.y, z: atom.z },
                        });
                    } else {
                        onAtomHover(null, null);
                    }
                }
            });

            stageRef.current = stage as unknown as NGLStage;

            // Handle window resize
            const handleResize = () => stage.handleResize();
            window.addEventListener("resize", handleResize);

            return () => {
                window.removeEventListener("resize", handleResize);
                stage.dispose();
            };
        } catch (err) {
            const error = err instanceof Error ? err : new Error(String(err));
            setError(error);
            onError?.(error);
        }
    }, [
        backgroundColor,
        quality,
        enableZoom,
        onAtomClick,
        onAtomHover,
        onError,
    ]);

    // Load molecule data
    const loadMolecule = useCallback(async () => {
        if (!stageRef.current || !moleculeData) return;

        try {
            // Remove existing component
            if (componentRef.current) {
                componentRef.current.removeAllRepresentations();
            }

            // Prepare data for loading
            let loadData: string | Blob = moleculeData;
            const loadParams: Record<string, unknown> = { ext: format };

            // Handle SMILES - need to convert first
            if (format === "smiles") {
                // Use RDKit or OpenBabel service for SMILES to 3D conversion
                // For now, we'll create a simple placeholder
                const response = await fetch("/api/chemistry/smiles-to-mol", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ smiles: moleculeData }),
                });

                if (!response.ok) {
                    throw new Error("Failed to convert SMILES to 3D structure");
                }

                const result = await response.json();
                loadData = new Blob([result.molData], { type: "text/plain" });
                loadParams.ext = "mol2";
            } else {
                loadData = new Blob([moleculeData], { type: "text/plain" });
            }

            // Load structure
            const component = await stageRef.current.loadFile(loadData, loadParams);
            componentRef.current = component;

            // Add representation
            updateRepresentation(representation, colorScheme);

            // Apply camera preset
            applyCameraPreset(cameraPreset);

            // Update highlights
            if (highlightAtoms.length > 0) {
                updateHighlights(highlightAtoms, highlightColor);
            }

            // Add labels if enabled
            if (showLabels) {
                addLabels(labelType);
            }

            // Enable spin if requested
            if (spin) {
                stageRef.current.setSpin(true);
            }

            setIsLoaded(true);
            onLoad?.();
        } catch (err) {
            const error = err instanceof Error ? err : new Error(String(err));
            setError(error);
            onError?.(error);
        }
    }, [
        moleculeData,
        format,
        representation,
        colorScheme,
        cameraPreset,
        highlightAtoms,
        highlightColor,
        showLabels,
        labelType,
        spin,
        onLoad,
        onError,
    ]);

    // Update representation
    const updateRepresentation = useCallback(
        (type: RepresentationType, scheme: ColorScheme) => {
            if (!componentRef.current) return;

            // Remove existing representation
            if (representationRef.current) {
                representationRef.current.dispose();
            }

            // Add new representation
            representationRef.current = componentRef.current.addRepresentation(type, {
                colorScheme: scheme,
                quality,
            });
        },
        [quality]
    );

    // Update highlights
    const updateHighlights = useCallback((indices: number[], color: string) => {
        if (!componentRef.current) return;

        // Remove existing highlights
        if (highlightRepRef.current) {
            highlightRepRef.current.dispose();
        }

        if (indices.length === 0) return;

        // Create selection string
        const selection = `@${indices.join(",")}`;

        // Add highlight representation
        highlightRepRef.current = componentRef.current.addRepresentation(
            "spacefill",
            {
                sele: selection,
                color,
                opacity: 0.6,
                scale: 1.2,
            }
        );
    }, []);

    // Add labels
    const addLabels = useCallback((type: string) => {
        if (!componentRef.current) return;

        componentRef.current.addRepresentation("label", {
            labelType: type,
            color: "white",
            fontFamily: "monospace",
            fontSize: 12,
            backgroundOpacity: 0.5,
            showBackground: true,
        });
    }, []);

    // Apply camera preset
    const applyCameraPreset = useCallback((preset: string) => {
        if (!componentRef.current) return;

        componentRef.current.autoView(undefined, 500);

        // Additional rotation based on preset
        const stage = stageRef.current;
        if (!stage) return;

        switch (preset) {
            case "front":
                // Default view
                break;
            case "side":
                stage.viewerControls.rotate([0, 0.707, 0, 0.707]);
                break;
            case "top":
                stage.viewerControls.rotate([0.707, 0, 0, 0.707]);
                break;
            case "isometric":
                stage.viewerControls.rotate([0.35, 0.35, 0.15, 0.85]);
                break;
        }
    }, []);

    // Initialize on mount
    useEffect(() => {
        const cleanup = initStage();
        return () => {
            cleanup?.then((fn) => fn?.());
        };
    }, [initStage]);

    // Load molecule when data changes
    useEffect(() => {
        if (stageRef.current) {
            loadMolecule();
        }
    }, [loadMolecule]);

    // Update representation when props change
    useEffect(() => {
        if (isLoaded) {
            updateRepresentation(representation, colorScheme);
        }
    }, [representation, colorScheme, isLoaded, updateRepresentation]);

    // Update highlights when props change
    useEffect(() => {
        if (isLoaded) {
            updateHighlights(highlightAtoms, highlightColor);
        }
    }, [highlightAtoms, highlightColor, isLoaded, updateHighlights]);

    // Toggle spin when prop changes
    useEffect(() => {
        if (stageRef.current && isLoaded) {
            stageRef.current.setSpin(spin);
        }
    }, [spin, isLoaded]);

    // Expose methods via ref
    useImperativeHandle(
        ref,
        () => ({
            centerView: () => {
                componentRef.current?.autoView(undefined, 500);
            },
            rotate: (x: number, y: number, z: number) => {
                stageRef.current?.viewerControls.rotate([x, y, z, 1]);
            },
            zoom: (level: number) => {
                stageRef.current?.viewerControls.zoom(level);
            },
            screenshot: async (params?: ScreenshotParams) => {
                if (!stageRef.current) {
                    throw new Error("Stage not initialized");
                }
                return stageRef.current.makeImage({
                    factor: params?.factor ?? 2,
                    antialias: params?.antialias ?? true,
                    transparent: params?.transparent ?? false,
                    trim: params?.trim ?? false,
                });
            },
            setRepresentation: (type: RepresentationType) => {
                updateRepresentation(type, colorScheme);
            },
            setColorScheme: (scheme: ColorScheme) => {
                updateRepresentation(representation, scheme);
            },
            highlightAtoms: (indices: number[], color?: string) => {
                updateHighlights(indices, color ?? highlightColor);
            },
            clearHighlights: () => {
                if (highlightRepRef.current) {
                    highlightRepRef.current.dispose();
                    highlightRepRef.current = null;
                }
            },
            toggleSpin: (enabled: boolean) => {
                stageRef.current?.setSpin(enabled);
            },
            getOrientation: () => {
                return stageRef.current?.viewerControls.getOrientation() ?? null;
            },
            setOrientation: (orientation: Float32Array) => {
                stageRef.current?.viewerControls.orient(orientation);
            },
        }),
        [
            colorScheme,
            representation,
            highlightColor,
            updateRepresentation,
            updateHighlights,
        ]
    );

    return (
        <div
            ref={containerRef}
            className={className}
            style={{
                width: "100%",
                height: "100%",
                minHeight: 400,
                position: "relative",
                ...style,
            }}
        >
            {!isLoaded && !error && (
                <div
                    style={{
                        position: "absolute",
                        inset: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: backgroundColor,
                        color: "#94a3b8",
                    }}
                >
                    Loading molecule...
                </div>
            )}
            {error && (
                <div
                    style={{
                        position: "absolute",
                        inset: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        flexDirection: "column",
                        background: backgroundColor,
                        color: "#ef4444",
                        padding: 20,
                        textAlign: "center",
                    }}
                >
                    <div style={{ marginBottom: 8 }}>Failed to load molecule</div>
                    <div style={{ fontSize: 12, color: "#94a3b8" }}>
                        {error.message}
                    </div>
                </div>
            )}
        </div>
    );
});

// =============================================================================
// Chemistry 3D Renderer (Entity-based)
// =============================================================================

export interface Chemistry3DRendererProps {
    entities: Map<string, SimEntityBase>;
    selectedEntityId?: string;
    hoveredEntityId?: string;
    onEntityClick?: (entityId: string) => void;
    onEntityHover?: (entityId: string | null) => void;
    representation?: RepresentationType;
    colorScheme?: ColorScheme;
    backgroundColor?: string;
    showLabels?: boolean;
    mode?: "2d" | "3d";
    onModeChange?: (mode: "2d" | "3d") => void;
}

export function Chemistry3DRenderer({
    entities,
    selectedEntityId,
    hoveredEntityId,
    onEntityClick,
    onEntityHover,
    representation = "ball+stick",
    colorScheme = "element",
    backgroundColor = "#0f172a",
    showLabels = false,
    mode = "3d",
    onModeChange,
}: Chemistry3DRendererProps): React.ReactElement {
    const nglRef = useRef<NGLMoleculeRendererRef>(null);

    // Convert entities to molecule data
    const moleculeData = useMemo(() => {
        // Find molecule entities
        const molecules = Array.from(entities.values()).filter(
            (e) => (e as { entityType?: string }).entityType === "molecule"
        ) as ChemMoleculeEntity[];

        if (molecules.length === 0) {
            // Try to build from atoms and bonds
            const atoms = Array.from(entities.values()).filter(
                (e) => (e as { entityType?: string }).entityType === "atom"
            ) as ChemAtomEntity[];
            const bonds = Array.from(entities.values()).filter(
                (e) => (e as { entityType?: string }).entityType === "bond"
            ) as ChemBondEntity[];

            if (atoms.length > 0) {
                return buildMolFileFromEntities(atoms, bonds);
            }
            return null;
        }

        // Use first molecule's SMILES or MOL data
        const mol = molecules[0]!;
        if (mol.smiles) {
            return { data: mol.smiles, format: "smiles" as const };
        }
        const molExt = mol as ChemMoleculeEntity & { molData?: string };
        if (molExt.molData) {
            return { data: molExt.molData, format: "mol2" as const };
        }

        return null;
    }, [entities]);

    // Calculate highlight atoms based on selection
    const highlightAtoms = useMemo(() => {
        if (!selectedEntityId) return [];

        const entity = entities.get(selectedEntityId);
        if (!entity) return [];

        if (entity.type === "atom") {
            const atom = entity as ChemAtomEntity & { atomIndex?: number };
            return [atom.atomIndex ?? 0];
        }

        if (entity.type === "bond") {
            const bond = entity as ChemBondEntity & { atom1Index?: number; atom2Index?: number };
            return [bond.atom1Index ?? 0, bond.atom2Index ?? 1];
        }

        return [];
    }, [entities, selectedEntityId]);

    if (!moleculeData) {
        return (
            <div
                style={{
                    width: "100%",
                    height: "100%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: backgroundColor,
                    color: "#94a3b8",
                }}
            >
                No molecule data available for 3D rendering
            </div>
        );
    }

    return (
        <div style={{ position: "relative", width: "100%", height: "100%" }}>
            {/* Mode Toggle */}
            {onModeChange && (
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
                        onClick={() => onModeChange("2d")}
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
                        onClick={() => onModeChange("3d")}
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
            )}

            {/* Representation Controls */}
            <div
                style={{
                    position: "absolute",
                    bottom: 10,
                    left: 10,
                    zIndex: 100,
                    display: "flex",
                    gap: 4,
                    background: "rgba(0,0,0,0.5)",
                    borderRadius: 6,
                    padding: 4,
                }}
            >
                {(
                    ["ball+stick", "spacefill", "licorice", "surface"] as const
                ).map((rep) => (
                    <button
                        key={rep}
                        onClick={() => nglRef.current?.setRepresentation(rep)}
                        style={{
                            padding: "4px 8px",
                            border: "none",
                            borderRadius: 4,
                            cursor: "pointer",
                            background:
                                representation === rep
                                    ? "rgba(59,130,246,0.5)"
                                    : "transparent",
                            color: "white",
                            fontSize: 10,
                        }}
                    >
                        {rep.replace("+", " & ")}
                    </button>
                ))}
            </div>

            <NGLMoleculeRenderer
                ref={nglRef}
                moleculeData={moleculeData.data}
                format={moleculeData.format}
                representation={representation}
                colorScheme={colorScheme}
                backgroundColor={backgroundColor}
                showLabels={showLabels}
                highlightAtoms={highlightAtoms}
                highlightColor="#ffff00"
                onAtomClick={(index) => {
                    // Find entity by atom index
                    for (const [id, entity] of entities) {
                        if (
                            entity.type === "atom" &&
                            (entity as ChemAtomEntity & { atomIndex?: number }).atomIndex === index
                        ) {
                            onEntityClick?.(id);
                            break;
                        }
                    }
                }}
                onAtomHover={(index) => {
                    if (index === null) {
                        onEntityHover?.(null);
                        return;
                    }
                    // Find entity by atom index
                    for (const [id, entity] of entities) {
                        if (
                            entity.type === "atom" &&
                            (entity as ChemAtomEntity & { atomIndex?: number }).atomIndex === index
                        ) {
                            onEntityHover?.(id);
                            break;
                        }
                    }
                }}
            />
        </div>
    );
}

// =============================================================================
// Utility Functions
// =============================================================================

function buildMolFileFromEntities(
    atoms: ChemAtomEntity[],
    bonds: ChemBondEntity[]
): { data: string; format: "mol2" } {
    // Build MOL2 format from entities
    const lines: string[] = [];

    // Header
    lines.push("@<TRIPOS>MOLECULE");
    lines.push("molecule");
    lines.push(`${atoms.length} ${bonds.length} 0 0 0`);
    lines.push("SMALL");
    lines.push("NO_CHARGES");
    lines.push("");

    // Atoms
    lines.push("@<TRIPOS>ATOM");
    atoms.forEach((atom, i) => {
        const x = atom.x?.toFixed(4) ?? "0.0000";
        const y = atom.y?.toFixed(4) ?? "0.0000";
        const z = atom.z?.toFixed(4) ?? "0.0000";
        const element = atom.element ?? "C";
        lines.push(
            `${i + 1} ${element}${i + 1} ${x} ${y} ${z} ${element} 1 MOL 0.0000`
        );
    });

    // Bonds
    if (bonds.length > 0) {
        lines.push("@<TRIPOS>BOND");
        bonds.forEach((bond, i) => {
            const bondExt = bond as ChemBondEntity & { atom1Index?: number; atom2Index?: number };
            const atom1 = (bondExt.atom1Index ?? 0) + 1;
            const atom2 = (bondExt.atom2Index ?? 1) + 1;
            const order = bond.bondOrder ?? 1;
            const bondType = order === 2 ? "2" : order === 3 ? "3" : "1";
            lines.push(`${i + 1} ${atom1} ${atom2} ${bondType}`);
        });
    }

    return {
        data: lines.join("\n"),
        format: "mol2",
    };
}

// =============================================================================
// Hook for Chemistry 3D State
// =============================================================================

export interface UseChemistry3DStateReturn {
    mode: "2d" | "3d";
    representation: RepresentationType;
    colorScheme: ColorScheme;
    showLabels: boolean;
    spin: boolean;
    setMode: (mode: "2d" | "3d") => void;
    setRepresentation: (rep: RepresentationType) => void;
    setColorScheme: (scheme: ColorScheme) => void;
    toggleLabels: () => void;
    toggleSpin: () => void;
}

export function useChemistry3DState(): UseChemistry3DStateReturn {
    const [mode, setMode] = useState<"2d" | "3d">("2d");
    const [representation, setRepresentation] =
        useState<RepresentationType>("ball+stick");
    const [colorScheme, setColorScheme] = useState<ColorScheme>("element");
    const [showLabels, setShowLabels] = useState(false);
    const [spin, setSpin] = useState(false);

    return {
        mode,
        representation,
        colorScheme,
        showLabels,
        spin,
        setMode,
        setRepresentation,
        setColorScheme,
        toggleLabels: () => setShowLabels((v) => !v),
        toggleSpin: () => setSpin((v) => !v),
    };
}

export default NGLMoleculeRenderer;
