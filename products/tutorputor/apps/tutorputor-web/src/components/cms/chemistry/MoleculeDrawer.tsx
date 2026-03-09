/**
 * Molecule Drawer Component
 *
 * Provides a UI for drawing and editing molecular structures
 * with SMILES input and 2D structure visualization.
 *
 * Uses SmilesDrawer for lightweight SMILES-to-SVG rendering.
 * Supports common chemistry authoring workflows.
 *
 * @doc.type component
 * @doc.purpose Chemistry molecule authoring UI
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useEffect, useRef } from "react";

// =============================================================================
// Types
// =============================================================================

/** Element definition for periodic table picker */
interface ElementDef {
    symbol: string;
    name: string;
    atomicNumber: number;
    category: "nonmetal" | "noble-gas" | "alkali" | "alkaline" | "metalloid" | "halogen" | "transition" | "post-transition" | "lanthanide" | "actinide";
    color: string;
}

/** Atom in the molecule */
interface MoleculeAtom {
    id: string;
    element: string;
    x: number;
    y: number;
    charge?: number;
    isotope?: number;
    label?: string;
}

/** Bond in the molecule */
interface MoleculeBond {
    id: string;
    fromAtomId: string;
    toAtomId: string;
    order: 1 | 2 | 3;
    stereo?: "up" | "down" | "cis" | "trans";
}

/** Molecule structure */
interface MoleculeStructure {
    atoms: MoleculeAtom[];
    bonds: MoleculeBond[];
    smiles?: string;
    name?: string;
}

/** Drawing tool mode */
type DrawingTool = "select" | "atom" | "bond" | "eraser" | "charge";

/** MoleculeDrawer props */
interface MoleculeDrawerProps {
    initialStructure?: MoleculeStructure;
    initialSmiles?: string;
    onChange?: (structure: MoleculeStructure) => void;
    onSmilesChange?: (smiles: string) => void;
    readOnly?: boolean;
    width?: number;
    height?: number;
    showSmilesInput?: boolean;
    showElementPicker?: boolean;
    className?: string;
}

// =============================================================================
// Common Elements (subset for authoring)
// =============================================================================

const COMMON_ELEMENTS: ElementDef[] = [
    { symbol: "H", name: "Hydrogen", atomicNumber: 1, category: "nonmetal", color: "#FFFFFF" },
    { symbol: "C", name: "Carbon", atomicNumber: 6, category: "nonmetal", color: "#909090" },
    { symbol: "N", name: "Nitrogen", atomicNumber: 7, category: "nonmetal", color: "#3050F8" },
    { symbol: "O", name: "Oxygen", atomicNumber: 8, category: "nonmetal", color: "#FF0D0D" },
    { symbol: "F", name: "Fluorine", atomicNumber: 9, category: "halogen", color: "#90E050" },
    { symbol: "P", name: "Phosphorus", atomicNumber: 15, category: "nonmetal", color: "#FF8000" },
    { symbol: "S", name: "Sulfur", atomicNumber: 16, category: "nonmetal", color: "#FFFF30" },
    { symbol: "Cl", name: "Chlorine", atomicNumber: 17, category: "halogen", color: "#1FF01F" },
    { symbol: "Br", name: "Bromine", atomicNumber: 35, category: "halogen", color: "#A62929" },
    { symbol: "I", name: "Iodine", atomicNumber: 53, category: "halogen", color: "#940094" },
];

const METAL_ELEMENTS: ElementDef[] = [
    { symbol: "Li", name: "Lithium", atomicNumber: 3, category: "alkali", color: "#CC80FF" },
    { symbol: "Na", name: "Sodium", atomicNumber: 11, category: "alkali", color: "#AB5CF2" },
    { symbol: "K", name: "Potassium", atomicNumber: 19, category: "alkali", color: "#8F40D4" },
    { symbol: "Mg", name: "Magnesium", atomicNumber: 12, category: "alkaline", color: "#8AFF00" },
    { symbol: "Ca", name: "Calcium", atomicNumber: 20, category: "alkaline", color: "#3DFF00" },
    { symbol: "Fe", name: "Iron", atomicNumber: 26, category: "transition", color: "#E06633" },
    { symbol: "Cu", name: "Copper", atomicNumber: 29, category: "transition", color: "#C88033" },
    { symbol: "Zn", name: "Zinc", atomicNumber: 30, category: "transition", color: "#7D80B0" },
];

