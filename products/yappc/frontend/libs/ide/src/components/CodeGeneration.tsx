/**
 * @ghatana/yappc-ide - Smart Code Generation System
 * 
 * Intelligent code generation with AI-powered templates,
 * context-aware suggestions, and automated scaffolding.
 * 
 * @doc.type component
 * @doc.purpose Smart code generation for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useToastNotifications } from './Toast';
import { InteractiveButton } from './MicroInteractions';

/**
 * Code generation template types
 */
export type GenerationType = 
  | 'component'
  | 'api-endpoint'
  | 'database-model'
  | 'test-suite'
  | 'documentation'
  | 'config-file'
  | 'hook'
  | 'utility'
  | 'custom-template';

/**
 * Code generation template interface
 */
export interface GenerationTemplate {
  id: string;
  name: string;
  description: string;
  type: GenerationType;
  category: 'frontend' | 'backend' | 'fullstack' | 'testing' | 'devops';
  language: string;
  icon: string;
  parameters: TemplateParameter[];
  template: string;
  examples?: string[];
  tags: string[];
}

/**
 * Template parameter interface
 */
export interface TemplateParameter {
  id: string;
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
 * Generation result interface
 */
export interface GenerationResult {
  id: string;
  template: GenerationTemplate;
  parameters: Record<string, unknown>;
  generatedCode: string;
  timestamp: Date;
  applied: boolean;
}

/**
 * Code generation props
 */
export interface CodeGenerationProps {
  isVisible: boolean;
  onClose: () => void;
  currentFile?: {
    name: string;
    content: string;
    language: string;
    path: string;
  };
  onGenerate: (result: GenerationResult) => Promise<void>;
  className?: string;
}

/**
 * Code Generation Component
 */
export const CodeGeneration: React.FC<CodeGenerationProps> = ({
  isVisible,
  onClose,
  onGenerate,
  className = '',
}) => {
  const [templates, setTemplates] = useState<GenerationTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<GenerationTemplate | null>(null);
  const [parameters, setParameters] = useState<Record<string, unknown>>({});
  const [isGenerating, setIsGenerating] = useState(false);
  const [results, setResults] = useState<GenerationResult[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const { success, error, info } = useToastNotifications();

  /**
   * Initialize default templates
   */
  useEffect(() => {
    const defaultTemplates: GenerationTemplate[] = [
      // React Component Template
      {
        id: 'react-component',
        name: 'React Component',
        description: 'Generate a React functional component with TypeScript',
        type: 'component',
        category: 'frontend',
        language: 'typescript',
        icon: '⚛️',
        parameters: [
          {
            id: 'componentName',
            name: 'Component Name',
            type: 'string',
            description: 'Name of the component',
            required: true,
            validation: { pattern: '^[A-Z][a-zA-Z0-9]*$' }
          },
          {
            id: 'withProps',
            name: 'Include Props',
            type: 'boolean',
            description: 'Generate with props interface',
            required: false,
            defaultValue: false
          },
          {
            id: 'withHooks',
            name: 'Include Hooks',
            type: 'boolean',
            description: 'Include common React hooks',
            required: false,
            defaultValue: false
          }
        ],
        template: `import React from 'react';

interface \${1:ComponentName}Props {
  \${2:// Define props here}
}

const \${1:ComponentName}: React.FC<\${1:ComponentName}Props> = (\${3:props}) => {
  return (
    <div>
      <h1>\${1:ComponentName}</h1>
      \${4:// Component content}
    </div>
  );
};

export default \${1:ComponentName};`,
        tags: ['react', 'component', 'typescript']
      },
      // API Endpoint Template
      {
        id: 'api-endpoint',
        name: 'API Endpoint',
        description: 'Generate an Express.js API endpoint with TypeScript',
        type: 'api-endpoint',
        category: 'backend',
        language: 'typescript',
        icon: '🔌',
        parameters: [
          {
            id: 'endpointName',
            name: 'Endpoint Name',
            type: 'string',
            description: 'Name of the endpoint',
            required: true,
            validation: { pattern: '^[a-z][a-zA-Z0-9]*$' }
          },
          {
            id: 'method',
            name: 'HTTP Method',
            type: 'select',
            description: 'HTTP method for the endpoint',
            required: true,
            defaultValue: 'get',
            options: ['get', 'post', 'put', 'delete', 'patch']
          },
          {
            id: 'withAuth',
            name: 'Include Authentication',
            type: 'boolean',
            description: 'Add authentication middleware',
            required: false,
            defaultValue: false
          }
        ],
        template: `import { Request, Response } from 'express';

export const \${1:endpointName}Handler = async (req: Request, res: Response) => {
  try {
    \${2:// Implementation here}
    
    res.json({
      success: true,
      data: \${3:// Response data}
    });
  } catch (err) {
    console.error('Error in \${1:endpointName}:', err);
    res.status(500).json({
      success: false,
      error: err instanceof Error ? err.message : 'Unknown error'
    });
  }
};

// Route definition
// router.\${4:get}('/\${1:endpointName}', \${1:endpointName}Handler);`,
        tags: ['api', 'express', 'typescript', 'backend']
      },
      // Database Model Template
      {
        id: 'database-model',
        name: 'Database Model',
        description: 'Generate a TypeScript database model with validation',
        type: 'database-model',
        category: 'backend',
        language: 'typescript',
        icon: '🗄️',
        parameters: [
          {
            id: 'modelName',
            name: 'Model Name',
            type: 'string',
            description: 'Name of the model',
            required: true,
            validation: { pattern: '^[A-Z][a-zA-Z0-9]*$' }
          },
          {
            id: 'tableName',
            name: 'Table Name',
            type: 'string',
            description: 'Database table name',
            required: true,
            defaultValue: ''
          },
          {
            id: 'withTimestamps',
            name: 'Include Timestamps',
            type: 'boolean',
            description: 'Add createdAt and updatedAt fields',
            required: false,
            defaultValue: true
          }
        ],
        template: `import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn } from 'typeorm';

@Entity('\${1:tableName}')
export class \${2:ModelName} {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  \${3:// Define your columns here}

  \${4:@CreateDateColumn()}
  createdAt: Date;

  \${5:@UpdateDateColumn()}
  updatedAt: Date;
}`,
        tags: ['database', 'typeorm', 'model', 'typescript']
      },
      // Test Suite Template
      {
        id: 'test-suite',
        name: 'Test Suite',
        description: 'Generate a Jest test suite with TypeScript',
        type: 'test-suite',
        category: 'testing',
        language: 'typescript',
        icon: '🧪',
        parameters: [
          {
            id: 'suiteName',
            name: 'Suite Name',
            type: 'string',
            description: 'Name of the test suite',
            required: true
          },
          {
            id: 'testType',
            name: 'Test Type',
            type: 'select',
            description: 'Type of tests to generate',
            required: true,
            defaultValue: 'unit',
            options: ['unit', 'integration', 'e2e']
          }
        ],
        template: `import { describe, it, expect, beforeEach, afterEach } from '@jest/globals';
import { \${1:serviceName} } from '../\${1:serviceName}';

describe('\${2:SuiteName}', () => {
  let service: \${1:serviceName};

  beforeEach(() => {
    \${3:// Setup before each test}
  });

  afterEach(() => {
    \${4:// Cleanup after each test}
  });

  it('should initialize correctly', () => {
    expect(service).toBeDefined();
  });

  it('should handle basic operations', async () => {
    \${5:// Test implementation}
    expect(true).toBe(true);
  });

  describe('error handling', () => {
    it('should handle errors appropriately', () => {
      \${6:// Error handling tests}
    });
  });
});`,
        tags: ['test', 'jest', 'typescript', 'testing']
      }
    ];

    setTemplates(defaultTemplates);
  }, []);

  /**
   * Filter templates by category
   */
  const filteredTemplates = selectedCategory === 'all'
    ? templates
    : templates.filter(template => template.category === selectedCategory);

  /**
   * Handle template selection
   */
  const handleTemplateSelect = useCallback((template: GenerationTemplate) => {
    setSelectedTemplate(template);
    
    // Initialize parameters with default values
    const defaultParams: Record<string, unknown> = {};
    template.parameters.forEach(param => {
      if (param.defaultValue !== undefined) {
        defaultParams[param.id] = param.defaultValue;
      }
    });
    setParameters(defaultParams);
  }, []);

  /**
   * Handle parameter change
   */
  const handleParameterChange = useCallback((paramId: string, value: unknown) => {
    setParameters(prev => ({
      ...prev,
      [paramId]: value
    }));
  }, []);

  /**
   * Generate code from template
   */
  const handleGenerate = useCallback(async () => {
    if (!selectedTemplate) return;

    setIsGenerating(true);
    
    try {
      // Simple template substitution (in real implementation, use a proper template engine)
      let generatedCode = selectedTemplate.template;
      
      // Replace parameter placeholders
      Object.entries(parameters).forEach(([key, value]) => {
        const placeholder = new RegExp(`\\$\\{1:${key}\\}`, 'g');
        generatedCode = generatedCode.replace(placeholder, String(value));
      });

      const result: GenerationResult = {
        id: Math.random().toString(36).substr(2, 9),
        template: selectedTemplate,
        parameters,
        generatedCode,
        timestamp: new Date(),
        applied: false
      };

      setResults(prev => [result, ...prev]);
      await onGenerate(result);
      success('Code generated successfully!');
    } catch {
      error('Failed to generate code');
    } finally {
      setIsGenerating(false);
    }
  }, [selectedTemplate, parameters, onGenerate, success, error]);

  /**
   * Apply generated code
   */
  const handleApply = useCallback(async (result: GenerationResult) => {
    try {
      await onGenerate(result);
      result.applied = true;
      success('Code applied successfully!');
    } catch {
      error('Failed to apply code');
    }
  }, [onGenerate, success, error]);

  /**
   * Copy generated code to clipboard
   */
  const handleCopy = useCallback(async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      success('Code copied to clipboard!');
    } catch {
      error('Failed to copy code');
    }
  }, [success, error]);

