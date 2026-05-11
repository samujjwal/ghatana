/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AuthUser } from './AuthUser';
export type LoginResponse = {
    user: AuthUser;
    session?: {
        expiresAt?: string;
        authMode?: LoginResponse.authMode;
    };
};
export namespace LoginResponse {
    export enum authMode {
        COOKIE = 'COOKIE',
    }
}

