/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type UpdateSprintRequest = {
    name?: string;
    startDate?: string;
    endDate?: string;
    goal?: string;
    status?: UpdateSprintRequest.status;
};
export namespace UpdateSprintRequest {
    export enum status {
        PLANNED = 'planned',
        ACTIVE = 'active',
        COMPLETED = 'completed',
        CANCELLED = 'cancelled',
    }
}

