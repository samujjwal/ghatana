/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type DashboardFreshness = {
    lastUpdated: string;
    stalenessSeconds: number;
    status: DashboardFreshness.status;
};
export namespace DashboardFreshness {
    export enum status {
        FRESH = 'FRESH',
        STALE = 'STALE',
        VERY_STALE = 'VERY_STALE',
        CRITICAL = 'CRITICAL',
    }
}

