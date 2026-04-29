import React from 'react';

export type ConsentCategory = 'analytics' | 'marketing' | 'functional' | 'essential';

export interface ConsentGateProps {
  category: ConsentCategory;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function ConsentGate({ children }: ConsentGateProps) {
  return <>{children}</>;
}
