/**
 * @ghatana/yappc-ui - Scaffold Wizard Component
 *
 * Multi-step wizard for scaffold generation:
 * 1. Choose pack
 * 2. Configure variables
 * 3. Preview (dry-run)
 * 4. Confirm
 * 5. Generate
 *
 * @doc.type component
 * @doc.purpose Scaffold generation wizard with multi-step flow
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect } from 'react';

import { InteractiveButton } from '../MicroInteractions';
import { useToastNotifications } from '../Toast';

/**
 * Pack metadata interface
 */
export interface PackMetadata {
  id: string;
  name: string;
  description: string;
  version: string;
  author: string;
  tags: string[];
  language: string;
  framework?: string;
  icon: string;
  variables: PackVariable[];
  dependencies?: string[];
}

/**
 * Pack variable interface
 */
export interface PackVariable {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'select' | 'textarea';
  description: string;
  required: boolean;
  defaultValue?: string | number | boolean;
  options?: string[];
  validation?: {
    pattern?: string;
    minLength?: number;
    maxLength?: number;
    min?: number;
    max?: number;
  };
}

/**
 * File change interface for dry-run preview
 */
export interface FileChange {
  type: 'CREATE' | 'UPDATE' | 'DELETE' | 'RENAME';
  filePath: string;
  oldContent?: string;
  newContent?: string;
  oldSize?: number;
  newSize?: number;
}

/**
 * Scaffold wizard props
 */
export interface ScaffoldWizardProps {
  isVisible: boolean;
  onClose: () => void;
  availablePacks: PackMetadata[];
  onGenerate: (packId: string, variables: Record<string, unknown>) => Promise<void>;
  onDryRun: (packId: string, variables: Record<string, unknown>) => Promise<FileChange[]>;
  className?: string;
}

/**
 * Wizard step type
 */
type WizardStep = 'choose-pack' | 'configure' | 'preview' | 'confirm' | 'generating';

/**
 * Scaffold Wizard Component
 */
