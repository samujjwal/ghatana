import { z } from 'zod';

import {
  ShareToken,
  UserPermission,
} from '../schemas/permission-schemas';

import type {
  UserRole,
  PermissionScope} from '../schemas/permission-schemas';

// Extended types for server implementation
/**
 *
 */
export interface CanvasPermission {
  id: string;
  canvasId: string;
  userId: string;
  email: string;
  role: UserRole;
  permissions: PermissionScope;
  grantedBy: string;
  grantedAt: string;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

/**
 *
 */
export interface ExtendedShareToken {
  id: string;
  token: string;
  canvasId: string;
  permissions: PermissionScope;
  createdBy: string;
  createdAt: string;
  expiresAt?: string;
  maxUses?: number;
  currentUses: number;
  isActive: boolean;
}

/**
 *
 */
export interface CreatePermissionRequest {
  canvasId: string;
  userId: string;
  email: string;
  role: UserRole;
  requestedBy: string;
}

// Mock server responses for collaboration system
/**
 *
 */
export interface CollaborationServer {
  // Permission management
  checkPermission(userId: string, canvasId: string, action: string): Promise<boolean>;
  createPermission(request: CreatePermissionRequest): Promise<CanvasPermission>;
  updatePermission(permissionId: string, updates: Partial<CanvasPermission>): Promise<CanvasPermission>;
  deletePermission(permissionId: string): Promise<void>;
  getCanvasPermissions(canvasId: string): Promise<CanvasPermission[]>;
  getUserPermissions(userId: string): Promise<CanvasPermission[]>;
  
  // Share tokens
  createShareToken(canvasId: string, permissions: PermissionScope, expiresAt?: Date): Promise<ExtendedShareToken>;
  validateShareToken(token: string): Promise<ExtendedShareToken | null>;
  deleteShareToken(tokenId: string): Promise<void>;
  
  // User management
  getActiveUsers(canvasId: string): Promise<string[]>;
  getUserPresence(userId: string): Promise<{ status: 'online' | 'offline' | 'away'; lastSeen: Date }>;
}

// Mock implementation for development
/**
 *
 */
export class MockCollaborationServer implements CollaborationServer {
  private permissions = new Map<string, CanvasPermission>();
  private shareTokens = new Map<string, ExtendedShareToken>();
  private activeUsers = new Map<string, Set<string>>(); // canvasId -> Set<userId>
  private userPresence = new Map<string, { status: 'online' | 'offline' | 'away'; lastSeen: Date }>();

  /**
   *
   */
  async checkPermission(userId: string, canvasId: string, action: string): Promise<boolean> {
    const userPermissions = Array.from(this.permissions.values())
      .filter(p => p.userId === userId && p.canvasId === canvasId);
    
    if (userPermissions.length === 0) return false;
    
    const permission = userPermissions[0];
    const scope = permission.permissions;
    
    switch (action) {
      case 'view':
      case 'read':
        return scope.canRead;
      case 'edit':
        return scope.canEdit;
      case 'comment':
        return scope.canComment;
      case 'export':
        return scope.canExport;
      case 'manage':
        return scope.canManagePermissions;
      case 'delete':
        return scope.canDelete;
      case 'share':
        return scope.canShare;
      case 'invite':
        return scope.canInvite;
      default:
        return false;
    }
  }

  /**
   *
   */
  async createPermission(request: CreatePermissionRequest): Promise<CanvasPermission> {
    const permission: CanvasPermission = {
      id: `perm_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      userId: request.userId,
      email: request.email,
      canvasId: request.canvasId,
      role: request.role,
      permissions: this.getRolePermissions(request.role),
      grantedBy: request.requestedBy,
      grantedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      createdBy: request.requestedBy,
    };
    
    this.permissions.set(permission.id, permission);
    return permission;
  }

  /**
   *
   */
  async updatePermission(permissionId: string, updates: Partial<CanvasPermission>): Promise<CanvasPermission> {
    const existing = this.permissions.get(permissionId);
    if (!existing) {
      throw new Error(`Permission ${permissionId} not found`);
    }
    
    const updated = {
      ...existing,
      ...updates,
      updatedAt: new Date().toISOString(),
    };
    
    this.permissions.set(permissionId, updated);
    return updated;
  }

  /**
   *
   */
  async deletePermission(permissionId: string): Promise<void> {
    this.permissions.delete(permissionId);
  }

  /**
   *
   */
  async getCanvasPermissions(canvasId: string): Promise<CanvasPermission[]> {
    return Array.from(this.permissions.values())
      .filter(p => p.canvasId === canvasId);
  }

  /**
   *
   */
  async getUserPermissions(userId: string): Promise<CanvasPermission[]> {
    return Array.from(this.permissions.values())
      .filter(p => p.userId === userId);
  }

  /**
   *
   */
  async createShareToken(canvasId: string, permissions: PermissionScope, expiresAt?: Date, createdBy = 'system'): Promise<ExtendedShareToken> {
    const token: ExtendedShareToken = {
      id: `token_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      token: `st_${Math.random().toString(36).substr(2, 32)}`,
      canvasId,
      permissions,
      createdBy,
      createdAt: new Date().toISOString(),
      expiresAt: expiresAt?.toISOString(),
      maxUses: undefined,
      currentUses: 0,
      isActive: true,
    };
    
    this.shareTokens.set(token.id, token);
    return token;
  }

