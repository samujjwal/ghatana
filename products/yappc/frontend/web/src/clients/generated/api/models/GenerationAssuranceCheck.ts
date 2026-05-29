/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type GenerationAssuranceCheck = {
    /**
     * Identifier for the assurance check type
     */
    id: GenerationAssuranceCheck.id;
    /**
     * Whether this specific check passed
     */
    passed: boolean;
    /**
     * List of failure messages if the check did not pass
     */
    failures: Array<string>;
};
export namespace GenerationAssuranceCheck {
    /**
     * Identifier for the assurance check type
     */
    export enum id {
        COMPILE = 'compile',
        TEST = 'test',
        STATIC = 'static',
        SECURITY = 'security',
        I18N = 'i18n',
        A11Y = 'a11y',
    }
}