  /**
   * Clear all results
   */
  const handleClear = useCallback(() => {
    setResults([]);
    info('All results cleared');
  }, [info]);

  if (!isVisible) return null;

  return (
    <div className={`fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 ${className}`}>
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-xl w-full max-w-6xl max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-6 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                Code Generation
              </h2>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Generate code from intelligent templates
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
        </div>

        {/* Content */}
        <div className="flex-1 overflow-hidden">
          <div className="grid grid-cols-1 lg:grid-cols-3 h-full">
            {/* Templates Panel */}
            <div className="border-r border-gray-200 dark:border-gray-700 flex flex-col">
              {/* Category Filter */}
              <div className="p-4 border-b border-gray-200 dark:border-gray-700">
                <select
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="all">All Categories</option>
                  <option value="frontend">Frontend</option>
                  <option value="backend">Backend</option>
                  <option value="fullstack">Full Stack</option>
                  <option value="testing">Testing</option>
                  <option value="devops">DevOps</option>
                </select>
              </div>

              {/* Template List */}
              <div className="flex-1 overflow-y-auto p-4">
                <div className="space-y-2">
                  {filteredTemplates.map(template => (
                    <div
                      key={template.id}
                      className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                        selectedTemplate?.id === template.id
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                          : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                      }`}
                      onClick={() => handleTemplateSelect(template)}
                    >
                      <div className="flex items-center space-x-2">
                        <span className="text-xl">{template.icon}</span>
                        <div className="flex-1 min-w-0">
                          <div className="font-medium text-gray-900 dark:text-gray-100">
                            {template.name}
                          </div>
                          <div className="text-sm text-gray-600 dark:text-gray-400 truncate">
                            {template.description}
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Configuration Panel */}
            <div className="border-r border-gray-200 dark:border-gray-700 flex flex-col">
              {selectedTemplate ? (
                <>
                  <div className="p-4 border-b border-gray-200 dark:border-gray-700">
                    <h3 className="font-medium text-gray-900 dark:text-gray-100">
                      Configure {selectedTemplate.name}
                    </h3>
                  </div>
                  
                  <div className="flex-1 overflow-y-auto p-4">
                    <div className="space-y-4">
                      {selectedTemplate.parameters.map(param => (
                        <div key={param.id}>
                          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            {param.name}
                            {param.required && <span className="text-red-500 ml-1">*</span>}
                          </label>
                          
                          {param.type === 'string' && (
                            <input
                              type="text"
                              value={parameters[param.id] as string || ''}
                              onChange={(e) => handleParameterChange(param.id, e.target.value)}
                              placeholder={param.description}
                              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                            />
                          )}
                          
                          {param.type === 'boolean' && (
                            <label className="flex items-center space-x-2">
                              <input
                                type="checkbox"
                                checked={parameters[param.id] as boolean || false}
                                onChange={(e) => handleParameterChange(param.id, e.target.checked)}
                                className="rounded border-gray-300 dark:border-gray-600 text-blue-600 focus:ring-blue-500 dark:bg-gray-800"
                              />
                              <span className="text-sm text-gray-700 dark:text-gray-300">
                                {param.description}
                              </span>
                            </label>
                          )}
                          
                          {param.type === 'select' && (
                            <select
                              value={parameters[param.id] as string || ''}
                              onChange={(e) => handleParameterChange(param.id, e.target.value)}
                              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                            >
                              {param.options?.map(option => (
                                <option key={option} value={option}>
                                  {option}
                                </option>
                              ))}
                            </select>
                          )}
                          
                          {param.type === 'textarea' && (
                            <textarea
                              value={parameters[param.id] as string || ''}
                              onChange={(e) => handleParameterChange(param.id, e.target.value)}
                              placeholder={param.description}
                              rows={3}
                              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                            />
                          )}
                          
                          {param.description && param.type !== 'boolean' && (
                            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                              {param.description}
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                  
                  <div className="p-4 border-t border-gray-200 dark:border-gray-700">
                    <InteractiveButton
                      variant="primary"
                      onClick={handleGenerate}
                      disabled={isGenerating}
                      className="w-full"
                    >
                      {isGenerating ? 'Generating...' : 'Generate Code'}
                    </InteractiveButton>
                  </div>
                </>
              ) : (
                <div className="flex-1 flex items-center justify-center text-gray-500 dark:text-gray-400">
                  Select a template to configure
                </div>
              )}
            </div>

            {/* Results Panel */}
            <div className="flex flex-col">
              <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h3 className="font-medium text-gray-900 dark:text-gray-100">
                  Generated Code
                </h3>
                {results.length > 0 && (
                  <InteractiveButton
                    variant="ghost"
                    size="sm"
                    onClick={handleClear}
                  >
                    Clear All
                  </InteractiveButton>
                )}
              </div>
              
              <div className="flex-1 overflow-y-auto">
                {results.length === 0 ? (
                  <div className="flex-1 flex items-center justify-center text-gray-500 dark:text-gray-400 p-8">
                    No code generated yet
                  </div>
                ) : (
                  <div className="p-4 space-y-4">
                    {results.map(result => (
                      <div key={result.id} className="border border-gray-200 dark:border-gray-700 rounded-lg">
                        <div className="p-3 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                          <div>
                            <div className="font-medium text-gray-900 dark:text-gray-100">
                              {result.template.name}
                            </div>
                            <div className="text-xs text-gray-500 dark:text-gray-400">
                              {result.timestamp.toLocaleString()}
                            </div>
                          </div>
                          <div className="flex space-x-2">
                            <InteractiveButton
                              variant="ghost"
                              size="sm"
                              onClick={() => handleCopy(result.generatedCode)}
                            >
                              Copy
                            </InteractiveButton>
                            {!result.applied && (
                              <InteractiveButton
                                variant="primary"
                                size="sm"
                                onClick={() => handleApply(result)}
                              >
                                Apply
                              </InteractiveButton>
                            )}
                          </div>
                        </div>
                        <div className="p-3">
                          <pre className="bg-gray-100 dark:bg-gray-800 p-3 rounded text-sm overflow-x-auto">
                            <code>{result.generatedCode}</code>
                          </pre>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Code generation hook
 */
export const useCodeGeneration = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [currentFile, setCurrentFile] = useState<{
    name: string;
    content: string;
    language: string;
    path: string;
  } | null>(null);

  const openGenerator = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    path: string;
  }) => {
    setCurrentFile(file || null);
    setIsVisible(true);
  }, []);

  const closeGenerator = useCallback(() => {
    setIsVisible(false);
  }, []);

  const toggleGenerator = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    path: string;
  }) => {
    if (isVisible) {
      closeGenerator();
    } else {
      openGenerator(file);
    }
  }, [isVisible, openGenerator, closeGenerator]);

  return {
    isVisible,
    currentFile,
    openGenerator,
    closeGenerator,
    toggleGenerator,
  };
};

export default {
  CodeGeneration,
  useCodeGeneration,
};
