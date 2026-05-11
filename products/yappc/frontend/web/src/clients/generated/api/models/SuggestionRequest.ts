/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type SuggestionRequest = {
    phase: SuggestionRequest.phase;
    /**
     * Additional domain context forwarded to the AI model
     */
    context?: Record<string, any>;
};
export namespace SuggestionRequest {
    export enum phase {
        INTENT = 'INTENT',
        SHAPE = 'SHAPE',
        VALIDATE = 'VALIDATE',
        GENERATE = 'GENERATE',
        RUN = 'RUN',
        OBSERVE = 'OBSERVE',
        LEARN = 'LEARN',
        EVOLVE = 'EVOLVE',
    }
}

