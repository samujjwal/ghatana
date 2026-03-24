/**
 * Extension Marketplace Types
 * 
 * Type definitions for plugin discovery, installation, and marketplace integration.
 */

import type { PluginManifest } from './types';

/**
 * Plugin listing in the marketplace
 */
export interface MarketplacePlugin {
  /** Plugin manifest */
  manifest: PluginManifest;
  
  /** Download URL for the plugin bundle */
  downloadUrl: string;
  
  /** Marketplace metadata */
  marketplace: {
    /** Publisher information */
    publisher: {
      id: string;
      name: string;
      verified: boolean;
      website?: string;
    };
    
    /** Download statistics */
    stats: {
      downloads: number;
      rating: number;
      reviews: number;
    };
    
    /** Publication dates */
    publishedAt: Date;
    updatedAt: Date;
    
    /** Available versions */
    versions: Array<{
      version: string;
      releaseDate: Date;
      changelog?: string;
      downloadUrl: string;
    }>;
    
    /** Screenshots and media */
    media?: {
      icon?: string;
      screenshots?: string[];
      video?: string;
    };
    
    /** Tags and categories */
    tags?: string[];
    category?: string;
    
    /** Marketplace flags */
    featured?: boolean;
    verified?: boolean;
    deprecated?: boolean;
  };
}

/**
 * Plugin installation status
 */
export type InstallationStatus =
  | 'pending'
  | 'downloading'
  | 'verifying'
  | 'installing'
  | 'installed'
  | 'failed'
  | 'cancelled';

/**
 * Plugin installation progress
 */
export interface InstallationProgress {
  /** Plugin being installed */
  pluginId: string;
  
  /** Current status */
  status: InstallationStatus;
  
  /** Progress percentage (0-100) */
  progress: number;
  
  /** Current step description */
  step: string;
  
  /** Error message if failed */
  error?: string;
  
  /** Downloaded bytes */
  downloadedBytes?: number;
  
  /** Total bytes to download */
  totalBytes?: number;
}

/**
 * Plugin update information
 */
export interface PluginUpdate {
  /** Plugin ID */
  pluginId: string;
  
  /** Currently installed version */
  currentVersion: string;
  
  /** Latest available version */
  latestVersion: string;
  
  /** Changelog for the update */
  changelog?: string;
  
  /** Whether update is breaking */
  breaking: boolean;
  
  /** Update priority */
  priority: 'critical' | 'high' | 'medium' | 'low';
}

/**
 * Marketplace search filters
 */
export interface SearchFilters {
  /** Search query */
  query?: string;
  
  /** Filter by category */
  category?: string;
  
  /** Filter by tags */
  tags?: string[];
  
  /** Filter by capabilities */
  capabilities?: string[];
  
  /** Only show verified plugins */
  verifiedOnly?: boolean;
  
  /** Only show featured plugins */
  featuredOnly?: boolean;
  
  /** Minimum rating (0-5) */
  minRating?: number;
  
  /** Sort order */
  sortBy?: 'relevance' | 'downloads' | 'rating' | 'newest' | 'updated';
  
  /** Results per page */
  limit?: number;
  
  /** Page offset */
  offset?: number;
}

/**
 * Search results
 */
export interface SearchResults {
  /** Matching plugins */
  plugins: MarketplacePlugin[];
  
  /** Total number of results */
  total: number;
  
  /** Current offset */
  offset: number;
  
  /** Results per page */
  limit: number;
  
  /** Whether there are more results */
  hasMore: boolean;
}

/**
 * Plugin security verification result
 */
export interface SecurityVerification {
  /** Whether plugin passed verification */
  verified: boolean;
  
  /** Verification checks */
  checks: {
    /** Code signature valid */
    signature: boolean;
    
    /** Checksum matches */
    checksum: boolean;
    
    /** No known vulnerabilities */
    vulnerabilities: boolean;
    
    /** Permissions are acceptable */
    permissions: boolean;
  };
  
  /** Security warnings */
  warnings?: string[];
  
  /** Security errors */
  errors?: string[];
  
  /** Verification timestamp */
  verifiedAt: Date;
}

/**
 * Plugin permissions request
 */
export interface PermissionRequest {
  /** Permission identifier */
  permission: string;
  
  /** Human-readable description */
  description: string;
  
  /** Whether permission is required */
  required: boolean;
  
  /** Risk level */
  risk: 'low' | 'medium' | 'high';
}

/**
 * Marketplace configuration
 */
export interface MarketplaceConfig {
  /** Marketplace API endpoint */
  apiUrl: string;
  
  /** API authentication token */
  token?: string;
  
  /** Enable automatic updates */
  autoUpdate: boolean;
  
  /** Check for updates interval (ms) */
  updateCheckInterval: number;
  
  /** Allow beta/pre-release versions */
  allowPrerelease: boolean;
  
  /** Require verified plugins only */
  verifiedOnly: boolean;
  
  /** Download cache directory */
  cacheDir: string;
  
  /** Maximum concurrent downloads */
  maxConcurrentDownloads: number;
}

/**
 * Installation options
 */
export interface InstallOptions {
  /** Specific version to install */
  version?: string;
  
  /** Force reinstall if already installed */
  force?: boolean;
  
  /** Skip dependency installation */
  skipDependencies?: boolean;
  
  /** Skip security verification */
  skipVerification?: boolean;
  
  /** Automatically activate after install */
  autoActivate?: boolean;
}

/**
 * Uninstallation options
 */
export interface UninstallOptions {
  /** Remove plugin data */
  removeData?: boolean;
  
  /** Force uninstall even if dependencies exist */
  force?: boolean;
}
