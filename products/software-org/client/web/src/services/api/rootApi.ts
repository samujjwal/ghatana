/**
 * Root API Client
 *
 * <p><b>Purpose</b><br>
 * Typed client for platform-level (root) endpoints.
 *
 * <p><b>Endpoints</b><br>
 * - GET /root/users/search
 * - POST /root/users/:id/suspend
 *
 * @doc.type service
 * @doc.purpose Root API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { apiClient } from './index';

export interface RootUserSearchResult {
    id: string;
    name: string;
    email: string;
    workspaces: Array<{ name: string }>;
}

export interface SuspendUserResponse {
    success: boolean;
    userId: string;
    reason: string;
    status: string;
}

export const rootApi = {
    async searchUsers(query: string): Promise<RootUserSearchResult[]> {
        const response = await apiClient.get<RootUserSearchResult[]>('/root/users/search', {
            params: { q: query },
        });
        return response.data;
    },

    async suspendUser(userId: string, reason: string): Promise<SuspendUserResponse> {
        const response = await apiClient.post<SuspendUserResponse>(`/root/users/${userId}/suspend`, {
            reason,
        });
        return response.data;
    },
};
