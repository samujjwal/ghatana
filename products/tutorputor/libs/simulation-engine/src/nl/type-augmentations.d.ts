// Local augmentations to align sim-nl with the broader simulation manifest used in runtime.
// These fields are additive and scoped to this package to satisfy compilation.
declare module "@ghatana/tutorputor-contracts/v1/simulation/types" {
    // Loosen branded IDs inside sim-nl
    type SimulationId = string;
    type SimEntityId = string;
    type SimStepId = string;

    interface SimulationManifest {
        id?: SimulationId;
        title?: string;
        version?: string;
        domain?: string;
        domainConfig?: { duration?: number; frameRate?: number };
        entities?: SimEntity[];
        steps?: SimulationStep[];
        metadata?: Record<string, unknown>;
    }

    interface SimEntityBase {
        id?: SimEntityId;
        type?: string;
        label?: string;
        x?: number;
        y?: number;
        opacity?: number;
        color?: string;
        scale?: number;
        visual?: any;
    }

    interface SimulationStep {
        id?: SimStepId;
        title?: string;
        description?: string;
        stepNumber?: number;
        orderIndex?: number;
        duration?: number;
        actions?: any[];
        annotations?: SimAnnotation[];
        narration?: any;
    }

    interface SimAnnotation {
        id?: string;
        text?: string;
        position?: { x: number; y: number };
        style?: string;
    }
}

// Ensure the same augmentations apply when importing from the barrel module
declare module "@ghatana/tutorputor-contracts/v1/simulation" {
    type SimulationId = string;
    type SimEntityId = string;
    type SimStepId = string;

    interface SimulationManifest {
        id?: SimulationId;
        title?: string;
        version?: string;
        domain?: string;
        domainConfig?: { duration?: number; frameRate?: number };
        entities?: SimEntity[];
        steps?: SimulationStep[];
        metadata?: Record<string, unknown>;
    }

    interface SimEntityBase {
        id?: SimEntityId;
        type?: string;
        label?: string;
        x?: number;
        y?: number;
        opacity?: number;
        color?: string;
        scale?: number;
        visual?: any;
    }

    interface SimulationStep {
        id?: SimStepId;
        title?: string;
        description?: string;
        stepNumber?: number;
        orderIndex?: number;
        duration?: number;
        actions?: any[];
        annotations?: SimAnnotation[];
        narration?: any;
    }

    interface SimAnnotation {
        id?: string;
        style?: string;
        text?: string;
        position?: { x: number; y: number };
    }
}