export const ScaffoldWizard: React.FC<ScaffoldWizardProps> = ({
  isVisible,
  onClose,
  availablePacks,
  onGenerate,
  onDryRun,
  className = '',
}) => {
  const [currentStep, setCurrentStep] = useState<WizardStep>('choose-pack');
  const [selectedPack, setSelectedPack] = useState<PackMetadata | null>(null);
  const [variables, setVariables] = useState<Record<string, unknown>>({});
  const [previewChanges, setPreviewChanges] = useState<FileChange[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const { success, error, info } = useToastNotifications();

  // Reset state when wizard closes
  useEffect(() => {
    if (!isVisible) {
      setCurrentStep('choose-pack');
      setSelectedPack(null);
      setVariables({});
      setPreviewChanges([]);
      setIsGenerating(false);
      setIsPreviewing(false);
    }
  }, [isVisible]);

  /**
   * Handle pack selection
   */
  const handlePackSelect = useCallback((pack: PackMetadata) => {
    setSelectedPack(pack);

    // Initialize variables with default values
    const defaultVars: Record<string, unknown> = {};
    pack.variables.forEach((variable) => {
      if (variable.defaultValue !== undefined) {
        defaultVars[variable.name] = variable.defaultValue;
      }
    });
    setVariables(defaultVars);
    setCurrentStep('configure');
  }, []);

  /**
   * Handle variable change
   */
  const handleVariableChange = useCallback(
    (variableName: string, value: unknown) => {
      setVariables((prev) => ({
        ...prev,
        [variableName]: value,
      }));
    },
    []
  );

  /**
   * Validate required variables
   */
  const validateVariables = useCallback((): boolean => {
    if (!selectedPack) return false;

    for (const variable of selectedPack.variables) {
      if (variable.required) {
        const value = variables[variable.name];
        if (value === undefined || value === null || value === '') {
          error(`${variable.name} is required`);
          return false;
        }
      }

      // Validate pattern if provided
      if (variable.validation?.pattern && typeof variables[variable.name] === 'string') {
        const regex = new RegExp(variable.validation.pattern);
        if (!regex.test(variables[variable.name] as string)) {
          error(`${variable.name} does not match the required pattern`);
          return false;
        }
      }

      // Validate string length
      if (variable.validation?.minLength || variable.validation?.maxLength) {
        const strValue = String(variables[variable.name] || '');
        if (variable.validation.minLength && strValue.length < variable.validation.minLength) {
          error(`${variable.name} must be at least ${variable.validation.minLength} characters`);
          return false;
        }
        if (variable.validation.maxLength && strValue.length > variable.validation.maxLength) {
          error(`${variable.name} must be at most ${variable.validation.maxLength} characters`);
          return false;
        }
      }

      // Validate number range
      if (variable.validation?.min || variable.validation?.max) {
        const numValue = Number(variables[variable.name]);
        if (variable.validation.min && numValue < variable.validation.min) {
          error(`${variable.name} must be at least ${variable.validation.min}`);
          return false;
        }
        if (variable.validation.max && numValue > variable.validation.max) {
          error(`${variable.name} must be at most ${variable.validation.max}`);
          return false;
        }
      }
    }

    return true;
  }, [selectedPack, variables, error]);

  /**
   * Handle preview (dry-run)
   */
  const handlePreview = useCallback(async () => {
    if (!selectedPack || !validateVariables()) return;

    setIsPreviewing(true);
    try {
      const changes = await onDryRun(selectedPack.id, variables);
      setPreviewChanges(changes);
      setCurrentStep('preview');
      success('Preview generated successfully!');
    } catch {
      error('Failed to generate preview');
    } finally {
      setIsPreviewing(false);
    }
  }, [selectedPack, variables, onDryRun, validateVariables, success, error]);

  /**
   * Handle confirm
   */
  const handleConfirm = useCallback(() => {
    setCurrentStep('confirm');
  }, []);

  /**
   * Handle generate
   */
  const handleGenerate = useCallback(async () => {
    if (!selectedPack) return;

    setIsGenerating(true);
    try {
      await onGenerate(selectedPack.id, variables);
      success('Scaffold generated successfully!');
      onClose();
    } catch {
      error('Failed to generate scaffold');
    } finally {
      setIsGenerating(false);
    }
  }, [selectedPack, variables, onGenerate, onClose, success, error]);

  /**
   * Go back to previous step
   */
  const handleBack = useCallback(() => {
    switch (currentStep) {
      case 'configure':
        setCurrentStep('choose-pack');
        break;
      case 'preview':
        setCurrentStep('configure');
        break;
      case 'confirm':
        setCurrentStep('preview');
        break;
      default:
        break;
    }
  }, [currentStep]);

  if (!isVisible) return null;

  return (
    <div
      className={`fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 ${className}`}
    >
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-xl w-full max-w-5xl max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-6 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                Scaffold Generation Wizard
              </h2>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                {selectedPack ? `Configuring: ${selectedPack.name}` : 'Choose a pack to generate'}
              </p>
            </div>
            <InteractiveButton
              variant="ghost"
              size="sm"
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            >
              ✕
            </InteractiveButton>
          </div>

          {/* Progress Steps */}
          <div className="mt-4">
            <div className="flex items-center justify-between">
              {['choose-pack', 'configure', 'preview', 'confirm'].map((step, index) => (
                <React.Fragment key={step}>
                  <div className="flex items-center">
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
                        currentStep === step
                          ? 'bg-blue-500 text-white'
                          : index <
                            ['choose-pack', 'configure', 'preview', 'confirm'].indexOf(
                              currentStep
                            )
                          ? 'bg-green-500 text-white'
                          : 'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                      }`}
                    >
                      {index <
                      ['choose-pack', 'configure', 'preview', 'confirm'].indexOf(
                        currentStep
                      )
                        ? '✓'
                        : index + 1}
                    </div>
                    <span className="ml-2 text-sm text-gray-600 dark:text-gray-400">
                      {step === 'choose-pack' && 'Choose Pack'}
                      {step === 'configure' && 'Configure'}
                      {step === 'preview' && 'Preview'}
                      {step === 'confirm' && 'Confirm'}
                    </span>
                  </div>
                  {index < 3 && (
                    <div
                      className={`flex-1 h-0.5 mx-4 ${
                        index <
                        ['choose-pack', 'configure', 'preview', 'confirm'].indexOf(
                          currentStep
                        )
                          ? 'bg-green-500'
                          : 'bg-gray-200 dark:bg-gray-700'
                      }`}
                    />
                  )}
                </React.Fragment>
              ))}
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-hidden">
          {currentStep === 'choose-pack' && (
            <ChoosePackStep
              availablePacks={availablePacks}
              selectedPack={selectedPack}
              onPackSelect={handlePackSelect}
            />
          )}

          {currentStep === 'configure' && selectedPack && (
            <ConfigureStep
              pack={selectedPack}
              variables={variables}
              onVariableChange={handleVariableChange}
              onNext={handlePreview}
              isNextDisabled={isPreviewing}
            />
          )}

          {currentStep === 'preview' && selectedPack && (
            <PreviewStep
              pack={selectedPack}
              changes={previewChanges}
              variables={variables}
              onBack={handleBack}
              onNext={handleConfirm}
              isNextDisabled={false}
            />
          )}

          {currentStep === 'confirm' && selectedPack && (
            <ConfirmStep
              pack={selectedPack}
              variables={variables}
              changes={previewChanges}
              onBack={handleBack}
              onGenerate={handleGenerate}
              isGenerating={isGenerating}
            />
          )}

          {currentStep === 'generating' && (
            <GeneratingStep packName={selectedPack?.name || ''} />
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * Choose Pack Step
 */
const ChoosePackStep: React.FC<{
  availablePacks: PackMetadata[];
  selectedPack: PackMetadata | null;
  onPackSelect: (pack: PackMetadata) => void;
}> = ({ availablePacks, selectedPack, onPackSelect }) => {
  const [filter, setFilter] = useState('');

  const filteredPacks = availablePacks.filter(
    (pack) =>
      pack.name.toLowerCase().includes(filter.toLowerCase()) ||
      pack.description.toLowerCase().includes(filter.toLowerCase()) ||
      pack.tags.some((tag) => tag.toLowerCase().includes(filter.toLowerCase()))
  );

  return (
    <div className="p-6">
      <div className="mb-4">
        <input
          type="text"
          placeholder="Search packs..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredPacks.map((pack) => (
          <div
            key={pack.id}
            className={`p-4 border rounded-lg cursor-pointer transition-colors ${
              selectedPack?.id === pack.id
                ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
            }`}
            onClick={() => onPackSelect(pack)}
          >
            <div className="flex items-start space-x-3">
              <span className="text-2xl">{pack.icon}</span>
              <div className="flex-1 min-w-0">
                <div className="font-medium text-gray-900 dark:text-gray-100">
                  {pack.name}
                </div>
                <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                  {pack.description}
                </div>
                <div className="flex flex-wrap gap-1 mt-2">
                  {pack.tags.slice(0, 3).map((tag) => (
                    <span
                      key={tag}
                      className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
                <div className="text-xs text-gray-500 dark:text-gray-500 mt-2">
                  {pack.language} {pack.framework && `• ${pack.framework}`}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {filteredPacks.length === 0 && (
        <div className="text-center text-gray-500 dark:text-gray-400 py-8">
          No packs found matching your search
        </div>
      )}
    </div>
  );
};

/**
 * Configure Step
 */
const ConfigureStep: React.FC<{
  pack: PackMetadata;
  variables: Record<string, unknown>;
  onVariableChange: (name: string, value: unknown) => void;
  onNext: () => void;
  isNextDisabled: boolean;
}> = ({ pack, variables, onVariableChange, onNext, isNextDisabled }) => {
  return (
    <div className="p-6">
      <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-4">
        Configure {pack.name}
      </h3>

      <div className="space-y-6">
        {pack.variables.map((variable) => (
          <div key={variable.name}>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              {variable.name}
              {variable.required && (
                <span className="text-red-500 ml-1">*</span>
              )}
            </label>

            {variable.type === 'string' && (
              <input
                type="text"
                value={(variables[variable.name] as string) || ''}
                onChange={(e) => onVariableChange(variable.name, e.target.value)}
                placeholder={variable.description}
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              />
            )}

            {variable.type === 'number' && (
              <input
                type="number"
                value={(variables[variable.name] as number) || ''}
                onChange={(e) =>
                  onVariableChange(variable.name, Number(e.target.value))
                }
                placeholder={variable.description}
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              />
            )}

            {variable.type === 'boolean' && (
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={(variables[variable.name] as boolean) || false}
                  onChange={(e) =>
                    onVariableChange(variable.name, e.target.checked)
                  }
                  className="rounded border-gray-300 dark:border-gray-600 text-blue-600 focus:ring-blue-500 dark:bg-gray-800"
                />
                <span className="text-sm text-gray-700 dark:text-gray-300">
                  {variable.description}
                </span>
              </label>
            )}

            {variable.type === 'select' && (
              <select
                value={(variables[variable.name] as string) || ''}
                onChange={(e) => onVariableChange(variable.name, e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              >
                <option value="">Select an option</option>
                {variable.options?.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            )}

            {variable.type === 'textarea' && (
              <textarea
                value={(variables[variable.name] as string) || ''}
                onChange={(e) => onVariableChange(variable.name, e.target.value)}
                placeholder={variable.description}
                rows={4}
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              />
            )}

            {variable.description && variable.type !== 'boolean' && (
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                {variable.description}
              </p>
            )}
          </div>
        ))}
      </div>

      <div className="mt-6 flex justify-end">
        <InteractiveButton
          variant="primary"
          onClick={onNext}
          disabled={isNextDisabled}
        >
          {isNextDisabled ? 'Previewing...' : 'Preview Changes'}
        </InteractiveButton>
      </div>
    </div>
  );
};

/**
 * Preview Step
 */
const PreviewStep: React.FC<{
  pack: PackMetadata;
  changes: FileChange[];
  variables: Record<string, unknown>;
  onBack: () => void;
  onNext: () => void;
  isNextDisabled: boolean;
}> = ({ pack, changes, variables, onBack, onNext, isNextDisabled }) => {
  const createCount = changes.filter((c) => c.type === 'CREATE').length;
  const updateCount = changes.filter((c) => c.type === 'UPDATE').length;
  const deleteCount = changes.filter((c) => c.type === 'DELETE').length;

  return (
    <div className="p-6">
      <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-4">
        Preview Changes for {pack.name}
      </h3>

      <div className="mb-4 p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
        <div className="grid grid-cols-3 gap-4 text-center">
          <div>
            <div className="text-2xl font-bold text-green-600">{createCount}</div>
            <div className="text-sm text-gray-600 dark:text-gray-400">Create</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-blue-600">{updateCount}</div>
            <div className="text-sm text-gray-600 dark:text-gray-400">Update</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-red-600">{deleteCount}</div>
            <div className="text-sm text-gray-600 dark:text-gray-400">Delete</div>
          </div>
        </div>
      </div>

      <div className="space-y-2 max-h-96 overflow-y-auto">
        {changes.map((change, index) => (
          <div
            key={index}
            className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <span
                  className={`px-2 py-0.5 text-xs rounded ${
                    change.type === 'CREATE'
                      ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                      : change.type === 'UPDATE'
                      ? 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
                      : change.type === 'DELETE'
                      ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                      : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
                  }`}
                >
                  {change.type}
                </span>
                <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {change.filePath}
                </span>
              </div>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {change.newSize ? `${change.newSize} bytes` : ''}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 flex justify-between">
        <InteractiveButton variant="ghost" onClick={onBack}>
          Back
        </InteractiveButton>
        <InteractiveButton variant="primary" onClick={onNext} disabled={isNextDisabled}>
          Confirm & Generate
        </InteractiveButton>
      </div>
    </div>
  );
};

/**
 * Confirm Step
 */
const ConfirmStep: React.FC<{
  pack: PackMetadata;
  variables: Record<string, unknown>;
  changes: FileChange[];
  onBack: () => void;
  onGenerate: () => void;
  isGenerating: boolean;
}> = ({ pack, variables, changes, onBack, onGenerate, isGenerating }) => {
  return (
    <div className="p-6">
      <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-4">
        Confirm Scaffold Generation
      </h3>

      <div className="space-y-4">
        <div className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
          <h4 className="font-medium text-blue-900 dark:text-blue-100 mb-2">
            Pack: {pack.name}
          </h4>
          <p className="text-sm text-blue-700 dark:text-blue-300">{pack.description}</p>
        </div>

        <div className="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
          <h4 className="font-medium text-gray-900 dark:text-gray-100 mb-2">
            Configuration
          </h4>
          <div className="space-y-1">
            {Object.entries(variables).map(([key, value]) => (
              <div key={key} className="flex justify-between text-sm">
                <span className="text-gray-600 dark:text-gray-400">{key}:</span>
                <span className="text-gray-900 dark:text-gray-100 font-mono">
                  {String(value)}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
          <p className="text-sm text-yellow-800 dark:text-yellow-200">
            <strong>Warning:</strong> This will modify your project structure. Make sure you have
            committed your changes before proceeding.
          </p>
        </div>
      </div>

      <div className="mt-6 flex justify-between">
        <InteractiveButton variant="ghost" onClick={onBack} disabled={isGenerating}>
          Back
        </InteractiveButton>
        <InteractiveButton
          variant="primary"
          onClick={onGenerate}
          disabled={isGenerating}
        >
          {isGenerating ? 'Generating...' : 'Generate Scaffold'}
        </InteractiveButton>
      </div>
    </div>
  );
};

/**
 * Generating Step
 */
const GeneratingStep: React.FC<{ packName: string }> = ({ packName }) => {
  return (
    <div className="p-6 flex flex-col items-center justify-center h-full">
      <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-500 mb-4"></div>
      <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Generating Scaffold
      </h3>
      <p className="text-gray-600 dark:text-gray-400">
        Creating {packName} scaffold...
      </p>
    </div>
  );
};

/**
 * Scaffold wizard hook
 */
export const useScaffoldWizard = () => {
  const [isVisible, setIsVisible] = useState(false);

  const openWizard = useCallback(() => {
    setIsVisible(true);
  }, []);

  const closeWizard = useCallback(() => {
    setIsVisible(false);
  }, []);

  return {
    isVisible,
    openWizard,
    closeWizard,
  };
};

export default ScaffoldWizard;
