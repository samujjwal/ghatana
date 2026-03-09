/**
 * Flashit API Client - shared HTTP client for all applications
 */

import type {
    User,
    AuthResponse,
    LoginRequest,
    RegisterRequest,
    Sphere,
    CreateSphereRequest,
    UpdateSphereRequest,
    Moment,
    CreateMomentRequest,
    UpdateMomentRequest,
    SearchMomentsRequest,
    SearchMomentsResponse,
    ClassifySphereRequest,
    ClassifySphereResponse,
    UploadUrlRequest,
    UploadUrlResponse,
} from '../types';

export interface FlashitApiClientConfig {
    baseURL: string;
    getToken?: () => Promise<string | null>;
    onTokenChange?: (token: string | null) => Promise<void>;
    onUnauthorized?: () => void;
}

export class FlashitApiClient {
    private baseURL: string;
    private getToken?: () => Promise<string | null>;
    private onTokenChange?: (token: string | null) => Promise<void>;
    private onUnauthorized?: () => void;

    constructor(config: FlashitApiClientConfig) {
        this.baseURL = config.baseURL.replace(/\/$/, '');
        this.getToken = config.getToken;
        this.onTokenChange = config.onTokenChange;
        this.onUnauthorized = config.onUnauthorized;
    }

    protected async request<T>(
        endpoint: string,
        options: RequestInit = {}
    ): Promise<T> {
        const url = `${this.baseURL}${endpoint}`;
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            ...((options.headers as Record<string, string>) || {}),
        };

        // Add auth token if available
        if (this.getToken) {
            const token = await this.getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
        }

        const response = await fetch(url, {
            ...options,
            headers,
        });

        if (response.status === 401 && this.onUnauthorized) {
            this.onUnauthorized();
            throw new Error('Unauthorized');
        }

        if (!response.ok) {
            const error = await response.json().catch(() => ({
                error: 'Unknown error',
                message: response.statusText,
            }));
            throw new Error(error.message || 'Request failed');
        }

        return response.json();
    }

    // Auth endpoints
    async login(email: string, password: string): Promise<AuthResponse>;
    async login(data: LoginRequest): Promise<AuthResponse>;
    async login(emailOrData: string | LoginRequest, password?: string): Promise<AuthResponse> {
        const data: LoginRequest = typeof emailOrData === 'string'
            ? { email: emailOrData, password: password! }
            : emailOrData;

        const response = await this.request<AuthResponse>('/auth/login', {
            method: 'POST',
            body: JSON.stringify(data),
        });

        if (this.onTokenChange && response.accessToken) {
            await this.onTokenChange(response.accessToken);
        }

        return response;
    }

    async register(email: string, password: string, displayName?: string): Promise<AuthResponse>;
    async register(data: RegisterRequest): Promise<AuthResponse>;
    async register(emailOrData: string | RegisterRequest, password?: string, displayName?: string): Promise<AuthResponse> {
        const data: RegisterRequest = typeof emailOrData === 'string'
            ? { email: emailOrData, password: password!, displayName }
            : emailOrData;

        const response = await this.request<AuthResponse>('/auth/register', {
            method: 'POST',
            body: JSON.stringify(data),
        });

        if (this.onTokenChange && response.accessToken) {
            await this.onTokenChange(response.accessToken);
        }

        return response;
    }

    async logout(): Promise<void> {
        if (this.onTokenChange) {
            await this.onTokenChange(null);
        }
    }

    async setToken(token: string): Promise<void> {
        if (this.onTokenChange) {
            await this.onTokenChange(token);
        }
    }

    async clearToken(): Promise<void> {
        if (this.onTokenChange) {
            await this.onTokenChange(null);
        }
    }

    async getCurrentUser(): Promise<User> {
        const response = await this.request<{ user: User }>('/auth/me');
        return response.user;
    }

    // Sphere endpoints
    async getSpheres(): Promise<Sphere[]> {
        return this.request<Sphere[]>('/api/spheres');
    }

    async getSphere(id: string): Promise<Sphere> {
        return this.request<Sphere>(`/api/spheres/${id}`);
    }

    async createSphere(data: CreateSphereRequest): Promise<Sphere> {
        return this.request<Sphere>('/api/spheres', {
            method: 'POST',
            body: JSON.stringify(data),
        });
    }

    async updateSphere(id: string, data: UpdateSphereRequest): Promise<Sphere> {
        return this.request<Sphere>(`/api/spheres/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        });
    }

    async deleteSphere(id: string): Promise<void> {
        await this.request<void>(`/api/spheres/${id}`, {
            method: 'DELETE',
        });
    }

    // Moment endpoints
    async getMoments(params?: SearchMomentsRequest): Promise<SearchMomentsResponse> {
        const queryParams = new URLSearchParams();
        if (params?.sphereIds) queryParams.append('sphereIds', params.sphereIds.join(','));
        if (params?.query) queryParams.append('query', params.query);
        if (params?.tags) queryParams.append('tags', params.tags.join(','));
        if (params?.emotions) queryParams.append('emotions', params.emotions.join(','));
        if (params?.startDate) queryParams.append('startDate', params.startDate);
        if (params?.endDate) queryParams.append('endDate', params.endDate);
        if (params?.limit) queryParams.append('limit', params.limit.toString());
        if (params?.cursor) queryParams.append('cursor', params.cursor);

        const query = queryParams.toString();
        return this.request<SearchMomentsResponse>(
            `/api/moments${query ? `?${query}` : ''}`
        );
    }

    async getMoment(id: string): Promise<Moment> {
        return this.request<Moment>(`/api/moments/${id}`);
    }

    async createMoment(data: CreateMomentRequest): Promise<Moment> {
        return this.request<Moment>('/api/moments', {
            method: 'POST',
            body: JSON.stringify(data),
        });
    }

    async updateMoment(id: string, data: UpdateMomentRequest): Promise<Moment> {
        return this.request<Moment>(`/api/moments/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        });
    }

    async deleteMoment(id: string): Promise<void> {
        await this.request<void>(`/api/moments/${id}`, {
            method: 'DELETE',
        });
    }

    /**
     * Enhanced AI Search
     * Uses vector embeddings and hybrid search
     */
    async search(params: any): Promise<any> {
        return this.request<any>('/api/search', {
            method: 'POST',
            body: JSON.stringify(params),
        });
    }

    /**
     * @deprecated Use search() instead
     */
    async searchMoments(query: string): Promise<Moment[]> {
        const response = await this.request<{ moments: Moment[] }>(
            `/api/moments?query=${encodeURIComponent(query)}`
        );
        return response.moments;
    }

    async classifySphere(data: ClassifySphereRequest): Promise<ClassifySphereResponse> {
        return this.request<ClassifySphereResponse>('/api/moments/classify-sphere', {
            method: 'POST',
            body: JSON.stringify(data),
        });
    }

    // Upload endpoints
    async getUploadUrl(data: UploadUrlRequest): Promise<UploadUrlResponse> {
        return this.request<UploadUrlResponse>('/api/upload/url', {
            method: 'POST',
            body: JSON.stringify(data),
        });
    }
}
