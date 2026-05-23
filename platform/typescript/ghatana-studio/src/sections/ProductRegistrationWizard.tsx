/**
 * Product Registration Wizard with Surface Detection
 *
 * Guides users through registering a new ProductUnit with automatic surface detection.
 * Detects language, runtime, and build system from codebase and validates configurations.
 *
 * @doc.type component
 * @doc.purpose Register new ProductUnits with surface detection and validation
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState } from 'react';
import { Button, Typography, Card, CardContent, CardHeader, Badge, Select } from '@ghatana/design-system';
import { studioLogger } from '../logging/studioLogger';
import type {
  ProductUnitSurfaceType,
  ImplementationStatus,
  ProductLanguage,
  ProductRuntime,
  ProductBuildSystem,
} from '@ghatana/kernel-product-contracts';

type WizardStep = 'product-info' | 'surface-detection' | 'surface-validation' | 'review' | 'complete';

interface DetectedSurface {
  readonly id: string;
  readonly type: ProductUnitSurfaceType;
  readonly path: string;
  readonly language?: ProductLanguage;
  readonly runtime?: ProductRuntime;
  readonly buildSystem?: ProductBuildSystem;
  readonly languageVersion?: string;
  readonly runtimeVersion?: string;
  readonly buildSystemVersion?: string;
  readonly implementationStatus: ImplementationStatus;
  readonly validationError?: string;
}

interface ProductInfo {
  readonly id: string;
  readonly name: string;
  readonly kind: string;
  readonly owner: string;
  readonly description: string;
}

export default function ProductRegistrationWizard(): ReactElement {
  const [currentStep, setCurrentStep] = useState<WizardStep>('product-info');
  const [productInfo, setProductInfo] = useState<ProductInfo>({
    id: '',
    name: '',
    kind: 'service',
    owner: '',
    description: '',
  });
  const [detectedSurfaces, setDetectedSurfaces] = useState<DetectedSurface[]>([]);
  const [isDetecting, setIsDetecting] = useState(false);
  const [isRegistering, setIsRegistering] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleProductInfoNext = (): void => {
    if (!productInfo.id || !productInfo.name || !productInfo.owner) {
      setError('Please fill in all required fields');
      return;
    }
    setError(null);
    setCurrentStep('surface-detection');
  };

  const detectSurfaces = async (): Promise<void> => {
    setIsDetecting(true);
    setError(null);

    try {
      // In a real implementation, this would scan the codebase for surface indicators
      // For now, using mock detection to demonstrate the UI
      await new Promise((resolve) => setTimeout(resolve, 1500));

      const mockSurfaces: DetectedSurface[] = [
        {
          id: 'backend-api-1',
          type: 'backend-api',
          path: 'products/my-product/services/backend',
          language: 'java',
          runtime: 'java-jre',
          buildSystem: 'gradle',
          languageVersion: '21',
          runtimeVersion: '21',
          buildSystemVersion: '8.5',
          implementationStatus: 'implemented',
        },
        {
          id: 'web-1',
          type: 'web',
          path: 'products/my-product/web',
          language: 'typescript',
          runtime: 'nodejs',
          buildSystem: 'pnpm',
          languageVersion: '5.3',
          runtimeVersion: '20.10.0',
          buildSystemVersion: '9.0',
          implementationStatus: 'implemented',
        },
      ];

      // Validate each surface
      const validatedSurfaces = mockSurfaces.map((surface) => {
        let validationError: string | undefined;
        if (surface.language && surface.runtime && surface.buildSystem) {
          // Simplified validation for common combinations
          const isValid = (
            (surface.language === 'java' && (surface.runtime === 'java-jre' || surface.runtime === 'java-jdk') && (surface.buildSystem === 'gradle' || surface.buildSystem === 'maven')) ||
            ((surface.language === 'typescript' || surface.language === 'javascript') && (surface.runtime === 'nodejs' || surface.runtime === 'browser') && (surface.buildSystem === 'pnpm' || surface.buildSystem === 'npm' || surface.buildSystem === 'yarn')) ||
            (surface.language === 'rust' && (surface.runtime === 'rust-native' || surface.runtime === 'rust-wasm') && surface.buildSystem === 'cargo') ||
            (surface.language === 'python' && (surface.runtime === 'python' || surface.runtime === 'python-uv') && (surface.buildSystem === 'poetry' || surface.buildSystem === 'pip'))
          );
          if (!isValid) {
            validationError = `Invalid combination: ${surface.language}/${surface.runtime}/${surface.buildSystem}`;
          }
        }
        return { ...surface, validationError };
      });

      setDetectedSurfaces(validatedSurfaces);
      setCurrentStep('surface-validation');
    } catch (err) {
      studioLogger.error('Failed to detect surfaces', { error: err });
      setError('Failed to detect surfaces from codebase');
    } finally {
      setIsDetecting(false);
    }
  };

  const handleSurfaceValidationNext = (): void => {
    const hasErrors = detectedSurfaces.some((s) => s.validationError);
    if (hasErrors) {
      setError('Please fix validation errors before proceeding');
      return;
    }
    setError(null);
    setCurrentStep('review');
  };

  const registerProduct = async (): Promise<void> => {
    setIsRegistering(true);
    setError(null);

    try {
      // In a real implementation, this would call the ProductUnit registration API
      await new Promise((resolve) => setTimeout(resolve, 2000));
      setCurrentStep('complete');
    } catch (err) {
      studioLogger.error('Failed to register product', { error: err });
      setError('Failed to register product');
    } finally {
      setIsRegistering(false);
    }
  };

  const resetWizard = (): void => {
    setCurrentStep('product-info');
    setProductInfo({
      id: '',
      name: '',
      kind: 'service',
      owner: '',
      description: '',
    });
    setDetectedSurfaces([]);
    setError(null);
  };

  const getStepTitle = (): string => {
    switch (currentStep) {
      case 'product-info':
        return 'Product Information';
      case 'surface-detection':
        return 'Surface Detection';
      case 'surface-validation':
        return 'Surface Validation';
      case 'review':
        return 'Review and Register';
      case 'complete':
        return 'Registration Complete';
    }
  };

  const getStepDescription = (): string => {
    switch (currentStep) {
      case 'product-info':
        return 'Enter basic information about the product you want to register.';
      case 'surface-detection':
        return 'The wizard will scan your codebase to detect deployable surfaces.';
      case 'surface-validation':
        return 'Review and validate detected surface configurations.';
      case 'review':
        return 'Review all information before registering the product.';
      case 'complete':
        return 'Your product has been successfully registered.';
    }
  };

  return (
    <div className="p-6">
      <div className="studio-section max-w-4xl mx-auto">
        <div className="mb-6">
          <Typography variant="h2" className="text-2xl font-bold mb-2">
            Product Registration Wizard
          </Typography>
          <Typography variant="body2" className="text-gray-600">
            {getStepDescription()}
          </Typography>
        </div>

        {/* Progress Steps */}
        <div className="mb-8">
          <div className="flex items-center justify-between">
            {(['product-info', 'surface-detection', 'surface-validation', 'review'] as WizardStep[]).map((step, index) => {
              const isCompleted = ['surface-detection', 'surface-validation', 'review', 'complete'].includes(currentStep) && index === 0 ||
                                 ['surface-validation', 'review', 'complete'].includes(currentStep) && index === 1 ||
                                 ['review', 'complete'].includes(currentStep) && index === 2 ||
                                 currentStep === 'complete' && index === 3;
              const isCurrent = currentStep === step;
              return (
                <div key={step} className="flex items-center flex-1">
                  <div className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-medium ${
                    isCompleted ? 'bg-green-500 text-white' : isCurrent ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-600'
                  }`}>
                    {isCompleted ? '✓' : index + 1}
                  </div>
                  <div className="ml-2 text-sm font-medium text-gray-700 capitalize">
                    {step.replace('-', ' ')}
                  </div>
                  {index < 3 && <div className="flex-1 h-0.5 bg-gray-200 mx-4" />}
                </div>
              );
            })}
          </div>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <Typography variant="body2" className="text-red-600">
              {error}
            </Typography>
          </div>
        )}

        {/* Step Content */}
        {currentStep === 'product-info' && (
          <Card>
            <CardHeader title={getStepTitle()} />
            <CardContent className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Product ID *
                </label>
                <input
                  type="text"
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  value={productInfo.id}
                  onChange={(e) => setProductInfo({ ...productInfo, id: e.target.value })}
                  placeholder="e.g., my-product"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Product Name *
                </label>
                <input
                  type="text"
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  value={productInfo.name}
                  onChange={(e) => setProductInfo({ ...productInfo, name: e.target.value })}
                  placeholder="e.g., My Product"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Kind
                </label>
                <Select
                  value={productInfo.kind}
                  onChange={(e) => setProductInfo({ ...productInfo, kind: e.target.value })}
                  className="w-full"
                >
                  <option value="service">Service</option>
                  <option value="application">Application</option>
                  <option value="library">Library</option>
                  <option value="platform">Platform</option>
                </Select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Owner *
                </label>
                <input
                  type="text"
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  value={productInfo.owner}
                  onChange={(e) => setProductInfo({ ...productInfo, owner: e.target.value })}
                  placeholder="e.g., team@example.com"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Description
                </label>
                <textarea
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  rows={3}
                  value={productInfo.description}
                  onChange={(e) => setProductInfo({ ...productInfo, description: e.target.value })}
                  placeholder="Brief description of the product"
                />
              </div>
              <div className="flex justify-end">
                <Button variant="primary" onClick={handleProductInfoNext}>
                  Next: Detect Surfaces
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 'surface-detection' && (
          <Card>
            <CardHeader title={getStepTitle()} />
            <CardContent className="space-y-4">
              <div className="text-center py-8">
                <Typography variant="body1" className="text-gray-600 mb-4">
                  The wizard will scan your codebase to detect deployable surfaces such as:
                </Typography>
                <ul className="text-left text-sm text-gray-600 space-y-2 max-w-md mx-auto">
                  <li>• Backend APIs (Java, Kotlin, Go)</li>
                  <li>• Web applications (TypeScript, JavaScript)</li>
                  <li>• Mobile apps (iOS, Android)</li>
                  <li>• Workers and operators</li>
                  <li>• Data pipelines</li>
                </ul>
              </div>
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setCurrentStep('product-info')}>
                  Back
                </Button>
                <Button variant="primary" onClick={detectSurfaces} disabled={isDetecting}>
                  {isDetecting ? 'Detecting...' : 'Start Detection'}
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 'surface-validation' && (
          <Card>
            <CardHeader title={getStepTitle()} />
            <CardContent className="space-y-4">
              <div className="space-y-3">
                {detectedSurfaces.map((surface) => (
                  <div
                    key={surface.id}
                    className={`p-4 border rounded-lg ${
                      surface.validationError ? 'border-red-300 bg-red-50' : 'border-gray-200'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <Typography variant="body1" className="font-semibold">
                          {surface.type}
                        </Typography>
                        <Typography variant="body2" className="text-gray-600 text-sm">
                          {surface.path}
                        </Typography>
                      </div>
                      <Badge
                        tone={surface.validationError ? 'danger' : 'success'}
                        variant="soft"
                      >
                        {surface.validationError ? 'Invalid' : 'Valid'}
                      </Badge>
                    </div>
                    <div className="grid grid-cols-3 gap-4 text-sm">
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Language
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {surface.language || 'Not detected'}
                        </Typography>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Runtime
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {surface.runtime || 'Not detected'}
                        </Typography>
                      </div>
                      <div>
                        <Typography variant="body2" className="text-gray-600">
                          Build System
                        </Typography>
                        <Typography variant="body1" className="font-medium">
                          {surface.buildSystem || 'Not detected'}
                        </Typography>
                      </div>
                    </div>
                    {surface.validationError && (
                      <div className="mt-3 text-sm text-red-600">
                        {surface.validationError}
                      </div>
                    )}
                  </div>
                ))}
              </div>
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setCurrentStep('surface-detection')}>
                  Back
                </Button>
                <Button variant="primary" onClick={handleSurfaceValidationNext}>
                  Next: Review
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 'review' && (
          <Card>
            <CardHeader title={getStepTitle()} />
            <CardContent className="space-y-6">
              <div>
                <Typography variant="body1" className="font-semibold mb-2">
                  Product Information
                </Typography>
                <dl className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt className="text-gray-600">ID</dt>
                    <dd className="font-medium">{productInfo.id}</dd>
                  </div>
                  <div>
                    <dt className="text-gray-600">Name</dt>
                    <dd className="font-medium">{productInfo.name}</dd>
                  </div>
                  <div>
                    <dt className="text-gray-600">Kind</dt>
                    <dd className="font-medium">{productInfo.kind}</dd>
                  </div>
                  <div>
                    <dt className="text-gray-600">Owner</dt>
                    <dd className="font-medium">{productInfo.owner}</dd>
                  </div>
                </dl>
                {productInfo.description && (
                  <div className="mt-2">
                    <dt className="text-gray-600 text-sm">Description</dt>
                    <dd className="text-sm">{productInfo.description}</dd>
                  </div>
                )}
              </div>

              <div>
                <Typography variant="body1" className="font-semibold mb-2">
                  Surfaces ({detectedSurfaces.length})
                </Typography>
                <div className="space-y-2">
                  {detectedSurfaces.map((surface) => (
                    <div key={surface.id} className="p-3 bg-gray-50 rounded-lg text-sm">
                      <div className="font-medium">{surface.type}</div>
                      <div className="text-gray-600">{surface.path}</div>
                      <div className="text-gray-500 text-xs mt-1">
                        {surface.language}/{surface.runtime}/{surface.buildSystem}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setCurrentStep('surface-validation')}>
                  Back
                </Button>
                <Button variant="primary" onClick={registerProduct} disabled={isRegistering}>
                  {isRegistering ? 'Registering...' : 'Register Product'}
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 'complete' && (
          <Card>
            <CardContent className="py-12 text-center">
              <div className="text-6xl mb-4">✓</div>
              <Typography variant="h3" className="text-xl font-semibold mb-2">
                Product Registered Successfully
              </Typography>
              <Typography variant="body2" className="text-gray-600 mb-6">
                {productInfo.name} has been registered with {detectedSurfaces.length} surface(s).
              </Typography>
              <Button variant="primary" onClick={resetWizard}>
                Register Another Product
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
