/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ActivityFeedEntry = {
    id: string;
    type: string;
    action: string;
    summary: string;
    actor: string;
    timestamp: string;
    severity: ActivityFeedEntry.severity;
};
export namespace ActivityFeedEntry {
    export enum severity {
        INFO = 'INFO',
        WARNING = 'WARNING',
        ERROR = 'ERROR',
    }
}

