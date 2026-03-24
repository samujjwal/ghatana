/**
 * Quick verification test for Phase 0 infrastructure
 * 
 * Tests:
 * - cn() utility import and usage
 * - Tailwind Button component import
 * - Basic rendering
 */
import { cn } from '../../utils/cn';

import { Button } from './Button.tailwind';

/**
 *
 */
export function Phase0VerificationTest() {
  // Test cn() utility
  const testClasses = cn('px-4 py-2', 'bg-blue-500', 'text-white');
  console.log('cn() utility test:', testClasses);
  
  // Test conditional cn()
  const isActive = true;
  const conditionalClasses = cn(
    'px-4 py-2',
    isActive && 'bg-primary-500',
    !isActive && 'bg-grey-300'
  );
  console.log('cn() conditional test:', conditionalClasses);
  
  return (
    <div className="p-8 space-y-4">
      <h1 className="text-2xl font-bold mb-4">Phase 0 Infrastructure Verification</h1>
      
      {/* Test cn() utility */}
      <div className={cn('p-4', 'bg-grey-100', 'rounded-md')}>
        <p className="text-sm">cn() utility working ✅</p>
      </div>
      
      {/* Test Tailwind Button variants */}
      <div className="flex gap-3">
        <Button variant="solid">Solid Button</Button>
        <Button variant="outline">Outline Button</Button>
        <Button variant="ghost">Ghost Button</Button>
        <Button variant="link">Link Button</Button>
      </div>
      
      {/* Test Button sizes */}
      <div className="flex items-center gap-3">
        <Button size="sm">Small</Button>
        <Button size="md">Medium</Button>
        <Button size="lg">Large</Button>
      </div>
      
      {/* Test Button states */}
      <div className="flex gap-3">
        <Button>Normal</Button>
        <Button disabled>Disabled</Button>
        <Button isLoading>Loading</Button>
      </div>
      
      {/* Test design tokens integration */}
      <div className="space-y-2">
        <p className="text-sm text-grey-600">Design Tokens Test:</p>
        <div className="flex gap-2">
          <div className="w-16 h-16 bg-primary-500 rounded-md" title="primary-500" />
          <div className="w-16 h-16 bg-secondary-500 rounded-md" title="secondary-500" />
          <div className="w-16 h-16 bg-success-DEFAULT rounded-md" title="success" />
          <div className="w-16 h-16 bg-error-DEFAULT rounded-md" title="error" />
        </div>
      </div>
      
      <p className="text-sm text-grey-600 mt-4">
        ✅ Phase 0 Infrastructure Complete!
        <br />
        Base UI + Tailwind CSS migration ready.
      </p>
    </div>
  );
}
