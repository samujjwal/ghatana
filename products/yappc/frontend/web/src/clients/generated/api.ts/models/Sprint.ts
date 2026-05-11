/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type Sprint = {
    id: string;
    projectId: string;
    name: string;
    status: Sprint.status;
    startDate: string;
    endDate: string;
    goal?: string;
};
export namespace Sprint {
    export enum status {
        PLANNED = 'planned',
        ACTIVE = 'active',
        COMPLETED = 'completed',
        CANCELLED = 'cancelled',
    }
}

