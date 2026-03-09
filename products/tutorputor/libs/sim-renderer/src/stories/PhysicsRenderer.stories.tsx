/**
 * Physics Renderer Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for physics simulation renderers
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import type {
    PhysicsBodyEntity,
    PhysicsSpringEntity,
    PhysicsVectorEntity,
    PhysicsParticleEntity,
    SimEntityId,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import { StoryCanvas } from './StoryCanvas';

const meta: Meta<typeof StoryCanvas> = {
    title: 'Simulation/Physics',
    component: StoryCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Renderers for physics simulations: rigid bodies, springs, vectors, and particles.',
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
// Rigid Bodies
// =============================================================================

const rigidBodies: PhysicsBodyEntity[] = [
    { id: id('b1'), type: 'rigidBody', x: -100, y: 0, mass: 1, shape: 'circle', velocity: { x: 0, y: 0 } },
    { id: id('b2'), type: 'rigidBody', x: 0, y: 0, mass: 2, shape: 'circle', velocity: { x: 0, y: 0 } },
    { id: id('b3'), type: 'rigidBody', x: 100, y: 0, mass: 4, shape: 'circle', velocity: { x: 0, y: 0 } },
];

export const RigidBodies: Story = {
    args: {
        entities: rigidBodies,
        width: 500,
        height: 200,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'Rigid bodies with varying masses. Size scales with mass.',
            },
        },
    },
};

// =============================================================================
// Body Shapes
// =============================================================================

const bodyShapes: PhysicsBodyEntity[] = [
    { id: id('s1'), type: 'rigidBody', x: -100, y: 0, mass: 2, shape: 'circle', velocity: { x: 0, y: 0 } },
    { id: id('s2'), type: 'rigidBody', x: 0, y: 0, mass: 2, shape: 'rect', width: 60, height: 40, velocity: { x: 0, y: 0 } },
    {
        id: id('s3'), type: 'rigidBody', x: 100, y: 0, mass: 2, shape: 'polygon', vertices: [
            { x: 0, y: -30 },
            { x: 30, y: 15 },
            { x: -30, y: 15 },
        ], velocity: { x: 0, y: 0 }
    },
];

export const BodyShapes: Story = {
    args: {
        entities: bodyShapes,
        width: 500,
        height: 200,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'Different body shapes: circle, rectangle, and polygon (triangle).',
            },
        },
    },
};

// =============================================================================
// Springs
// =============================================================================

const springBodies: PhysicsBodyEntity[] = [
    { id: id('sb1'), type: 'rigidBody', x: -100, y: 0, mass: 1, shape: 'circle', velocity: { x: 0, y: 0 }, pinned: true },
    { id: id('sb2'), type: 'rigidBody', x: 0, y: 50, mass: 1, shape: 'circle', velocity: { x: 0, y: 0 } },
    { id: id('sb3'), type: 'rigidBody', x: 100, y: 0, mass: 1, shape: 'circle', velocity: { x: 0, y: 0 }, pinned: true },
];

const springs: PhysicsSpringEntity[] = [
    { id: id('sp1'), type: 'spring', x: 0, y: 0, body1Id: id('sb1'), body2Id: id('sb2'), stiffness: 0.5, restLength: 80 },
    { id: id('sp2'), type: 'spring', x: 0, y: 0, body1Id: id('sb2'), body2Id: id('sb3'), stiffness: 0.5, restLength: 80 },
];

export const SpringSystem: Story = {
    args: {
        entities: [...springBodies, ...springs],
        width: 500,
        height: 250,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'Two fixed bodies connected to a movable body via springs.',
            },
        },
    },
};

// =============================================================================
// Vectors
// =============================================================================

const vectorBody: PhysicsBodyEntity = {
    id: id('vb1'),
    type: 'rigidBody',
    x: 0,
    y: 0,
    mass: 2,
    shape: 'circle',
    velocity: { x: 0, y: 0 },
};

const vectors: PhysicsVectorEntity[] = [
    { id: id('v1'), type: 'vector', x: 0, y: 0, attachedToId: id('vb1'), dx: 80, dy: -40, vectorType: 'velocity', label: 'v' },
    { id: id('v2'), type: 'vector', x: 0, y: 0, attachedToId: id('vb1'), dx: 40, dy: 60, vectorType: 'force', label: 'F' },
    { id: id('v3'), type: 'vector', x: 0, y: 0, attachedToId: id('vb1'), dx: -60, dy: -30, vectorType: 'acceleration', label: 'a' },
];

export const VectorTypes: Story = {
    args: {
        entities: [vectorBody, ...vectors],
        width: 400,
        height: 300,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'Different vector types: velocity (blue), force (red), and acceleration (yellow).',
            },
        },
    },
};

// =============================================================================
// Particles
// =============================================================================

const particles: PhysicsParticleEntity[] = [];
for (let i = 0; i < 30; i++) {
    const angle = (i / 30) * Math.PI * 2;
    const radius = 50 + Math.random() * 50;
    particles.push({
        id: id(`p${i}`),
        type: 'particle',
        x: Math.cos(angle) * radius,
        y: Math.sin(angle) * radius,
        velocity: { x: Math.cos(angle) * 20, y: Math.sin(angle) * 20 },
        lifetime: 1 + Math.random(),
        age: Math.random(),
        color: `hsl(${(i / 30) * 360}, 70%, 60%)`,
    });
}

export const ParticleSystem: Story = {
    args: {
        entities: particles,
        width: 400,
        height: 400,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'A ring of particles with varying colors and lifetimes.',
            },
        },
    },
};

// =============================================================================
// Projectile Motion
// =============================================================================

const projectile: PhysicsBodyEntity = {
    id: id('proj'),
    type: 'rigidBody',
    x: -150,
    y: 50,
    mass: 1,
    shape: 'circle',
    velocity: { x: 50, y: -80 },
};

const projectileVectors: PhysicsVectorEntity[] = [
    { id: id('pv1'), type: 'vector', x: 0, y: 0, attachedToId: id('proj'), dx: 50, dy: -80, vectorType: 'velocity', label: 'v₀' },
    { id: id('pv2'), type: 'vector', x: 0, y: 0, attachedToId: id('proj'), dx: 0, dy: 40, vectorType: 'force', label: 'mg' },
];

// Trajectory path as particles
const trajectoryPoints: PhysicsParticleEntity[] = [];
for (let t = 0; t < 10; t++) {
    const x = -150 + 50 * t * 0.3;
    const y = 50 - 80 * t * 0.3 + 0.5 * 9.8 * (t * 0.3) ** 2 * 4;
    trajectoryPoints.push({
        id: id(`tp${t}`),
        type: 'particle',
        x,
        y,
        velocity: { x: 0, y: 0 },
        lifetime: 1,
        age: 0,
        color: 'rgba(100, 149, 237, 0.5)',
        size: 4,
    });
}

export const ProjectileMotion: Story = {
    args: {
        entities: [projectile, ...projectileVectors, ...trajectoryPoints],
        width: 600,
        height: 350,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'A projectile with initial velocity and gravity force vectors, showing trajectory path.',
            },
        },
    },
};

// =============================================================================
// Pendulum
// =============================================================================

const pivot: PhysicsBodyEntity = {
    id: id('pivot'),
    type: 'rigidBody',
    x: 0,
    y: -80,
    mass: 0.1,
    shape: 'circle',
    velocity: { x: 0, y: 0 },
    pinned: true,
};

const pendulumBob: PhysicsBodyEntity = {
    id: id('bob'),
    type: 'rigidBody',
    x: 60,
    y: 60,
    mass: 3,
    shape: 'circle',
    velocity: { x: 0, y: 0 },
};

const pendulumRod: PhysicsSpringEntity = {
    id: id('rod'),
    type: 'spring',
    x: 0,
    y: 0,
    body1Id: id('pivot'),
    body2Id: id('bob'),
    stiffness: 1,
    restLength: 150,
};

const pendulumVectors: PhysicsVectorEntity[] = [
    { id: id('tension'), type: 'vector', x: 0, y: 0, attachedToId: id('bob'), dx: -30, dy: -70, vectorType: 'force', label: 'T' },
    { id: id('weight'), type: 'vector', x: 0, y: 0, attachedToId: id('bob'), dx: 0, dy: 50, vectorType: 'force', label: 'W' },
];

export const Pendulum: Story = {
    args: {
        entities: [pivot, pendulumBob, pendulumRod, ...pendulumVectors],
        width: 400,
        height: 350,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'A simple pendulum with tension and weight force vectors.',
            },
        },
    },
};

// =============================================================================
// Collision Scene
// =============================================================================

const collisionBodies: PhysicsBodyEntity[] = [
    { id: id('c1'), type: 'rigidBody', x: -80, y: 0, mass: 2, shape: 'circle', velocity: { x: 30, y: 0 }, highlighted: true },
    { id: id('c2'), type: 'rigidBody', x: 80, y: 0, mass: 2, shape: 'circle', velocity: { x: -30, y: 0 }, highlighted: true },
];

const collisionVectors: PhysicsVectorEntity[] = [
    { id: id('cv1'), type: 'vector', x: 0, y: 0, attachedToId: id('c1'), dx: 40, dy: 0, vectorType: 'velocity', label: 'v₁' },
    { id: id('cv2'), type: 'vector', x: 0, y: 0, attachedToId: id('c2'), dx: -40, dy: 0, vectorType: 'velocity', label: 'v₂' },
];

export const Collision: Story = {
    args: {
        entities: [...collisionBodies, ...collisionVectors],
        width: 500,
        height: 200,
        domain: 'physics',
    },
    parameters: {
        docs: {
            description: {
                story: 'Two bodies approaching each other for an elastic collision.',
            },
        },
    },
};
