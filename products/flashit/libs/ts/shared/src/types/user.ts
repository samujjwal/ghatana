/**
 * User-related types shared across Flashit applications
 */

export interface User {
    id: string;
    email: string;
    displayName: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface AuthTokens {
    accessToken: string;
    refreshToken?: string;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
    displayName?: string;
}

export interface AuthResponse {
    user: User;
    accessToken: string;
    refreshToken?: string;
    sessionId?: string;
    expiresIn?: number;
}
