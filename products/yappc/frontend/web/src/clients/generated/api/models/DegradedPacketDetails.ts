/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type DegradedPacketDetails = {
    dependency: DegradedPacketDetails.dependency;
    reason: string;
    truthSource: string;
    recoveryAction: string;
    impactedFeatures: Array<string>;
};
export namespace DegradedPacketDetails {
    export enum dependency {
        DATA_CLOUD = 'DATA_CLOUD',
        AEP = 'AEP',
        KERNEL = 'KERNEL',
    }
}