  /**
   *
   */
  async validateShareToken(token: string): Promise<ExtendedShareToken | null> {
    const shareToken = Array.from(this.shareTokens.values())
      .find(t => t.token === token);
    
    if (!shareToken || !shareToken.isActive) return null;
    
    if (shareToken.expiresAt && new Date() > new Date(shareToken.expiresAt)) {
      shareToken.isActive = false;
      return null;
    }
    
    if (shareToken.maxUses && shareToken.currentUses >= shareToken.maxUses) {
      shareToken.isActive = false;
      return null;
    }
    
    shareToken.currentUses++;
    return shareToken;
  }

  /**
   *
   */
  async deleteShareToken(tokenId: string): Promise<void> {
    this.shareTokens.delete(tokenId);
  }

  /**
   *
   */
  async getActiveUsers(canvasId: string): Promise<string[]> {
    return Array.from(this.activeUsers.get(canvasId) || new Set());
  }

  /**
   *
   */
  async getUserPresence(userId: string): Promise<{ status: 'online' | 'offline' | 'away'; lastSeen: Date }> {
    return this.userPresence.get(userId) || { 
      status: 'offline', 
      lastSeen: new Date(Date.now() - 24 * 60 * 60 * 1000) 
    };
  }

  // Helper methods
  /**
   *
   */
  private getRolePermissions(role: UserRole): PermissionScope {
    switch (role) {
      case 'owner':
        return {
          canRead: true,
          canEdit: true,
          canDelete: true,
          canComment: true,
          canShare: true,
          canInvite: true,
          canManagePermissions: true,
          canExport: true,
        };
      case 'admin':
        return {
          canRead: true,
          canEdit: true,
          canDelete: false,
          canComment: true,
          canShare: true,
          canInvite: true,
          canManagePermissions: true,
          canExport: true,
        };
      case 'editor':
        return {
          canRead: true,
          canEdit: true,
          canDelete: false,
          canComment: true,
          canShare: false,
          canInvite: false,
          canManagePermissions: false,
          canExport: true,
        };
      case 'viewer':
        return {
          canRead: true,
          canEdit: false,
          canDelete: false,
          canComment: false,
          canShare: false,
          canInvite: false,
          canManagePermissions: false,
          canExport: false,
        };
      case 'commenter':
        return {
          canRead: true,
          canEdit: false,
          canDelete: false,
          canComment: true,
          canShare: false,
          canInvite: false,
          canManagePermissions: false,
          canExport: false,
        };
      default:
        return {
          canRead: false,
          canEdit: false,
          canDelete: false,
          canComment: false,
          canShare: false,
          canInvite: false,
          canManagePermissions: false,
          canExport: false,
        };
    }
  }

  // Simulation methods for testing
  /**
   *
   */
  public addActiveUser(canvasId: string, userId: string): void {
    if (!this.activeUsers.has(canvasId)) {
      this.activeUsers.set(canvasId, new Set());
    }
    this.activeUsers.get(canvasId)!.add(userId);
    this.userPresence.set(userId, { status: 'online', lastSeen: new Date() });
  }

  /**
   *
   */
  public removeActiveUser(canvasId: string, userId: string): void {
    this.activeUsers.get(canvasId)?.delete(userId);
    this.userPresence.set(userId, { status: 'offline', lastSeen: new Date() });
  }

  /**
   *
   */
  public setUserStatus(userId: string, status: 'online' | 'offline' | 'away'): void {
    const current = this.userPresence.get(userId) || { status: 'offline', lastSeen: new Date() };
    this.userPresence.set(userId, { ...current, status, lastSeen: new Date() });
  }
}

// Singleton instance for development
export const mockCollaborationServer = new MockCollaborationServer();