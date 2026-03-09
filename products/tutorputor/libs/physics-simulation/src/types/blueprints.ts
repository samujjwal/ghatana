import { PhysicsEntity, PhysicsConfig } from './entities';

/**
 * @doc.type interface
 * @doc.purpose Input parameter definition for a blueprint
 * @doc.layer core
 */
export interface BlueprintParameter {
    name: string;
    label: string;
    type: 'number' | 'boolean' | 'select';
    defaultValue: number | boolean | string;
    min?: number;
    max?: number;
    options?: string[]; // For select type
    unit?: string;
}

/**
 * @doc.type interface
 * @doc.purpose Definition of a parametric simulation generator
 * @doc.layer core
 */
export interface SimulationBlueprint {
    id: string;
    name: string;
    description: string;
    category: 'PHYSICS' | 'MATH' | 'MECHANICS' | 'OPTICS';
    /** Parameters required to generate this simulation */
    parameters: BlueprintParameter[];
    /** Function to generate the simulation manifest based on inputs */
    generate: (inputs: Record<string, number | boolean | string>) => {
        entities: PhysicsEntity[];
        config: PhysicsConfig;
    };
}
