/**
 * Chemistry Renderer Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for chemistry simulation renderers
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import type {
    ChemAtomEntity,
    ChemBondEntity,
    ChemMoleculeEntity,
    ChemReactionArrowEntity,
    SimEntityId,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import { StoryCanvas } from './StoryCanvas';

const meta: Meta<typeof StoryCanvas> = {
    title: 'Simulation/Chemistry',
    component: StoryCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Renderers for chemistry simulations: atoms, bonds, molecules, and reactions.',
            },
        },
    },
    argTypes: {
        width: { control: { type: 'range', min: 300, max: 1200, step: 50 } },
        height: { control: { type: 'range', min: 200, max: 800, step: 50 } },
        zoom: { control: { type: 'range', min: 0.5, max: 2, step: 0.1 } },
        showGrid: { control: 'boolean' },
        backgroundColor: { control: 'color' },
    },
};

export default meta;
type Story = StoryObj<typeof StoryCanvas>;

const id = (s: string) => s as SimEntityId;

// =============================================================================
// Atom Elements with CPK Colors
// =============================================================================

const atomElements: ChemAtomEntity[] = [
    { id: id('h'), type: 'atom', x: -150, y: -50, element: 'H', label: 'Hydrogen' },
    { id: id('c'), type: 'atom', x: -50, y: -50, element: 'C', label: 'Carbon' },
    { id: id('n'), type: 'atom', x: 50, y: -50, element: 'N', label: 'Nitrogen' },
    { id: id('o'), type: 'atom', x: 150, y: -50, element: 'O', label: 'Oxygen' },
    { id: id('f'), type: 'atom', x: -150, y: 50, element: 'F', label: 'Fluorine' },
    { id: id('cl'), type: 'atom', x: -50, y: 50, element: 'Cl', label: 'Chlorine' },
    { id: id('s'), type: 'atom', x: 50, y: 50, element: 'S', label: 'Sulfur' },
    { id: id('p'), type: 'atom', x: 150, y: 50, element: 'P', label: 'Phosphorus' },
];

export const AtomElements: Story = {
    args: {
        entities: atomElements,
        width: 500,
        height: 250,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Common elements displayed with CPK (Corey-Pauling-Koltun) coloring convention.',
            },
        },
    },
};

// =============================================================================
// Metal Atoms
// =============================================================================

const metalAtoms: ChemAtomEntity[] = [
    { id: id('fe'), type: 'atom', x: -100, y: 0, element: 'Fe', label: 'Iron' },
    { id: id('cu'), type: 'atom', x: 0, y: 0, element: 'Cu', label: 'Copper' },
    { id: id('zn'), type: 'atom', x: 100, y: 0, element: 'Zn', label: 'Zinc' },
];

export const MetalAtoms: Story = {
    args: {
        entities: metalAtoms,
        width: 400,
        height: 150,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Transition metal atoms with CPK colors.',
            },
        },
    },
};

// =============================================================================
// Bond Types
// =============================================================================

const bondAtoms: ChemAtomEntity[] = [
    { id: id('a1'), type: 'atom', x: -180, y: 0, element: 'C' },
    { id: id('a2'), type: 'atom', x: -80, y: 0, element: 'C' },
    { id: id('a3'), type: 'atom', x: -30, y: 0, element: 'C' },
    { id: id('a4'), type: 'atom', x: 70, y: 0, element: 'C' },
    { id: id('a5'), type: 'atom', x: 120, y: 0, element: 'C' },
    { id: id('a6'), type: 'atom', x: 220, y: 0, element: 'C' },
];

const bondTypes: ChemBondEntity[] = [
    { id: id('b1'), type: 'bond', x: 0, y: 0, atom1Id: id('a1'), atom2Id: id('a2'), bondType: 'single', label: 'Single' },
    { id: id('b2'), type: 'bond', x: 0, y: 0, atom1Id: id('a3'), atom2Id: id('a4'), bondType: 'double', label: 'Double' },
    { id: id('b3'), type: 'bond', x: 0, y: 0, atom1Id: id('a5'), atom2Id: id('a6'), bondType: 'triple', label: 'Triple' },
];

export const BondTypes: Story = {
    args: {
        entities: [...bondAtoms, ...bondTypes],
        width: 600,
        height: 150,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Different bond types: single, double, and triple bonds between carbon atoms.',
            },
        },
    },
};

// =============================================================================
// Water Molecule (H₂O)
// =============================================================================

const waterAtoms: ChemAtomEntity[] = [
    { id: id('wo'), type: 'atom', x: 0, y: 0, element: 'O' },
    { id: id('wh1'), type: 'atom', x: -50, y: 40, element: 'H' },
    { id: id('wh2'), type: 'atom', x: 50, y: 40, element: 'H' },
];

const waterBonds: ChemBondEntity[] = [
    { id: id('wb1'), type: 'bond', x: 0, y: 0, atom1Id: id('wo'), atom2Id: id('wh1'), bondType: 'single' },
    { id: id('wb2'), type: 'bond', x: 0, y: 0, atom1Id: id('wo'), atom2Id: id('wh2'), bondType: 'single' },
];

const waterMolecule: ChemMoleculeEntity = {
    id: id('water'),
    type: 'molecule',
    x: 0,
    y: 0,
    formula: 'H₂O',
    name: 'Water',
    atomIds: [id('wo'), id('wh1'), id('wh2')],
};

export const WaterMolecule: Story = {
    args: {
        entities: [...waterBonds, ...waterAtoms, waterMolecule],
        width: 300,
        height: 200,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Water molecule (H₂O) showing oxygen and two hydrogen atoms.',
            },
        },
    },
};

// =============================================================================
// Methane Molecule (CH₄)
// =============================================================================

const methaneAtoms: ChemAtomEntity[] = [
    { id: id('mc'), type: 'atom', x: 0, y: 0, element: 'C' },
    { id: id('mh1'), type: 'atom', x: -50, y: -40, element: 'H' },
    { id: id('mh2'), type: 'atom', x: 50, y: -40, element: 'H' },
    { id: id('mh3'), type: 'atom', x: -50, y: 40, element: 'H' },
    { id: id('mh4'), type: 'atom', x: 50, y: 40, element: 'H' },
];

const methaneBonds: ChemBondEntity[] = [
    { id: id('mb1'), type: 'bond', x: 0, y: 0, atom1Id: id('mc'), atom2Id: id('mh1'), bondType: 'single' },
    { id: id('mb2'), type: 'bond', x: 0, y: 0, atom1Id: id('mc'), atom2Id: id('mh2'), bondType: 'single' },
    { id: id('mb3'), type: 'bond', x: 0, y: 0, atom1Id: id('mc'), atom2Id: id('mh3'), bondType: 'single' },
    { id: id('mb4'), type: 'bond', x: 0, y: 0, atom1Id: id('mc'), atom2Id: id('mh4'), bondType: 'single' },
];

export const MethaneMolecule: Story = {
    args: {
        entities: [...methaneBonds, ...methaneAtoms],
        width: 300,
        height: 200,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Methane molecule (CH₄) - 2D projection of tetrahedral structure.',
            },
        },
    },
};

// =============================================================================
// Benzene Ring (Aromatic)
// =============================================================================

const benzeneRadius = 60;
const benzeneAtoms: ChemAtomEntity[] = [];
const benzeneBonds: ChemBondEntity[] = [];

for (let i = 0; i < 6; i++) {
    const angle = (i / 6) * Math.PI * 2 - Math.PI / 2;
    benzeneAtoms.push({
        id: id(`bc${i}`),
        type: 'atom',
        x: Math.cos(angle) * benzeneRadius,
        y: Math.sin(angle) * benzeneRadius,
        element: 'C',
    });
}

for (let i = 0; i < 6; i++) {
    const nextI = (i + 1) % 6;
    benzeneBonds.push({
        id: id(`bb${i}`),
        type: 'bond',
        x: 0,
        y: 0,
        atom1Id: id(`bc${i}`),
        atom2Id: id(`bc${nextI}`),
        bondType: i % 2 === 0 ? 'aromatic' : 'single',
    });
}

export const BenzeneRing: Story = {
    args: {
        entities: [...benzeneBonds, ...benzeneAtoms],
        width: 300,
        height: 300,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Benzene ring (C₆H₆) showing aromatic bonds (alternating single/aromatic representation).',
            },
        },
    },
};

// =============================================================================
// Chemical Reaction
// =============================================================================

const reactionReactants: ChemAtomEntity[] = [
    { id: id('r1h1'), type: 'atom', x: -180, y: -20, element: 'H' },
    { id: id('r1h2'), type: 'atom', x: -180, y: 20, element: 'H' },
    { id: id('r2o1'), type: 'atom', x: -100, y: -20, element: 'O' },
    { id: id('r2o2'), type: 'atom', x: -100, y: 20, element: 'O' },
];

const reactionProducts: ChemAtomEntity[] = [
    { id: id('po'), type: 'atom', x: 130, y: 0, element: 'O' },
    { id: id('ph1'), type: 'atom', x: 90, y: 35, element: 'H' },
    { id: id('ph2'), type: 'atom', x: 170, y: 35, element: 'H' },
];

const reactionBonds: ChemBondEntity[] = [
    { id: id('rb1'), type: 'bond', x: 0, y: 0, atom1Id: id('r1h1'), atom2Id: id('r1h2'), bondType: 'single' },
    { id: id('rb2'), type: 'bond', x: 0, y: 0, atom1Id: id('r2o1'), atom2Id: id('r2o2'), bondType: 'double' },
    { id: id('rb3'), type: 'bond', x: 0, y: 0, atom1Id: id('po'), atom2Id: id('ph1'), bondType: 'single' },
    { id: id('rb4'), type: 'bond', x: 0, y: 0, atom1Id: id('po'), atom2Id: id('ph2'), bondType: 'single' },
];

const reactionArrow: ChemReactionArrowEntity = {
    id: id('arrow'),
    type: 'reactionArrow',
    x: 0,
    y: 0,
    startX: -50,
    startY: 0,
    endX: 60,
    endY: 0,
    arrowStyle: 'forward',
    label: '2H₂ + O₂ → 2H₂O',
};

export const ChemicalReaction: Story = {
    args: {
        entities: [...reactionBonds, ...reactionReactants, ...reactionProducts, reactionArrow],
        width: 500,
        height: 200,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Combustion of hydrogen: 2H₂ + O₂ → 2H₂O with reaction arrow.',
            },
        },
    },
};

// =============================================================================
// Reversible Reaction
// =============================================================================

const eqReactants: ChemAtomEntity[] = [
    { id: id('en'), type: 'atom', x: -100, y: 0, element: 'N', highlighted: true },
    { id: id('eh'), type: 'atom', x: -50, y: 0, element: 'H' },
];

const eqProducts: ChemAtomEntity[] = [
    { id: id('enh3'), type: 'atom', x: 100, y: 0, element: 'N', highlighted: true },
];

const eqArrow: ChemReactionArrowEntity = {
    id: id('eqArrow'),
    type: 'reactionArrow',
    x: 0,
    y: 0,
    startX: -20,
    startY: 0,
    endX: 60,
    endY: 0,
    arrowStyle: 'equilibrium',
    label: 'N₂ + 3H₂ ⇌ 2NH₃',
};

export const EquilibriumReaction: Story = {
    args: {
        entities: [...eqReactants, ...eqProducts, eqArrow],
        width: 400,
        height: 150,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Haber process equilibrium: N₂ + 3H₂ ⇌ 2NH₃ with double-headed arrow.',
            },
        },
    },
};

// =============================================================================
// Highlighted Atoms (Reaction Center)
// =============================================================================

const highlightedAtoms: ChemAtomEntity[] = [
    { id: id('ha1'), type: 'atom', x: -60, y: 0, element: 'C' },
    { id: id('ha2'), type: 'atom', x: 0, y: 0, element: 'C', highlighted: true },
    { id: id('ha3'), type: 'atom', x: 60, y: 0, element: 'C' },
    { id: id('ha4'), type: 'atom', x: 0, y: -50, element: 'O', highlighted: true },
];

const highlightedBonds: ChemBondEntity[] = [
    { id: id('hb1'), type: 'bond', x: 0, y: 0, atom1Id: id('ha1'), atom2Id: id('ha2'), bondType: 'single' },
    { id: id('hb2'), type: 'bond', x: 0, y: 0, atom1Id: id('ha2'), atom2Id: id('ha3'), bondType: 'single' },
    { id: id('hb3'), type: 'bond', x: 0, y: 0, atom1Id: id('ha2'), atom2Id: id('ha4'), bondType: 'double', highlighted: true },
];

export const ReactionCenter: Story = {
    args: {
        entities: [...highlightedBonds, ...highlightedAtoms],
        width: 300,
        height: 200,
        domain: 'chemistry',
    },
    parameters: {
        docs: {
            description: {
                story: 'Ketone showing highlighted carbonyl group as the reaction center.',
            },
        },
    },
};
