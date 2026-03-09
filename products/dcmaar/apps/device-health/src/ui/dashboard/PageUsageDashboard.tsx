/**
 * @file Page Usage Dashboard
 * 
 * @deprecated This component is deprecated and will be removed in the next major version.
 * Please migrate to EnhancedPageUsageDashboard for improved features and performance.
 * 
 * ## Migration Guide
 * 
 * 1. Update your imports:
 *    ```typescript
 *    // Old
 *    import { PageUsageDashboard } from './dashboard/PageUsageDashboard';
 *    
 *    // New
 *    import EnhancedPageUsageDashboard from './dashboard/EnhancedPageUsageDashboard';
 *    ```
 * 
 * 2. Replace the component usage:
 *    ```tsx
 *    // Old
 *    <PageUsageDashboard />
 *    
 *    // New
 *    <EnhancedPageUsageDashboard />
 *    ```
 * 
 * For more details, see the component documentation or contact the development team.
 * 
 * @see EnhancedPageUsageDashboard
 */

import React, { useEffect } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';

const PageUsageDashboard: React.FC = () => {
  useEffect(() => {
    console.warn(
      'DEPRECATION WARNING: PageUsageDashboard is deprecated and will be removed in the next major version.\n' +
      'Please migrate to EnhancedPageUsageDashboard for improved features and performance.\n' +
      'See the component documentation for migration instructions.'
    );
  }, []);
  
  return (
    <Card title="Legacy Dashboard (Deprecated)" className="border-2 border-amber-200">
      <div className="p-6 space-y-4">
        <div className="bg-amber-50 p-4 rounded-lg border border-amber-200">
          <h3 className="font-medium text-amber-800">This dashboard has been replaced</h3>
          <p className="mt-1 text-amber-700">
            This version is deprecated and will be removed in a future update.
          </p>
        </div>
        
        <div className="mt-4 p-4 bg-slate-50 rounded-lg border border-slate-200">
          <h3 className="font-medium text-slate-800">Upgrade to Enhanced Dashboard</h3>
          <p className="mt-1 text-slate-600">
            Please update your code to use the new <code className="px-1.5 py-0.5 bg-slate-100 rounded text-sm font-mono">EnhancedPageUsageDashboard</code> component.
          </p>
          
          <div className="mt-3 p-3 bg-white border border-slate-200 rounded text-sm">
            <pre className="whitespace-pre-wrap">
              <code className="text-xs">
{`// 1. Update your import:
import EnhancedPageUsageDashboard from './dashboard/EnhancedPageUsageDashboard';

// 2. Replace the component:
<EnhancedPageUsageDashboard />`}
              </code>
            </pre>
          </div>
          
          <p className="mt-3 text-sm text-slate-500">
            For more details, check the component documentation or contact the development team.
          </p>
        </div>
      </div>
    </Card>
  );
};

export default PageUsageDashboard;
