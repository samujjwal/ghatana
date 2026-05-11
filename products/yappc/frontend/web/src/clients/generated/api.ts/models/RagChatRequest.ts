/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type RagChatRequest = {
    messages: Array<{
        role: 'user' | 'assistant' | 'system';
        content: string;
    }>;
    topK?: number;
};

