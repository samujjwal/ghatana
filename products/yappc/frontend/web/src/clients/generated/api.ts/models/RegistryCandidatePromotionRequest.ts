/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type RegistryCandidatePromotionRequest = {
    proposedContractName: string;
    source: RegistryCandidatePromotionRequest.source;
    notes?: string;
};
export namespace RegistryCandidatePromotionRequest {
    export enum source {
        DECOMPILED_IMPORT = 'decompiled-import',
    }
}