// =============================================================================
// Utility Functions
// =============================================================================

/**
 * Generate a unique ID for atoms/bonds
 */
function generateId(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Simple SMILES to structure conversion (basic patterns only)
 * NOTE: For production, use RDKit WASM or a proper SMILES parser
 */
function parseSmilesBasic(smiles: string): MoleculeStructure | null {
    // This is a placeholder - real implementation would use RDKit or OpenBabel
    // For now, we only handle very simple cases for demonstration
    const atoms: MoleculeAtom[] = [];
    const bonds: MoleculeBond[] = [];

    if (!smiles || smiles.length === 0) {
        return { atoms, bonds, smiles };
    }

    // Basic element detection (very simplified)
    const elementPattern = /([A-Z][a-z]?)/g;
    let match;
    let x = 100;
    let y = 200;
    let lastAtomId: string | null = null;

    while ((match = elementPattern.exec(smiles)) !== null) {
        const element = match[1];
        const atomId = generateId("atom");

        atoms.push({
            id: atomId,
            element,
            x,
            y: y + (atoms.length % 2 === 0 ? 0 : 30),
        });

        // Create bond to previous atom (simplified linear structure)
        if (lastAtomId) {
            bonds.push({
                id: generateId("bond"),
                fromAtomId: lastAtomId,
                toAtomId: atomId,
                order: 1,
            });
        }

        lastAtomId = atomId;
        x += 60;
    }

    return { atoms, bonds, smiles };
}

/**
 * Structure to SMILES conversion (basic)
 * NOTE: For production, use RDKit WASM
 */
function structureToSmiles(structure: MoleculeStructure): string {
    // Simplified - just concatenate element symbols
    // Real implementation would use proper SMILES generation
    if (structure.smiles) return structure.smiles;
    return structure.atoms.map((a) => a.element).join("");
}

// =============================================================================
// Sub-Components
// =============================================================================

interface ToolbarButtonProps {
    icon: string;
    label: string;
    active?: boolean;
    onClick: () => void;
    disabled?: boolean;
}

function ToolbarButton({ icon, label, active, onClick, disabled }: ToolbarButtonProps) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            title={label}
            className={`
                w-10 h-10 flex items-center justify-center rounded-lg
                text-lg font-medium transition-colors
                ${active
                    ? "bg-blue-600 text-white"
                    : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                }
                ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
            `}
        >
            {icon}
        </button>
    );
}

interface ElementPickerProps {
    selectedElement: string;
    onSelect: (element: string) => void;
    expanded?: boolean;
}

