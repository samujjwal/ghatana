import React, { useEffect, useState } from 'react';

import { detectPlatform } from './platform';

import type { Platform } from './platform';

/**
 *
 */
interface PlatformWrapperProps {
  children: React.ReactNode;
  onPlatformDetected?: (platform: Platform) => void;
}

/**
 * A wrapper component that provides platform-specific styling and behavior
 * based on the detected platform (web, desktop, or mobile)
 */
export function PlatformWrapper({ children, onPlatformDetected }: PlatformWrapperProps) {
  const [platform, setPlatform] = useState<Platform>('web');
  
  useEffect(() => {
    // Detect platform on mount
    const detectedPlatform = detectPlatform();
    setPlatform(detectedPlatform);
    
    // Notify parent component if callback provided
    if (onPlatformDetected) {
      onPlatformDetected(detectedPlatform);
    }
    
    // Add platform-specific class to body for global styling
    document.body.classList.add(`platform-${detectedPlatform}`);
    
    return () => {
      // Clean up on unmount
      document.body.classList.remove(`platform-${detectedPlatform}`);
    };
  }, [onPlatformDetected]);
  
  return (
    <div className={`platform-wrapper platform-${platform}`}>
      {children}
    </div>
  );
}
