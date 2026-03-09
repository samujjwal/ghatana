/**
 * @doc.type hook
 * @doc.purpose Component generation hook for Journey 7.1 (Frontend Engineer - Component Development)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import { AICodeGenerationService } from '../integration/aiCodeGeneration';
import { StorybookService, type ComponentProp, type StoryVariant } from '../services/StorybookService';
import type { Node } from '@xyflow/react';

/**
 * UI framework options
 */
export type UIFramework = 'react' | 'vue' | 'angular' | 'svelte';

/**
 * Styling approach options
 */
export type StylingApproach = 'tailwind' | 'css-modules' | 'styled-components' | 'emotion' | 'sass';

/**
 * Component generation options
 */
export interface ComponentGenerationOptions {
    framework: UIFramework;
    styling: StylingApproach;
    typescript: boolean;
    props: ComponentProp[];
    includeTests?: boolean;
    includeStorybook?: boolean;
    accessible?: boolean;
    responsive?: boolean;
}

/**
 * Generated component result
 */
export interface GeneratedComponent {
    componentFile: {
        filename: string;
        content: string;
    };
    testFile?: {
        filename: string;
        content: string;
    };
    storyFile?: {
        filename: string;
        content: string;
    };
    styleFile?: {
        filename: string;
        content: string;
    };
}

/**
 * Hook options
 */
export interface UseComponentGenerationOptions {
    defaultFramework?: UIFramework;
    defaultStyling?: StylingApproach;
    defaultTypescript?: boolean;
}

/**
 * Hook return value
 */
export interface UseComponentGenerationResult {
    // Generation
    generateComponent: (node: Node, options: ComponentGenerationOptions) => Promise<GeneratedComponent>;
    isGenerating: boolean;
    error: string | null;

    // Generated files
    lastGenerated: GeneratedComponent | null;

    // Storybook
    generateStorybook: (node: Node, variants?: StoryVariant[]) => Promise<string>;

    // File management
    downloadFiles: (component: GeneratedComponent) => void;
    copyToClipboard: (content: string) => Promise<void>;

    // Preview
    previewCode: string | null;
    setPreviewCode: (code: string | null) => void;
}

/**
 * Component Generation Hook
 * 
 * Provides component generation from wireframe nodes with support for
 * multiple frameworks, styling approaches, and file types (component, tests, stories).
 * 
 * @example
 * ```tsx
 * const {
 *   generateComponent,
 *   isGenerating,
 *   lastGenerated,
 *   downloadFiles,
 * } = useComponentGeneration({
 *   defaultFramework: 'react',
 *   defaultStyling: 'tailwind',
 *   defaultTypescript: true,
 * });
 * ```
 */
