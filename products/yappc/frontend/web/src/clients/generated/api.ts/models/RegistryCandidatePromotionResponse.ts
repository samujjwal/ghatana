/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type RegistryCandidatePromotionResponse = {
    candidateId: string;
    artifactId: string;
    residualIslandId: string;
    proposedContractName: string;
    status: RegistryCandidatePromotionResponse.status;
    auditRecordId: string;
    auditRecorded: boolean;
    createdAt: string;
};
export namespace RegistryCandidatePromotionResponse {
    export enum status {
        NEEDS_REVIEW = 'NEEDS_REVIEW',
    }
}

