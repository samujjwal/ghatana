/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SourceImportJob } from './SourceImportJob';
export type SourceImportResponse = {
    success: boolean;
    componentId?: string;
    files: Array<{
        path: string;
        content: string;
        type: 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';
        source?: string;
    }>;
    warnings: Array<string>;
    errors: Array<string>;
    metadata: {
        sourceType: string;
        source: string;
        importedAt: string;
        componentName?: string;
        dependencies: Array<string>;
        fileCount: number;
        totalSize: number;
    };
    job?: SourceImportJob;
};

