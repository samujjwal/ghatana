/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DeleteMyDataResponse } from '../models/DeleteMyDataResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserDataService {
    /**
     * Request deletion of the current user's data
     * @returns DeleteMyDataResponse Data deletion request accepted
     * @throws ApiError
     */
    public static requestCurrentUserDataDeletion(): CancelablePromise<DeleteMyDataResponse> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/users/me/data',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
}
