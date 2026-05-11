/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Campaign } from './Campaign';
export type CampaignListResponse = {
    /**
     * List of campaigns (paginated)
     */
    items: Array<Campaign>;
    /**
     * Number of items in this response
     */
    count: number;
    /**
     * Pagination offset used for this query
     */
    offset: number;
};