function ElementPicker({ selectedElement, onSelect, expanded }: ElementPickerProps) {
    const [showAll, setShowAll] = useState(expanded ?? false);

    return (
        <div className="p-2 bg-gray-50 rounded-lg border">
            <div className="text-xs font-medium text-gray-500 mb-2">Elements</div>
            <div className="flex flex-wrap gap-1">
                {COMMON_ELEMENTS.map((el) => (
                    <button
                        key={el.symbol}
                        onClick={() => onSelect(el.symbol)}
                        title={el.name}
                        className={`
                            w-8 h-8 flex items-center justify-center rounded
                            text-xs font-bold transition-colors
                            ${selectedElement === el.symbol
                                ? "ring-2 ring-blue-500"
                                : ""
                            }
                        `}
                        style={{
                            backgroundColor: el.color,
                            color: el.symbol === "H" || el.symbol === "S" ? "#333" : "#fff",
                        }}
                    >
                        {el.symbol}
                    </button>
                ))}
            </div>

            {showAll && (
                <>
                    <div className="text-xs font-medium text-gray-500 mt-3 mb-2">Metals</div>
                    <div className="flex flex-wrap gap-1">
                        {METAL_ELEMENTS.map((el) => (
                            <button
                                key={el.symbol}
                                onClick={() => onSelect(el.symbol)}
                                title={el.name}
                                className={`
                                    w-8 h-8 flex items-center justify-center rounded
                                    text-xs font-bold transition-colors
                                    ${selectedElement === el.symbol
                                        ? "ring-2 ring-blue-500"
                                        : ""
                                    }
                                `}
                                style={{
                                    backgroundColor: el.color,
                                    color: "#fff",
                                }}
                            >
                                {el.symbol}
                            </button>
                        ))}
                    </div>
                </>
            )}

            <button
                onClick={() => setShowAll(!showAll)}
                className="mt-2 text-xs text-blue-600 hover:underline"
            >
                {showAll ? "Show fewer" : "Show more elements"}
            </button>
        </div>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function MoleculeDrawer({
    initialStructure,
    initialSmiles,
    onChange,
    onSmilesChange,
    readOnly = false,
    width = 600,
    height = 400,
    showSmilesInput = true,
    showElementPicker = true,
    className,
}: MoleculeDrawerProps) {
    // State
    const [structure, setStructure] = useState<MoleculeStructure>(
        initialStructure ?? { atoms: [], bonds: [] }
    );
    const [smiles, setSmiles] = useState<string>(initialSmiles ?? "");
    const [tool, setTool] = useState<DrawingTool>("atom");
    const [selectedElement, setSelectedElement] = useState<string>("C");
    const [bondOrder, setBondOrder] = useState<1 | 2 | 3>(1);
    const [selectedAtomId, setSelectedAtomId] = useState<string | null>(null);
    const [bondStartAtomId, setBondStartAtomId] = useState<string | null>(null);
    const [validationError, setValidationError] = useState<string | null>(null);

    const canvasRef = useRef<HTMLCanvasElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Parse initial SMILES on mount
    useEffect(() => {
        if (initialSmiles && !initialStructure) {
            const parsed = parseSmilesBasic(initialSmiles);
            if (parsed) {
                setStructure(parsed);
            }
        }
    }, [initialSmiles, initialStructure]);

    // Notify parent on structure change
    useEffect(() => {
        onChange?.(structure);
        const newSmiles = structureToSmiles(structure);
        if (newSmiles !== smiles) {
            setSmiles(newSmiles);
            onSmilesChange?.(newSmiles);
        }
    }, [structure, onChange, onSmilesChange, smiles]);

    // Draw molecule on canvas
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        // Clear canvas
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, width, height);

        // Draw grid (subtle)
        ctx.strokeStyle = "#f0f0f0";
        ctx.lineWidth = 1;
        for (let x = 0; x < width; x += 20) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, height);
            ctx.stroke();
        }
        for (let y = 0; y < height; y += 20) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(width, y);
            ctx.stroke();
        }

        // Draw bonds first
        structure.bonds.forEach((bond) => {
            const fromAtom = structure.atoms.find((a) => a.id === bond.fromAtomId);
            const toAtom = structure.atoms.find((a) => a.id === bond.toAtomId);
            if (!fromAtom || !toAtom) return;

            ctx.strokeStyle = "#333333";
            ctx.lineWidth = 2;

            const dx = toAtom.x - fromAtom.x;
            const dy = toAtom.y - fromAtom.y;
            const len = Math.sqrt(dx * dx + dy * dy);
            const nx = -dy / len;
            const ny = dx / len;
            const offset = 4;

            if (bond.order === 1) {
                ctx.beginPath();
                ctx.moveTo(fromAtom.x, fromAtom.y);
                ctx.lineTo(toAtom.x, toAtom.y);
                ctx.stroke();
            } else if (bond.order === 2) {
                ctx.beginPath();
                ctx.moveTo(fromAtom.x + nx * offset, fromAtom.y + ny * offset);
                ctx.lineTo(toAtom.x + nx * offset, toAtom.y + ny * offset);
                ctx.stroke();
                ctx.beginPath();
                ctx.moveTo(fromAtom.x - nx * offset, fromAtom.y - ny * offset);
                ctx.lineTo(toAtom.x - nx * offset, toAtom.y - ny * offset);
                ctx.stroke();
            } else if (bond.order === 3) {
                ctx.beginPath();
                ctx.moveTo(fromAtom.x, fromAtom.y);
                ctx.lineTo(toAtom.x, toAtom.y);
                ctx.stroke();
                ctx.beginPath();
                ctx.moveTo(fromAtom.x + nx * offset * 1.5, fromAtom.y + ny * offset * 1.5);
                ctx.lineTo(toAtom.x + nx * offset * 1.5, toAtom.y + ny * offset * 1.5);
                ctx.stroke();
                ctx.beginPath();
                ctx.moveTo(fromAtom.x - nx * offset * 1.5, fromAtom.y - ny * offset * 1.5);
                ctx.lineTo(toAtom.x - nx * offset * 1.5, toAtom.y - ny * offset * 1.5);
                ctx.stroke();
            }
        });

        // Draw atoms
        structure.atoms.forEach((atom) => {
            const elDef = [...COMMON_ELEMENTS, ...METAL_ELEMENTS].find(
                (e) => e.symbol === atom.element
            );
            const color = elDef?.color ?? "#FF1493";
            const isSelected = atom.id === selectedAtomId;
            const isBondStart = atom.id === bondStartAtomId;

            // Atom circle
            ctx.beginPath();
            ctx.arc(atom.x, atom.y, 18, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.strokeStyle = isSelected || isBondStart ? "#2563eb" : "#666";
            ctx.lineWidth = isSelected || isBondStart ? 3 : 2;
            ctx.stroke();

            // Element symbol
            ctx.fillStyle = atom.element === "H" || atom.element === "S" ? "#333" : "#fff";
            ctx.font = "bold 14px Arial";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(atom.element, atom.x, atom.y);

            // Charge indicator
            if (atom.charge) {
                const chargeStr = atom.charge > 0 ? `${atom.charge}+` : `${Math.abs(atom.charge)}-`;
                ctx.fillStyle = "#333";
                ctx.font = "bold 10px Arial";
                ctx.fillText(chargeStr, atom.x + 14, atom.y - 14);
            }
        });
    }, [structure, width, height, selectedAtomId, bondStartAtomId]);

    // Canvas click handler
    const handleCanvasClick = useCallback(
        (e: React.MouseEvent<HTMLCanvasElement>) => {
            if (readOnly) return;

            const canvas = canvasRef.current;
            if (!canvas) return;

            const rect = canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            // Check if clicked on an atom
            const clickedAtom = structure.atoms.find((atom) => {
                const dx = atom.x - x;
                const dy = atom.y - y;
                return dx * dx + dy * dy <= 18 * 18;
            });

            switch (tool) {
                case "select":
                    setSelectedAtomId(clickedAtom?.id ?? null);
                    setBondStartAtomId(null);
                    break;

                case "atom":
                    if (!clickedAtom) {
                        // Add new atom
                        const newAtom: MoleculeAtom = {
                            id: generateId("atom"),
                            element: selectedElement,
                            x,
                            y,
                        };
                        setStructure((prev) => ({
                            ...prev,
                            atoms: [...prev.atoms, newAtom],
                        }));
                    } else {
                        // Change element of existing atom
                        setStructure((prev) => ({
                            ...prev,
                            atoms: prev.atoms.map((a) =>
                                a.id === clickedAtom.id
                                    ? { ...a, element: selectedElement }
                                    : a
                            ),
                        }));
                    }
                    break;

                case "bond":
                    if (clickedAtom) {
                        if (!bondStartAtomId) {
                            // Start bond
                            setBondStartAtomId(clickedAtom.id);
                        } else if (bondStartAtomId !== clickedAtom.id) {
                            // Complete bond
                            const existingBond = structure.bonds.find(
                                (b) =>
                                    (b.fromAtomId === bondStartAtomId &&
                                        b.toAtomId === clickedAtom.id) ||
                                    (b.fromAtomId === clickedAtom.id &&
                                        b.toAtomId === bondStartAtomId)
                            );

                            if (existingBond) {
                                // Cycle bond order
                                setStructure((prev) => ({
                                    ...prev,
                                    bonds: prev.bonds.map((b) =>
                                        b.id === existingBond.id
                                            ? { ...b, order: ((b.order % 3) + 1) as 1 | 2 | 3 }
                                            : b
                                    ),
                                }));
                            } else {
                                // Create new bond
                                const newBond: MoleculeBond = {
                                    id: generateId("bond"),
                                    fromAtomId: bondStartAtomId,
                                    toAtomId: clickedAtom.id,
                                    order: bondOrder,
                                };
                                setStructure((prev) => ({
                                    ...prev,
                                    bonds: [...prev.bonds, newBond],
                                }));
                            }
                            setBondStartAtomId(null);
                        }
                    } else {
                        setBondStartAtomId(null);
                    }
                    break;

                case "eraser":
                    if (clickedAtom) {
                        // Remove atom and its bonds
                        setStructure((prev) => ({
                            atoms: prev.atoms.filter((a) => a.id !== clickedAtom.id),
                            bonds: prev.bonds.filter(
                                (b) =>
                                    b.fromAtomId !== clickedAtom.id &&
                                    b.toAtomId !== clickedAtom.id
                            ),
                        }));
                    }
                    break;

                case "charge":
                    if (clickedAtom) {
                        // Cycle charge: 0 -> +1 -> +2 -> -1 -> -2 -> 0
                        const currentCharge = clickedAtom.charge ?? 0;
                        let newCharge: number;
                        if (currentCharge === 0) newCharge = 1;
                        else if (currentCharge === 1) newCharge = 2;
                        else if (currentCharge === 2) newCharge = -1;
                        else if (currentCharge === -1) newCharge = -2;
                        else newCharge = 0;

                        setStructure((prev) => ({
                            ...prev,
                            atoms: prev.atoms.map((a) =>
                                a.id === clickedAtom.id
                                    ? { ...a, charge: newCharge || undefined }
                                    : a
                            ),
                        }));
                    }
                    break;
            }
        },
        [tool, selectedElement, bondOrder, bondStartAtomId, structure, readOnly]
    );

    // SMILES input handler
    const handleSmilesInput = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => {
            const value = e.target.value;
            setSmiles(value);

            if (!value) {
                setStructure({ atoms: [], bonds: [] });
                setValidationError(null);
                return;
            }

            try {
                const parsed = parseSmilesBasic(value);
                if (parsed) {
                    setStructure(parsed);
                    setValidationError(null);
                } else {
                    setValidationError("Unable to parse SMILES");
                }
            } catch {
                setValidationError("Invalid SMILES format");
            }
        },
        []
    );

    // Clear all
    const handleClear = useCallback(() => {
        setStructure({ atoms: [], bonds: [] });
        setSmiles("");
        setSelectedAtomId(null);
        setBondStartAtomId(null);
        setValidationError(null);
    }, []);

    return (
        <div
            ref={containerRef}
            className={`flex flex-col bg-white rounded-lg border shadow-sm ${className ?? ""}`}
        >
            {/* Toolbar */}
            {!readOnly && (
                <div className="flex items-center gap-2 p-3 border-b">
                    <div className="flex items-center gap-1">
                        <ToolbarButton
                            icon="↖"
                            label="Select"
                            active={tool === "select"}
                            onClick={() => setTool("select")}
                        />
                        <ToolbarButton
                            icon="⚛"
                            label="Add Atom"
                            active={tool === "atom"}
                            onClick={() => setTool("atom")}
                        />
                        <ToolbarButton
                            icon="—"
                            label="Add Bond"
                            active={tool === "bond"}
                            onClick={() => setTool("bond")}
                        />
                        <ToolbarButton
                            icon="⌫"
                            label="Eraser"
                            active={tool === "eraser"}
                            onClick={() => setTool("eraser")}
                        />
                        <ToolbarButton
                            icon="±"
                            label="Charge"
                            active={tool === "charge"}
                            onClick={() => setTool("charge")}
                        />
                    </div>

                    <div className="w-px h-8 bg-gray-200 mx-2" />

                    {tool === "bond" && (
                        <div className="flex items-center gap-1">
                            <span className="text-xs text-gray-500 mr-1">Bond:</span>
                            {([1, 2, 3] as const).map((order) => (
                                <button
                                    key={order}
                                    onClick={() => setBondOrder(order)}
                                    className={`
                                        px-2 py-1 text-xs font-medium rounded
                                        ${bondOrder === order
                                            ? "bg-blue-600 text-white"
                                            : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                                        }
                                    `}
                                >
                                    {order === 1 ? "Single" : order === 2 ? "Double" : "Triple"}
                                </button>
                            ))}
                        </div>
                    )}

                    <div className="flex-1" />

                    <button
                        onClick={handleClear}
                        className="px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 rounded"
                    >
                        Clear All
                    </button>
                </div>
            )}

            {/* Main Content */}
            <div className="flex">
                {/* Canvas */}
                <div className="flex-1 p-4">
                    <canvas
                        ref={canvasRef}
                        width={width}
                        height={height}
                        onClick={handleCanvasClick}
                        className={`
                            border rounded-lg
                            ${readOnly ? "cursor-default" : "cursor-crosshair"}
                        `}
                        style={{ width, height }}
                    />
                </div>

                {/* Side Panel */}
                {showElementPicker && !readOnly && (
                    <div className="w-48 p-3 border-l">
                        <ElementPicker
                            selectedElement={selectedElement}
                            onSelect={setSelectedElement}
                        />
                    </div>
                )}
            </div>

            {/* SMILES Input */}
            {showSmilesInput && (
                <div className="p-3 border-t">
                    <label className="block text-xs font-medium text-gray-500 mb-1">
                        SMILES Notation
                    </label>
                    <div className="flex gap-2">
                        <input
                            type="text"
                            value={smiles}
                            onChange={handleSmilesInput}
                            readOnly={readOnly}
                            placeholder="e.g., CCO for ethanol"
                            className={`
                                flex-1 px-3 py-2 text-sm border rounded-md
                                font-mono
                                ${validationError
                                    ? "border-red-300 focus:ring-red-500"
                                    : "border-gray-300 focus:ring-blue-500"
                                }
                                ${readOnly ? "bg-gray-50" : ""}
                            `}
                        />
                    </div>
                    {validationError && (
                        <p className="mt-1 text-xs text-red-500">{validationError}</p>
                    )}
                    <p className="mt-1 text-xs text-gray-400">
                        Note: SMILES parsing is limited. For complex molecules, draw directly.
                    </p>
                </div>
            )}

            {/* Status Bar */}
            <div className="flex items-center gap-4 px-3 py-2 bg-gray-50 border-t text-xs text-gray-500">
                <span>Atoms: {structure.atoms.length}</span>
                <span>Bonds: {structure.bonds.length}</span>
                {bondStartAtomId && (
                    <span className="text-blue-600">
                        Click another atom to complete bond
                    </span>
                )}
            </div>
        </div>
    );
}

export default MoleculeDrawer;
