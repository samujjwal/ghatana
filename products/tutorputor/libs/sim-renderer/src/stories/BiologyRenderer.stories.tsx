/**
 * Biology Renderer Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for biology simulation renderers
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import type {
    BioCellEntity,
    BioOrganelleEntity,
    BioCompartmentEntity,
    BioEnzymeEntity,
    BioSignalEntity,
    BioGeneEntity,
    SimEntityId,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import { StoryCanvas } from './StoryCanvas';

const meta: Meta<typeof StoryCanvas> = {
    title: 'Simulation/Biology',
    component: StoryCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Renderers for biology simulations: cells, organelles, enzymes, and genetic elements.',
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
// Cell Types
// =============================================================================

const cellTypes: BioCellEntity[] = [
    { id: id('prok'), type: 'cell', x: -120, y: 0, cellType: 'prokaryote', radius: 40, label: 'Prokaryote' },
    { id: id('euk'), type: 'cell', x: 0, y: 0, cellType: 'eukaryote', radius: 50, label: 'Eukaryote' },
    { id: id('plant'), type: 'cell', x: 120, y: 0, cellType: 'plant', radius: 50, label: 'Plant Cell' },
];

export const CellTypes: Story = {
    args: {
        entities: cellTypes,
        width: 500,
        height: 250,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Different cell types: prokaryote (simple), eukaryote (animal), and plant cell.',
            },
        },
    },
};

// =============================================================================
// Organelles
// =============================================================================

const organelles: BioOrganelleEntity[] = [
    { id: id('nuc'), type: 'organelle', x: -120, y: -50, organelleType: 'nucleus', label: 'Nucleus' },
    { id: id('mito'), type: 'organelle', x: 0, y: -50, organelleType: 'mitochondria', label: 'Mitochondria' },
    { id: id('er'), type: 'organelle', x: 120, y: -50, organelleType: 'er', label: 'ER' },
    { id: id('golgi'), type: 'organelle', x: -120, y: 50, organelleType: 'golgi', label: 'Golgi' },
    { id: id('lyso'), type: 'organelle', x: 0, y: 50, organelleType: 'lysosome', label: 'Lysosome' },
    { id: id('ribo'), type: 'organelle', x: 120, y: 50, organelleType: 'ribosome', label: 'Ribosome' },
];

export const Organelles: Story = {
    args: {
        entities: organelles,
        width: 450,
        height: 250,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Common organelles: nucleus, mitochondria, endoplasmic reticulum, Golgi apparatus, lysosome, and ribosome.',
            },
        },
    },
};

// =============================================================================
// Complete Eukaryotic Cell
// =============================================================================

const eukaryoticCell: BioCellEntity = {
    id: id('mainCell'),
    type: 'cell',
    x: 0,
    y: 0,
    cellType: 'eukaryote',
    radius: 100,
};

const cellOrganelles: BioOrganelleEntity[] = [
    { id: id('cellNuc'), type: 'organelle', x: 0, y: 0, organelleType: 'nucleus', containedInId: id('mainCell') },
    { id: id('cellMito1'), type: 'organelle', x: 50, y: -40, organelleType: 'mitochondria', containedInId: id('mainCell') },
    { id: id('cellMito2'), type: 'organelle', x: -50, y: 40, organelleType: 'mitochondria', containedInId: id('mainCell') },
    { id: id('cellER'), type: 'organelle', x: 40, y: 50, organelleType: 'er', containedInId: id('mainCell') },
    { id: id('cellGolgi'), type: 'organelle', x: -60, y: -30, organelleType: 'golgi', containedInId: id('mainCell') },
    { id: id('cellLyso'), type: 'organelle', x: 70, y: 20, organelleType: 'lysosome', containedInId: id('mainCell') },
];

export const EukaryoticCell: Story = {
    args: {
        entities: [eukaryoticCell, ...cellOrganelles],
        width: 400,
        height: 350,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'A complete eukaryotic cell with nucleus, mitochondria, ER, Golgi, and lysosomes.',
            },
        },
    },
};

// =============================================================================
// Compartments
// =============================================================================

const compartments: BioCompartmentEntity[] = [
    { id: id('cyto'), type: 'compartment', x: 0, y: 0, compartmentType: 'cytoplasm', width: 200, height: 150 },
    { id: id('extracell'), type: 'compartment', x: 0, y: -100, compartmentType: 'extracellular', width: 200, height: 50 },
    { id: id('membrane'), type: 'compartment', x: 0, y: -50, compartmentType: 'membrane', width: 200, height: 20 },
];

export const Compartments: Story = {
    args: {
        entities: compartments,
        width: 400,
        height: 350,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Cellular compartments: extracellular space, membrane, and cytoplasm.',
            },
        },
    },
};

// =============================================================================
// Enzyme Reaction
// =============================================================================

const enzymeEntities: BioEnzymeEntity[] = [
    { id: id('enz'), type: 'enzyme', x: 0, y: 0, name: 'Hexokinase', active: true, kcat: 200, km: 0.1 },
];

const enzymeSubstrate: BioOrganelleEntity = {
    id: id('substrate'),
    type: 'organelle',
    x: -60,
    y: 0,
    organelleType: 'ribosome', // Using ribosome as a small molecule placeholder
    label: 'Glucose',
};

const enzymeProduct: BioOrganelleEntity = {
    id: id('product'),
    type: 'organelle',
    x: 60,
    y: 0,
    organelleType: 'ribosome',
    label: 'G6P',
};

export const EnzymeReaction: Story = {
    args: {
        entities: [...enzymeEntities, enzymeSubstrate, enzymeProduct],
        width: 350,
        height: 200,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'An enzyme (Hexokinase) catalyzing glucose phosphorylation.',
            },
        },
    },
};

// =============================================================================
// Signal Transduction
// =============================================================================

const signalEntities: BioSignalEntity[] = [
    { id: id('sig1'), type: 'signal', x: -80, y: 0, signalType: 'hormone', name: 'Insulin', concentration: 1.0 },
    { id: id('sig2'), type: 'signal', x: 0, y: 0, signalType: 'receptor', name: 'IR', active: true },
    { id: id('sig3'), type: 'signal', x: 80, y: 0, signalType: 'secondMessenger', name: 'cAMP', concentration: 0.5 },
];

export const SignalTransduction: Story = {
    args: {
        entities: signalEntities,
        width: 400,
        height: 200,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Signal transduction: hormone (insulin), receptor (IR), and second messenger (cAMP).',
            },
        },
    },
};

// =============================================================================
// Gene Expression
// =============================================================================

const geneEntities: BioGeneEntity[] = [
    { id: id('gene1'), type: 'gene', x: -80, y: 0, name: 'p53', sequence: 'ATG...', expressionLevel: 0.8, highlighted: true },
    { id: id('gene2'), type: 'gene', x: 0, y: 0, name: 'BRCA1', sequence: 'ATG...', expressionLevel: 0.3 },
    { id: id('gene3'), type: 'gene', x: 80, y: 0, name: 'MYC', sequence: 'ATG...', expressionLevel: 0.6 },
];

export const GeneExpression: Story = {
    args: {
        entities: geneEntities,
        width: 400,
        height: 200,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Gene expression levels: p53 (high, highlighted), BRCA1 (low), MYC (medium).',
            },
        },
    },
};

// =============================================================================
// Plant Cell
// =============================================================================

const plantCell: BioCellEntity = {
    id: id('plantMain'),
    type: 'cell',
    x: 0,
    y: 0,
    cellType: 'plant',
    radius: 100,
};

const plantOrganelles: BioOrganelleEntity[] = [
    { id: id('plantNuc'), type: 'organelle', x: -20, y: 0, organelleType: 'nucleus', containedInId: id('plantMain') },
    { id: id('chloro1'), type: 'organelle', x: 40, y: -30, organelleType: 'chloroplast', containedInId: id('plantMain') },
    { id: id('chloro2'), type: 'organelle', x: 30, y: 40, organelleType: 'chloroplast', containedInId: id('plantMain') },
    { id: id('chloro3'), type: 'organelle', x: -50, y: 50, organelleType: 'chloroplast', containedInId: id('plantMain') },
    { id: id('vacuole'), type: 'organelle', x: 0, y: 20, organelleType: 'vacuole', containedInId: id('plantMain') },
];

export const PlantCell: Story = {
    args: {
        entities: [plantCell, ...plantOrganelles],
        width: 400,
        height: 350,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'A plant cell with nucleus, chloroplasts, and central vacuole.',
            },
        },
    },
};

// =============================================================================
// Mitosis Stages
// =============================================================================

const mitosisStages: BioCellEntity[] = [
    { id: id('interphase'), type: 'cell', x: -150, y: 0, cellType: 'eukaryote', radius: 35, label: 'Interphase' },
    { id: id('prophase'), type: 'cell', x: -75, y: 0, cellType: 'eukaryote', radius: 35, label: 'Prophase' },
    { id: id('metaphase'), type: 'cell', x: 0, y: 0, cellType: 'eukaryote', radius: 35, label: 'Metaphase', highlighted: true },
    { id: id('anaphase'), type: 'cell', x: 75, y: 0, cellType: 'eukaryote', radius: 35, label: 'Anaphase' },
    { id: id('telophase'), type: 'cell', x: 150, y: 0, cellType: 'eukaryote', radius: 35, label: 'Telophase' },
];

export const MitosisStages: Story = {
    args: {
        entities: mitosisStages,
        width: 550,
        height: 200,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'The five stages of mitosis, with metaphase highlighted.',
            },
        },
    },
};

// =============================================================================
// Membrane Transport
// =============================================================================

const membraneCompartment: BioCompartmentEntity = {
    id: id('memComp'),
    type: 'compartment',
    x: 0,
    y: 0,
    compartmentType: 'membrane',
    width: 300,
    height: 30,
};

const transportSignals: BioSignalEntity[] = [
    { id: id('outside1'), type: 'signal', x: -80, y: -40, signalType: 'ligand', name: 'Na⁺', concentration: 1.0 },
    { id: id('outside2'), type: 'signal', x: 0, y: -40, signalType: 'ligand', name: 'Glucose', concentration: 0.8 },
    { id: id('channel'), type: 'signal', x: -80, y: 0, signalType: 'receptor', name: 'Channel', active: true },
    { id: id('pump'), type: 'signal', x: 0, y: 0, signalType: 'receptor', name: 'Pump', active: true },
    { id: id('inside1'), type: 'signal', x: -80, y: 40, signalType: 'ligand', name: 'Na⁺', concentration: 0.2 },
    { id: id('inside2'), type: 'signal', x: 0, y: 40, signalType: 'ligand', name: 'Glucose', concentration: 0.5 },
];

export const MembraneTransport: Story = {
    args: {
        entities: [membraneCompartment, ...transportSignals],
        width: 500,
        height: 200,
        domain: 'biology',
    },
    parameters: {
        docs: {
            description: {
                story: 'Membrane transport showing ion channels and pumps for Na⁺ and glucose.',
            },
        },
    },
};
