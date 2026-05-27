/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type PhaseAction = {
    actionId: string;
    label: string;
    description: string;
    enabled: boolean;
    disabledReason?: string;
    requiredPermission: string;
    category: string;
    severity: string;
    confirmationRequired: boolean;
    idempotencyKey: string;
    auditType: string;
    parameters: Record<string, any>;
};