export function useComponentGeneration(
    options: UseComponentGenerationOptions = {}
): UseComponentGenerationResult {
    const {
        defaultFramework = 'react',
        defaultStyling = 'tailwind',
        defaultTypescript = true,
    } = options;

    const [isGenerating, setIsGenerating] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [lastGenerated, setLastGenerated] = useState<GeneratedComponent | null>(null);
    const [previewCode, setPreviewCode] = useState<string | null>(null);

    /**
     * Generate React component code
     */
    const generateReactComponent = useCallback(async (
        componentName: string,
        genOptions: ComponentGenerationOptions
    ): Promise<string> => {
        const { props, styling, typescript, accessible, responsive } = genOptions;

        // Build prompt for AI
        const prompt = `Generate a React ${typescript ? 'TypeScript' : 'JavaScript'} component named "${componentName}".

Requirements:
- Framework: React 18+
- Styling: ${styling}
- ${typescript ? 'TypeScript with strict types' : 'JavaScript with JSDoc'}
- Props: ${props.map(p => `${p.name}: ${p.type}${p.required ? '' : ' (optional)'}`).join(', ')}
${accessible ? '- WCAG 2.2 AA accessible (ARIA labels, keyboard nav, focus management)' : ''}
${responsive ? '- Responsive design (mobile-first)' : ''}

Props interface:
${props.map(p => `  ${p.name}${p.required ? '' : '?'}: ${p.type}; ${p.description ? `// ${p.description}` : ''}`).join('\n')}

Generate clean, production-ready code with:
1. Proper prop destructuring
2. ${styling === 'tailwind' ? 'Tailwind CSS classes' : styling === 'styled-components' ? 'Styled components' : 'CSS modules'}
3. Error handling for edge cases
4. Default prop values where appropriate
${accessible ? '5. ARIA attributes for accessibility' : ''}
${responsive ? '6. Responsive breakpoints' : ''}
`;

        try {
            // NOTE: Initialize AICodeGenerationService with proper IAIService instance
            // For now, generate template-based component code
            const code = generateTemplateComponent(componentName, genOptions);
            return code;
        } catch (err) {
            throw new Error(`Failed to generate component: ${err}`);
        }
    }, []);

    /**
     * Generate test file
     */
    const generateTestFile = useCallback(async (
        componentName: string,
        componentCode: string,
        props: ComponentProp[]
    ): Promise<string> => {
        const prompt = `Generate React Testing Library tests for the ${componentName} component.

Component code:
\`\`\`tsx
${componentCode}
\`\`\`

Generate tests for:
1. Component renders without crashing
2. Renders with all props
3. Renders with minimal props
4. Handles prop changes
5. Edge cases (empty strings, null values, etc.)
${props.some(p => p.type.includes('=>')) ? '6. Callback props are called correctly' : ''}

Use:
- @testing-library/react
- @testing-library/user-event
- vitest
- expect assertions
`;

        try {
            // NOTE: Initialize AICodeGenerationService with proper IAIService instance
            // For now, generate template-based test code
            const testCode = generateTemplateTestFile(componentName, componentCode, props);
            return testCode;
        } catch (err) {
            throw new Error(`Failed to generate tests: ${err}`);
        }
    }, []);

    /**
     * Generate component from node
     */
    const generateComponent = useCallback(async (
        node: Node,
        genOptions: ComponentGenerationOptions
    ): Promise<GeneratedComponent> => {
        setIsGenerating(true);
        setError(null);

        try {
            const componentName = node.data.label || node.data.name || 'Component';
            const { framework, typescript, includeTests, includeStorybook, styling } = genOptions;

            // Generate component file
            let componentCode = '';
            if (framework === 'react') {
                componentCode = await generateReactComponent(componentName, genOptions);
            } else {
                throw new Error(`Framework ${framework} not yet supported`);
            }

            const componentFile = {
                filename: `${componentName}.${typescript ? 'tsx' : 'jsx'}`,
                content: componentCode,
            };

            // Generate test file
            let testFile: { filename: string; content: string } | undefined;
            if (includeTests) {
                const testCode = await generateTestFile(componentName, componentCode, genOptions.props);
                testFile = {
                    filename: `${componentName}.test.${typescript ? 'tsx' : 'jsx'}`,
                    content: testCode,
                };
            }

            // Generate Storybook file
            let storyFile: { filename: string; content: string } | undefined;
            if (includeStorybook) {
                const story = StorybookService.generateStory({
                    componentName,
                    props: genOptions.props,
                    includeActions: true,
                    includeControls: true,
                    includeDocsPage: true,
                });

                storyFile = {
                    filename: story.filename,
                    content: story.content,
                };
            }

            // Generate style file (if not using Tailwind)
            let styleFile: { filename: string; content: string } | undefined;
            if (styling === 'css-modules') {
                styleFile = {
                    filename: `${componentName}.module.css`,
                    content: `.container {\n  /* Add your styles here */\n}\n`,
                };
            } else if (styling === 'sass') {
                styleFile = {
                    filename: `${componentName}.module.scss`,
                    content: `.container {\n  /* Add your styles here */\n}\n`,
                };
            }

            const result: GeneratedComponent = {
                componentFile,
                testFile,
                storyFile,
                styleFile,
            };

            setLastGenerated(result);
            setPreviewCode(componentCode);

            return result;
        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : 'Unknown error';
            setError(errorMessage);
            throw err;
        } finally {
            setIsGenerating(false);
        }
    }, [generateReactComponent, generateTestFile]);

    /**
     * Generate Storybook stories only
     */
    const generateStorybook = useCallback(async (
        node: Node,
        variants?: StoryVariant[]
    ): Promise<string> => {
        const componentName = node.data.label || node.data.name || 'Component';
        const props = node.data.props || [];

        const story = StorybookService.generateStory({
            componentName,
            props,
            variants,
            includeActions: true,
            includeControls: true,
            includeDocsPage: true,
        });

        return story.content;
    }, []);

    /**
     * Download generated files
     */
    const downloadFiles = useCallback((component: GeneratedComponent) => {
        const files = [
            component.componentFile,
            component.testFile,
            component.storyFile,
            component.styleFile,
        ].filter(Boolean) as Array<{ filename: string; content: string }>;

        files.forEach(file => {
            const blob = new Blob([file.content], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = file.filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        });
    }, []);

    /**
     * Copy content to clipboard
     */
    const copyToClipboard = useCallback(async (content: string): Promise<void> => {
        try {
            await navigator.clipboard.writeText(content);
        } catch (err) {
            throw new Error('Failed to copy to clipboard');
        }
    }, []);

    return {
        generateComponent,
        isGenerating,
        error,
        lastGenerated,
        generateStorybook,
        downloadFiles,
        copyToClipboard,
        previewCode,
        setPreviewCode,
    };
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Generate template-based React component code
 */
function generateTemplateComponent(
    componentName: string,
    options: ComponentGenerationOptions
): string {
    const { props, styling, typescript, accessible, responsive } = options;
    const ext = typescript ? 'tsx' : 'jsx';

    let code = `import React from 'react';\n\n`;

    // TypeScript interface
    if (typescript && props.length > 0) {
        code += `interface ${componentName}Props {\n`;
        props.forEach(prop => {
            const optional = prop.required ? '' : '?';
            const desc = prop.description ? `  /** ${prop.description} */\n` : '';
            code += `${desc}  ${prop.name}${optional}: ${prop.type};\n`;
        });
        code += `}\n\n`;
    }

    // Component
    const propsParam = typescript && props.length > 0 ? `props: ${componentName}Props` : props.length > 0 ? 'props' : '';
    code += `export function ${componentName}(${propsParam}) {\n`;

    // Destructure props
    if (props.length > 0) {
        const propNames = props.map(p => {
            const def = p.defaultValue ? ` = ${p.defaultValue}` : '';
            return `${p.name}${def}`;
        }).join(', ');
        code += `  const { ${propNames} } = props;\n\n`;
    }

    // JSX
    const tailwindClasses = styling === 'tailwind' ? ' className="p-4 bg-white rounded-lg shadow"' : '';
    const a11yAttrs = accessible ? ' role="main" aria-label="' + componentName + '"' : '';
    const responsiveClass = responsive && styling === 'tailwind' ? ' sm:p-6 md:p-8' : '';

    code += `  return (\n`;
    code += `    <div${tailwindClasses}${responsiveClass}${a11yAttrs}>\n`;
    code += `      <h1${styling === 'tailwind' ? ' className="text-2xl font-bold mb-4"' : ''}>${componentName}</h1>\n`;
    code += `      {/* Add your component content here */}\n`;
    code += `    </div>\n`;
    code += `  );\n`;
    code += `}\n`;

    return code;
}

/**
 * Generate template-based test file
 */
function generateTemplateTestFile(
    componentName: string,
    componentCode: string,
    props: ComponentProp[]
): string {
    let code = `import { render, screen } from '@testing-library/react';\n`;
    code += `import { describe, it, expect } from 'vitest';\n`;
    code += `import { ${componentName} } from '../${componentName}';\n\n`;

    code += `describe('${componentName}', () => {\n`;
    code += `  it('should render successfully', () => {\n`;

    // Mock props
    const mockProps: string[] = [];
    props.forEach(prop => {
        if (prop.required) {
            if (prop.type.includes('() => void') || prop.type.includes('=>')) {
                mockProps.push(`${prop.name}={() => {}}`);
            } else if (prop.type === 'boolean') {
                mockProps.push(`${prop.name}={true}`);
            } else if (prop.type.includes('[]')) {
                mockProps.push(`${prop.name}={[]}`);
            } else if (prop.type === 'string') {
                mockProps.push(`${prop.name}="test"`);
            } else if (prop.type === 'number') {
                mockProps.push(`${prop.name}={1}`);
            } else {
                mockProps.push(`${prop.name}={null as unknown}`);
            }
        }
    });

    const propsStr = mockProps.length > 0 ? ` ${mockProps.join(' ')}` : '';
    code += `    render(<${componentName}${propsStr} />);\n`;
    code += `    expect(screen.getByRole('main')).toBeInTheDocument();\n`;
    code += `  });\n\n`;

    code += `  it('should render with props', () => {\n`;
    code += `    render(<${componentName}${propsStr} />);\n`;
    code += `    expect(screen.getByText('${componentName}')).toBeInTheDocument();\n`;
    code += `  });\n`;
    code += `});\n`;

    return code;
}
