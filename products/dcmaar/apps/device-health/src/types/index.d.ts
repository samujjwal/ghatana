// Core types for the extension
declare module '@core/types' {
  export * from '../../../core/interfaces';
  export * from '../../../core/storage/types';
}

// Extension types
declare module '@extension/types' {
  export * from '../../../app/background/types';
}

// Communication types
declare module '@communication/types' {
  // Legacy communication types removed - use connector types instead
  // export * from '../../../communication/types';
}

// Feature types
declare module '@features/types' {
  export * from '../../../features/security/types';
  export * from '../../../features/privacy/types';
  export * from '../../../features/metrics/types';
  export * from '../../../features/analytics/types';
}

// UI types
declare module '@ui/types' {
  export * from '../../../ui/components/types';
}

// Service types
declare module '@services/types' {
  export * from '../../../services/auth/types';
}

// Configuration types
declare module '@config/constants' {
  export const DEFAULT_METRICS_FILE: string;
  export const DEFAULT_ACKS_FILE: string;
  export const DEFAULT_DIRECTORY: string;

  export const RETRY_OPTIONS: {
    maxRetries: number;
    initialDelay: number;
    maxDelay: number;
    factor: number;
  };

  export const BATCH_OPTIONS: {
    maxSize: number;
    maxAge: number;
  };
}
