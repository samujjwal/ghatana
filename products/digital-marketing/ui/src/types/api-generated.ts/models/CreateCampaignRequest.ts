/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CreateCampaignRequest = {
    name: string;
    type: CreateCampaignRequest.type;
};
export namespace CreateCampaignRequest {
    export enum type {
        EMAIL = 'EMAIL',
        SOCIAL = 'SOCIAL',
        PAID_SEARCH = 'PAID_SEARCH',
        PUSH = 'PUSH',
        SMS = 'SMS',
        OMNICHANNEL = 'OMNICHANNEL',
    }
}

