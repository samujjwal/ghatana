/**
 * Medicine Renderer Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for pharmacology/medicine simulation renderers
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import type {
    MedCompartmentEntity,
    MedDoseEntity,
    MedInfectionAgentEntity,
    SimEntityId,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import { StoryCanvas } from './StoryCanvas';

const meta: Meta<typeof StoryCanvas> = {
    title: 'Simulation/Medicine',
    component: StoryCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Renderers for pharmacokinetic and medical simulations: compartments, doses, and infection agents.',
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
// PK Compartment Types
// =============================================================================

const compartmentTypes: MedCompartmentEntity[] = [
    { id: id('central'), type: 'pkCompartment', x: -100, y: 0, compartmentType: 'central', volume: 5, concentration: 0.8, label: 'Central (Plasma)' },
    { id: id('peripheral'), type: 'pkCompartment', x: 0, y: 0, compartmentType: 'peripheral', volume: 20, concentration: 0.3, label: 'Peripheral (Tissue)' },
    { id: id('effect'), type: 'pkCompartment', x: 100, y: 0, compartmentType: 'effect', volume: 1, concentration: 0.5, label: 'Effect Site' },
];

export const CompartmentTypes: Story = {
    args: {
        entities: compartmentTypes,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Pharmacokinetic compartment types: central (plasma), peripheral (tissue), and effect site.',
            },
        },
    },
};

// =============================================================================
// One-Compartment Model
// =============================================================================

const oneCompModel: MedCompartmentEntity = {
    id: id('oneComp'),
    type: 'pkCompartment',
    x: 0,
    y: 0,
    compartmentType: 'central',
    volume: 5,
    concentration: 0.6,
    label: 'Vd = 5L',
};

const ivDose: MedDoseEntity = {
    id: id('ivDose'),
    type: 'dose',
    x: -100,
    y: 0,
    route: 'iv',
    amount: 100,
    time: 0,
    label: '100mg IV bolus',
};

export const OneCompartmentModel: Story = {
    args: {
        entities: [oneCompModel, ivDose],
        width: 400,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Simple one-compartment PK model with IV bolus dose.',
            },
        },
    },
};

// =============================================================================
// Two-Compartment Model
// =============================================================================

const twoCompCentral: MedCompartmentEntity = {
    id: id('twoCompCentral'),
    type: 'pkCompartment',
    x: -60,
    y: 0,
    compartmentType: 'central',
    volume: 3,
    concentration: 0.8,
    label: 'Central',
};

const twoCompPeriph: MedCompartmentEntity = {
    id: id('twoCompPeriph'),
    type: 'pkCompartment',
    x: 60,
    y: 0,
    compartmentType: 'peripheral',
    volume: 15,
    concentration: 0.2,
    label: 'Peripheral',
};

export const TwoCompartmentModel: Story = {
    args: {
        entities: [twoCompCentral, twoCompPeriph],
        width: 400,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Two-compartment PK model showing distribution between central and peripheral compartments.',
            },
        },
    },
};

// =============================================================================
// Dose Routes
// =============================================================================

const doseRoutes: MedDoseEntity[] = [
    { id: id('iv'), type: 'dose', x: -120, y: 0, route: 'iv', amount: 100, time: 0, label: 'IV' },
    { id: id('oral'), type: 'dose', x: -40, y: 0, route: 'oral', amount: 200, time: 0, label: 'Oral' },
    { id: id('im'), type: 'dose', x: 40, y: 0, route: 'im', amount: 150, time: 0, label: 'IM' },
    { id: id('subq'), type: 'dose', x: 120, y: 0, route: 'subq', amount: 50, time: 0, label: 'SubQ' },
];

export const DoseRoutes: Story = {
    args: {
        entities: doseRoutes,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Different drug administration routes: IV, oral, intramuscular (IM), and subcutaneous (SubQ).',
            },
        },
    },
};

// =============================================================================
// Infection Agents
// =============================================================================

const infectionAgents: MedInfectionAgentEntity[] = [
    { id: id('virus'), type: 'infectionAgent', x: -120, y: 0, agentType: 'virus', name: 'SARS-CoV-2', load: 1e6 },
    { id: id('bacteria'), type: 'infectionAgent', x: -40, y: 0, agentType: 'bacteria', name: 'E. coli', load: 1e8 },
    { id: id('parasite'), type: 'infectionAgent', x: 40, y: 0, agentType: 'parasite', name: 'P. falciparum', load: 1e4 },
    { id: id('fungus'), type: 'infectionAgent', x: 120, y: 0, agentType: 'fungus', name: 'C. albicans', load: 1e5 },
];

export const InfectionAgents: Story = {
    args: {
        entities: infectionAgents,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Infectious agents: virus, bacteria, parasite, and fungus with pathogen loads.',
            },
        },
    },
};

// =============================================================================
// Complete PK/PD Model
// =============================================================================

const pkpdCompartments: MedCompartmentEntity[] = [
    { id: id('pkpd_central'), type: 'pkCompartment', x: -80, y: -50, compartmentType: 'central', volume: 5, concentration: 0.7, label: 'Plasma' },
    { id: id('pkpd_periph'), type: 'pkCompartment', x: 80, y: -50, compartmentType: 'peripheral', volume: 20, concentration: 0.3, label: 'Tissue' },
    { id: id('pkpd_effect'), type: 'pkCompartment', x: 0, y: 50, compartmentType: 'effect', volume: 1, concentration: 0.5, label: 'Effect Site' },
];

const pkpdDose: MedDoseEntity = {
    id: id('pkpd_dose'),
    type: 'dose',
    x: -180,
    y: -50,
    route: 'iv',
    amount: 500,
    time: 0,
    label: '500mg IV',
};

export const PKPDModel: Story = {
    args: {
        entities: [...pkpdCompartments, pkpdDose],
        width: 500,
        height: 250,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Complete PK/PD model: plasma, tissue compartments, and effect site with IV dose.',
            },
        },
    },
};

// =============================================================================
// Antibiotic vs Bacteria
// =============================================================================

const antibioticCompartment: MedCompartmentEntity = {
    id: id('abx_site'),
    type: 'pkCompartment',
    x: 0,
    y: 0,
    compartmentType: 'effect',
    volume: 2,
    concentration: 0.8,
    label: 'Infection Site',
    highlighted: true,
};

const antibiotic: MedDoseEntity = {
    id: id('abx'),
    type: 'dose',
    x: -100,
    y: 0,
    route: 'iv',
    amount: 1000,
    time: 0,
    label: 'Vancomycin',
    highlighted: true,
};

const targetBacteria: MedInfectionAgentEntity = {
    id: id('staph'),
    type: 'infectionAgent',
    x: 100,
    y: 0,
    agentType: 'bacteria',
    name: 'MRSA',
    load: 1e7,
};

export const AntibioticVsBacteria: Story = {
    args: {
        entities: [antibioticCompartment, antibiotic, targetBacteria],
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Antibiotic therapy: Vancomycin targeting MRSA at the infection site.',
            },
        },
    },
};

// =============================================================================
// Antiviral vs Virus
// =============================================================================

const antiviralScenario: (MedDoseEntity | MedInfectionAgentEntity | MedCompartmentEntity)[] = [
    { id: id('av_plasma'), type: 'pkCompartment', x: 0, y: 0, compartmentType: 'central', volume: 5, concentration: 0.6, label: 'Plasma' },
    { id: id('remdesivir'), type: 'dose', x: -120, y: 0, route: 'iv', amount: 200, time: 0, label: 'Remdesivir' },
    { id: id('covid'), type: 'infectionAgent', x: 120, y: 0, agentType: 'virus', name: 'SARS-CoV-2', load: 1e5, highlighted: true },
];

export const AntiviralTherapy: Story = {
    args: {
        entities: antiviralScenario,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Antiviral therapy: Remdesivir against SARS-CoV-2 viral load.',
            },
        },
    },
};

// =============================================================================
// Multiple Dosing
// =============================================================================

const multiDosing: MedDoseEntity[] = [
    { id: id('dose1'), type: 'dose', x: -120, y: 0, route: 'oral', amount: 500, time: 0, label: 't=0h' },
    { id: id('dose2'), type: 'dose', x: -40, y: 0, route: 'oral', amount: 500, time: 8, label: 't=8h' },
    { id: id('dose3'), type: 'dose', x: 40, y: 0, route: 'oral', amount: 500, time: 16, label: 't=16h' },
    { id: id('dose4'), type: 'dose', x: 120, y: 0, route: 'oral', amount: 500, time: 24, label: 't=24h' },
];

export const MultipleDosing: Story = {
    args: {
        entities: multiDosing,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Multiple dosing schedule: 500mg oral every 8 hours (TID regimen).',
            },
        },
    },
};

// =============================================================================
// Drug Concentration States
// =============================================================================

const concentrationStates: MedCompartmentEntity[] = [
    { id: id('low'), type: 'pkCompartment', x: -100, y: 0, compartmentType: 'central', volume: 5, concentration: 0.1, label: 'Sub-therapeutic' },
    { id: id('optimal'), type: 'pkCompartment', x: 0, y: 0, compartmentType: 'central', volume: 5, concentration: 0.5, label: 'Therapeutic', highlighted: true },
    { id: id('high'), type: 'pkCompartment', x: 100, y: 0, compartmentType: 'central', volume: 5, concentration: 0.9, label: 'Toxic' },
];

export const DrugConcentrationStates: Story = {
    args: {
        entities: concentrationStates,
        width: 500,
        height: 200,
        domain: 'medicine',
    },
    parameters: {
        docs: {
            description: {
                story: 'Drug concentration states: sub-therapeutic, therapeutic (target), and toxic levels.',
            },
        },
    },
};
