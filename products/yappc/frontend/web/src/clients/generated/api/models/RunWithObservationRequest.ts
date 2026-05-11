/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type RunWithObservationRequest = {
    runSpec: {
        id: string;
        artifactsRef?: string;
        tasks?: Array<Record<string, any>>;
        environment?: string;
        config?: Record<string, string>;
    };
    observationConfig?: Record<string, any>;
};

