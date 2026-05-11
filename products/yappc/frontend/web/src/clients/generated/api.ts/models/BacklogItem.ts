/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type BacklogItem = {
    id: string;
    title: string;
    type: BacklogItem.type;
    priority?: BacklogItem.priority;
    sprintId?: string | null;
    storyPoints?: number;
    assigneeId?: string;
};
export namespace BacklogItem {
    export enum type {
        STORY = 'story',
        TASK = 'task',
        BUG = 'bug',
        EPIC = 'epic',
    }
    export enum priority {
        LOW = 'low',
        MEDIUM = 'medium',
        HIGH = 'high',
        CRITICAL = 'critical',
    }
}

