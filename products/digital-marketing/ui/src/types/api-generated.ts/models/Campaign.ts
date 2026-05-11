/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type Campaign = {
    /**
     * Unique campaign identifier
     */
    id: string;
    /**
     * Workspace scope
     */
    workspaceId: string;
    /**
     * Campaign name
     */
    name: string;
    /**
     * Campaign lifecycle status (P0-010)
     */
    status: Campaign.status;
    /**
     * Campaign channel type (P0-010)
     */
    type: Campaign.type;
    /**
     * Principal ID of the creator
     */
    createdBy: string;
    /**
     * ISO 8601 timestamp
     */
    createdAt: string;
    /**
     * ISO 8601 timestamp
     */
    updatedAt: string;
};
export namespace Campaign {
    /**
     * Campaign lifecycle status (P0-010)
     */
    export enum status {
        DRAFT = 'DRAFT',
        PENDING_APPROVAL = 'PENDING_APPROVAL',
        APPROVED = 'APPROVED',
        PENDING_LAUNCH = 'PENDING_LAUNCH',
        LAUNCH_RUNNING = 'LAUNCH_RUNNING',
        LAUNCH_FAILED = 'LAUNCH_FAILED',
        EXTERNAL_EXECUTION_BLOCKED = 'EXTERNAL_EXECUTION_BLOCKED',
        LAUNCHED = 'LAUNCHED',
        PAUSED = 'PAUSED',
        COMPLETED = 'COMPLETED',
        ARCHIVED = 'ARCHIVED',
        ROLLED_BACK = 'ROLLED_BACK',
    }
    /**
     * Campaign channel type (P0-010)
     */
    export enum type {
        EMAIL = 'EMAIL',
        SOCIAL = 'SOCIAL',
        PAID_SEARCH = 'PAID_SEARCH',
        PUSH = 'PUSH',
        SMS = 'SMS',
        OMNICHANNEL = 'OMNICHANNEL',
    }
}

