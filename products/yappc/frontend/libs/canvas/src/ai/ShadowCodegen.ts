/**
 * Shadow Codegen
 *
 * Background code generation from canvas state.
 * Converts visual designs to code in multiple frameworks.
 *
 * @doc.type service
 * @doc.purpose Code generation from canvas
 * @doc.layer ai
 * @doc.pattern Strategy Pattern
 */

import type {
    UniversalNode,
    UniversalDocument,
    ArtifactContract,
    ContentModality,
} from '../model/contracts';
import { getArtifactRegistry } from '../registry/ArtifactRegistry';

// ============================================================================
// Codegen Types
// ============================================================================

/**
 * Target framework for code generation
 */
export type CodegenTarget =
    | 'react'
    | 'react-native'
    | 'vue'
    | 'angular'
    | 'svelte'
    | 'html'
    | 'flutter'
    | 'swiftui';

/**
 * Code generation options
 */
export interface CodegenOptions {
    /** Target framework */
    target: CodegenTarget;
    /** Whether to use TypeScript */
    typescript?: boolean;
    /** Whether to include styling */
    includeStyles?: boolean;
    /** Style system to use */
    styleSystem?: 'css' | 'tailwind' | 'styled-components' | 'emotion' | 'inline';
    /** Whether to generate component files separately */
    splitComponents?: boolean;
    /** Whether to include comments */
    includeComments?: boolean;
    /** Custom component name mapping */
    componentMapping?: Record<string, string>;
    /** Custom import mappings */
    importMapping?: Record<string, string>;
    /** Indent size */
    indentSize?: number;
    /** Use single quotes */
    singleQuotes?: boolean;
}

/**
 * Generated code result
 */
export interface CodegenResult {
    /** Main component code */
    mainCode: string;
    /** Style code (if separate) */
    styleCode?: string;
    /** Additional component files */
    components?: Record<string, string>;
    /** Import statements needed */
    imports: string[];
    /** Dependencies required */
    dependencies: string[];
    /** Warnings during generation */
    warnings: string[];
}

/**
 * Code generator interface for extending with new targets
 */
export interface CodeGenerator {
    /** Target framework */
    target: CodegenTarget;
    /** Generate code for a node */
    generateNode: (
        node: UniversalNode,
        contract: ArtifactContract | undefined,
        options: CodegenOptions,
        depth: number
    ) => string;
    /** Generate imports */
    generateImports: (nodes: UniversalNode[], options: CodegenOptions) => string[];
    /** Generate styles */
    generateStyles?: (nodes: UniversalNode[], options: CodegenOptions) => string;
    /** Wrap in component */
    wrapComponent: (
        code: string,
        name: string,
        imports: string[],
        options: CodegenOptions
    ) => string;
}

// ============================================================================
// Shadow Codegen Class
// ============================================================================

/**
 * Shadow Codegen - Background code generation service
 */
export class ShadowCodegen {
    private static instance: ShadowCodegen | null = null;

    private generators: Map<CodegenTarget, CodeGenerator> = new Map();
    private cache: Map<string, CodegenResult> = new Map();
    private debounceTimers: Map<string, NodeJS.Timeout> = new Map();

    private constructor() {
        this.registerBuiltinGenerators();
    }

    /**
     * Get singleton instance
     */
    public static getInstance(): ShadowCodegen {
        if (!ShadowCodegen.instance) {
            ShadowCodegen.instance = new ShadowCodegen();
        }
        return ShadowCodegen.instance;
    }

    /**
     * Register a custom code generator
     */
    public registerGenerator(generator: CodeGenerator): void {
        this.generators.set(generator.target, generator);
    }

    /**
     * Generate code for a document
     */
    public generate(
        document: UniversalDocument,
        options: CodegenOptions
    ): CodegenResult {
        const cacheKey = this.getCacheKey(document, options);
        const cached = this.cache.get(cacheKey);

        if (cached) {
            return cached;
        }

        const generator = this.generators.get(options.target);
        if (!generator) {
            return {
                mainCode: `// No generator available for ${options.target}`,
                imports: [],
                dependencies: [],
                warnings: [`Unsupported target: ${options.target}`],
            };
        }

        const registry = getArtifactRegistry();
        const warnings: string[] = [];
        const allNodes = this.collectNodes(document.root.children);

        // Generate imports
        const imports = generator.generateImports(allNodes, options);

        // Generate node code
        const nodeCode = document.root.children
            .map((child) => {
                const contract = registry.get(child.kind);
                if (!contract) {
                    warnings.push(`No contract found for kind: ${child.kind}`);
                }
                return generator.generateNode(child, contract ?? undefined, options, 1);
            })
            .join('\n');

        // Generate styles if requested
        const styleCode =
            options.includeStyles && generator.generateStyles
                ? generator.generateStyles(allNodes, options)
                : undefined;

        // Wrap in component
        const componentName = this.toComponentName(document.name);
        const mainCode = generator.wrapComponent(nodeCode, componentName, imports, options);

        // Collect dependencies
        const dependencies = this.collectDependencies(options);

        const result: CodegenResult = {
            mainCode,
            styleCode,
            imports,
            dependencies,
            warnings,
        };

        this.cache.set(cacheKey, result);
        return result;
    }

    /**
     * Generate code for a single node
     */
    public generateNode(
        node: UniversalNode,
        options: CodegenOptions
    ): string {
        const generator = this.generators.get(options.target);
        if (!generator) {
            return `// No generator for ${options.target}`;
        }

        const registry = getArtifactRegistry();
        const contract = registry.get(node.kind);

        return generator.generateNode(node, contract ?? undefined, options, 0);
    }

    /**
     * Schedule background code generation (debounced)
     */
    public scheduleGeneration(
        documentId: string,
        document: UniversalDocument,
        options: CodegenOptions,
        callback: (result: CodegenResult) => void,
        debounceMs: number = 500
    ): void {
        // Clear existing timer
        const existingTimer = this.debounceTimers.get(documentId);
        if (existingTimer) {
            clearTimeout(existingTimer);
        }

        // Set new timer
        const timer = setTimeout(() => {
            const result = this.generate(document, options);
            callback(result);
            this.debounceTimers.delete(documentId);
        }, debounceMs);

        this.debounceTimers.set(documentId, timer);
    }

    /**
     * Clear cache
     */
    public clearCache(): void {
        this.cache.clear();
    }

    /**
     * Invalidate cache for a document
     */
    public invalidateCache(documentId: string): void {
        for (const key of this.cache.keys()) {
            if (key.startsWith(documentId)) {
                this.cache.delete(key);
            }
        }
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    private registerBuiltinGenerators(): void {
        // React generator
        this.generators.set('react', {
            target: 'react',
            generateNode: this.generateReactNode.bind(this),
            generateImports: this.generateReactImports.bind(this),
            generateStyles: this.generateReactStyles.bind(this),
            wrapComponent: this.wrapReactComponent.bind(this),
        });

        // React Native generator
        this.generators.set('react-native', {
            target: 'react-native',
            generateNode: this.generateReactNativeNode.bind(this),
            generateImports: this.generateReactNativeImports.bind(this),
            generateStyles: this.generateReactNativeStyles.bind(this),
            wrapComponent: this.wrapReactNativeComponent.bind(this),
        });

        // HTML generator
        this.generators.set('html', {
            target: 'html',
            generateNode: this.generateHtmlNode.bind(this),
            generateImports: () => [],
            generateStyles: this.generateCssStyles.bind(this),
            wrapComponent: this.wrapHtmlComponent.bind(this),
        });
    }

    private getCacheKey(document: UniversalDocument, options: CodegenOptions): string {
        return `${document.id}:${document.metadata.version}:${JSON.stringify(options)}`;
    }

    private collectNodes(nodes: UniversalNode[]): UniversalNode[] {
        const result: UniversalNode[] = [];

        const collect = (nodeList: UniversalNode[]) => {
            for (const node of nodeList) {
                result.push(node);
                if (node.children.length > 0) {
                    collect(node.children);
                }
            }
        };

        collect(nodes);
        return result;
    }

    private collectDependencies(options: CodegenOptions): string[] {
        const deps: string[] = [];

        switch (options.target) {
            case 'react':
                deps.push('react');
                if (options.styleSystem === 'styled-components') {
                    deps.push('styled-components');
                } else if (options.styleSystem === 'emotion') {
                    deps.push('@emotion/react', '@emotion/styled');
                } else if (options.styleSystem === 'tailwind') {
                    deps.push('tailwindcss');
                }
                break;
            case 'react-native':
                deps.push('react-native');
                break;
            case 'vue':
                deps.push('vue');
                break;
            case 'angular':
                deps.push('@angular/core');
                break;
        }

        return deps;
    }

    private toComponentName(name: string): string {
        return name
            .replace(/[^a-zA-Z0-9]/g, ' ')
            .split(' ')
            .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
            .join('');
    }

    private toKebabCase(name: string): string {
        return name
            .replace(/([a-z])([A-Z])/g, '$1-$2')
            .replace(/\s+/g, '-')
            .toLowerCase();
    }

    private indent(code: string, depth: number, size: number = 2): string {
        const spaces = ' '.repeat(depth * size);
        return code
            .split('\n')
            .map((line) => (line.trim() ? spaces + line : line))
            .join('\n');
    }

    // ============================================================================
    // React Generator
    // ============================================================================

    private generateReactNode(
        node: UniversalNode,
        contract: ArtifactContract | undefined,
        options: CodegenOptions,
        depth: number
    ): string {
        const indent = ' '.repeat(depth * (options.indentSize ?? 2));
        const componentName = options.componentMapping?.[node.kind] ?? this.mapToReactComponent(node.kind);
        const props = this.generateReactProps(node, options);
        const styleAttr = this.generateReactStyleAttr(node, options);

        const children = node.children
            .map((child) => {
                const childContract = getArtifactRegistry().get(child.kind);
                return this.generateReactNode(child, childContract ?? undefined, options, depth + 1);
            })
            .join('\n');

        // Text content
        const textContent = typeof node.content === 'string' ? node.content : node.props.text || node.props.label;

        if (children) {
            return `${indent}<${componentName}${props}${styleAttr}>\n${children}\n${indent}</${componentName}>`;
        } else if (textContent) {
            return `${indent}<${componentName}${props}${styleAttr}>${textContent}</${componentName}>`;
        } else {
            return `${indent}<${componentName}${props}${styleAttr} />`;
        }
    }

    private generateReactProps(node: UniversalNode, options: CodegenOptions): string {
        const excludeKeys = ['text', 'label', 'children'];
        const props: string[] = [];

        Object.entries(node.props).forEach(([key, value]) => {
            if (excludeKeys.includes(key) || value === undefined || value === null) return;

            if (typeof value === 'string') {
                const quote = options.singleQuotes ? "'" : '"';
                props.push(`${key}=${quote}${value}${quote}`);
            } else if (typeof value === 'boolean') {
                if (value) {
                    props.push(key);
                }
            } else if (typeof value === 'number') {
                props.push(`${key}={${value}}`);
            } else {
                props.push(`${key}={${JSON.stringify(value)}}`);
            }
        });

        return props.length > 0 ? ' ' + props.join(' ') : '';
    }

    private generateReactStyleAttr(node: UniversalNode, options: CodegenOptions): string {
        if (!options.includeStyles || options.styleSystem !== 'inline') {
            if (options.styleSystem === 'tailwind') {
                return this.generateTailwindClasses(node);
            }
            if (options.styleSystem === 'css') {
                return ` className="${this.toKebabCase(node.name)}"`;
            }
            return '';
        }

        const style = this.styleToReactStyle(node.style);
        if (Object.keys(style).length === 0) return '';

        return ` style={${JSON.stringify(style)}}`;
    }

    private generateTailwindClasses(node: UniversalNode): string {
        const classes: string[] = [];
        const style = node.style;

        // Map common styles to Tailwind classes
        if (style.backgroundColor) {
            classes.push(`bg-[${style.backgroundColor}]`);
        }
        if (style.padding !== undefined) {
            classes.push(`p-${Math.round((style.padding as number) / 4)}`);
        }
        if (style.borderRadius !== undefined) {
            classes.push(`rounded-${style.borderRadius === 9999 ? 'full' : Math.round((style.borderRadius as number) / 4)}`);
        }
        if (style.fontSize !== undefined) {
            classes.push(`text-[${style.fontSize}px]`);
        }

        return classes.length > 0 ? ` className="${classes.join(' ')}"` : '';
    }

    private styleToReactStyle(style: Record<string, unknown>): Record<string, unknown> {
        const result: Record<string, unknown> = {};

        Object.entries(style).forEach(([key, value]) => {
            if (value !== undefined && value !== null) {
                result[key] = value;
            }
        });

        return result;
    }

    private mapToReactComponent(kind: string): string {
        const mapping: Record<string, string> = {
            button: 'button',
            textInput: 'input',
            container: 'div',
            card: 'div',
            text: 'span',
            image: 'img',
            // Add more mappings
        };

        return mapping[kind] ?? 'div';
    }

    private generateReactImports(nodes: UniversalNode[], options: CodegenOptions): string[] {
        const imports: string[] = ["import React from 'react';"];

        if (options.styleSystem === 'styled-components') {
            imports.push("import styled from 'styled-components';");
        } else if (options.styleSystem === 'emotion') {
            imports.push("import styled from '@emotion/styled';");
        }

        return imports;
    }

    private generateReactStyles(nodes: UniversalNode[], options: CodegenOptions): string {
        if (options.styleSystem === 'css') {
            return this.generateCssStyles(nodes, options);
        }
        return '';
    }

    private wrapReactComponent(
        code: string,
        name: string,
        imports: string[],
        options: CodegenOptions
    ): string {
        const ts = options.typescript;
        const propsType = ts ? `interface ${name}Props {}\n\n` : '';
        const propsArg = ts ? `props: ${name}Props` : 'props';

        return `${imports.join('\n')}\n\n${propsType}export const ${name}${ts ? ': React.FC<' + name + 'Props>' : ''} = (${propsArg}) => {\n  return (\n${this.indent(code, 2, options.indentSize ?? 2)}\n  );\n};\n\nexport default ${name};\n`;
    }

    // ============================================================================
    // React Native Generator
    // ============================================================================

    private generateReactNativeNode(
        node: UniversalNode,
        contract: ArtifactContract | undefined,
        options: CodegenOptions,
        depth: number
    ): string {
        const indent = ' '.repeat(depth * (options.indentSize ?? 2));
        const componentName = this.mapToReactNativeComponent(node.kind);
        const props = this.generateReactProps(node, options);
        const styleAttr = ` style={styles.${this.toStyleName(node.name)}}`;

        const children = node.children
            .map((child) => {
                const childContract = getArtifactRegistry().get(child.kind);
                return this.generateReactNativeNode(child, childContract ?? undefined, options, depth + 1);
            })
            .join('\n');

        const textContent = typeof node.content === 'string' ? node.content : node.props.text || node.props.label;

        if (componentName === 'Text' || node.kind === 'text') {
            return `${indent}<Text${props}${styleAttr}>${textContent || ''}</Text>`;
        }

        if (children) {
            return `${indent}<${componentName}${props}${styleAttr}>\n${children}\n${indent}</${componentName}>`;
        } else if (textContent && componentName !== 'Image') {
            return `${indent}<${componentName}${props}${styleAttr}>\n${indent}  <Text>${textContent}</Text>\n${indent}</${componentName}>`;
        } else {
            return `${indent}<${componentName}${props}${styleAttr} />`;
        }
    }

    private mapToReactNativeComponent(kind: string): string {
        const mapping: Record<string, string> = {
            button: 'TouchableOpacity',
            textInput: 'TextInput',
            container: 'View',
            card: 'View',
            text: 'Text',
            image: 'Image',
        };

        return mapping[kind] ?? 'View';
    }

    private toStyleName(name: string): string {
        return name
            .replace(/[^a-zA-Z0-9]/g, '_')
            .replace(/^_+|_+$/g, '')
            .replace(/_+/g, '_');
    }

    private generateReactNativeImports(nodes: UniversalNode[], options: CodegenOptions): string[] {
        const components = new Set<string>(['View', 'StyleSheet']);

        const collectComponents = (nodeList: UniversalNode[]) => {
            for (const node of nodeList) {
                const component = this.mapToReactNativeComponent(node.kind);
                components.add(component);
                if (component === 'TouchableOpacity') {
                    components.add('Text');
                }
                collectComponents(node.children);
            }
        };

        collectComponents(nodes);

        return [
            `import { ${Array.from(components).sort().join(', ')} } from 'react-native';`,
        ];
    }

    private generateReactNativeStyles(nodes: UniversalNode[], options: CodegenOptions): string {
        const styles: Record<string, Record<string, unknown>> = {};

        const collectStyles = (nodeList: UniversalNode[]) => {
            for (const node of nodeList) {
                styles[this.toStyleName(node.name)] = this.styleToReactNativeStyle(node.style, node.transform);
                collectStyles(node.children);
            }
        };

        collectStyles(nodes);

        const styleEntries = Object.entries(styles)
            .map(([name, style]) => `  ${name}: ${JSON.stringify(style, null, 4).replace(/\n/g, '\n  ')}`)
            .join(',\n');

        return `const styles = StyleSheet.create({\n${styleEntries}\n});`;
    }

    private styleToReactNativeStyle(
        style: Record<string, unknown>,
        transform: { width: number; height: number }
    ): Record<string, unknown> {
        const result: Record<string, unknown> = {
            width: transform.width,
            height: transform.height,
        };

        // Copy valid React Native style properties
        const validProps = [
            'backgroundColor',
            'borderColor',
            'borderWidth',
            'borderRadius',
            'padding',
            'paddingTop',
            'paddingBottom',
            'paddingLeft',
            'paddingRight',
            'margin',
            'marginTop',
            'marginBottom',
            'marginLeft',
            'marginRight',
            'fontSize',
            'fontWeight',
            'color',
            'textAlign',
            'opacity',
        ];

        validProps.forEach((prop) => {
            if (style[prop] !== undefined) {
                result[prop] = style[prop];
            }
        });

        return result;
    }

    private wrapReactNativeComponent(
        code: string,
        name: string,
        imports: string[],
        options: CodegenOptions
    ): string {
        const ts = options.typescript;
        const propsType = ts ? `interface ${name}Props {}\n\n` : '';
        const propsArg = ts ? `props: ${name}Props` : 'props';

        return `${imports.join('\n')}\n\n${propsType}export const ${name} = (${propsArg}) => {\n  return (\n${this.indent(code, 2, options.indentSize ?? 2)}\n  );\n};\n\n${this.generateReactNativeStyles([], options)}\n\nexport default ${name};\n`;
    }

    // ============================================================================
    // HTML Generator
    // ============================================================================

    private generateHtmlNode(
        node: UniversalNode,
        contract: ArtifactContract | undefined,
        options: CodegenOptions,
        depth: number
    ): string {
        const indent = ' '.repeat(depth * (options.indentSize ?? 2));
        const tagName = this.mapToHtmlTag(node.kind);
        const attrs = this.generateHtmlAttrs(node, options);
        const classAttr = options.includeStyles ? ` class="${this.toKebabCase(node.name)}"` : '';

        const children = node.children
            .map((child) => {
                const childContract = getArtifactRegistry().get(child.kind);
                return this.generateHtmlNode(child, childContract ?? undefined, options, depth + 1);
            })
            .join('\n');

        const textContent = typeof node.content === 'string' ? node.content : node.props.text || node.props.label;

        if (children) {
            return `${indent}<${tagName}${classAttr}${attrs}>\n${children}\n${indent}</${tagName}>`;
        } else if (textContent) {
            return `${indent}<${tagName}${classAttr}${attrs}>${textContent}</${tagName}>`;
        } else if (['img', 'input', 'br', 'hr'].includes(tagName)) {
            return `${indent}<${tagName}${classAttr}${attrs} />`;
        } else {
            return `${indent}<${tagName}${classAttr}${attrs}></${tagName}>`;
        }
    }

    private mapToHtmlTag(kind: string): string {
        const mapping: Record<string, string> = {
            button: 'button',
            textInput: 'input',
            container: 'div',
            card: 'div',
            text: 'span',
            image: 'img',
        };

        return mapping[kind] ?? 'div';
    }

    private generateHtmlAttrs(node: UniversalNode, options: CodegenOptions): string {
        const attrs: string[] = [];

        if (node.kind === 'textInput') {
            attrs.push('type="text"');
            if (node.props.placeholder) {
                attrs.push(`placeholder="${node.props.placeholder}"`);
            }
        }

        if (node.kind === 'image' && node.props.src) {
            attrs.push(`src="${node.props.src}"`);
            attrs.push(`alt="${node.props.alt || node.name}"`);
        }

        if (node.kind === 'button' && node.props.disabled) {
            attrs.push('disabled');
        }

        return attrs.length > 0 ? ' ' + attrs.join(' ') : '';
    }

    private generateCssStyles(nodes: UniversalNode[], options: CodegenOptions): string {
        const rules: string[] = [];

        const generateRule = (node: UniversalNode) => {
            const selector = `.${this.toKebabCase(node.name)}`;
            const declarations: string[] = [];

            // Position and size
            declarations.push(`width: ${node.transform.width}px`);
            declarations.push(`height: ${node.transform.height}px`);

            // Copy style properties
            Object.entries(node.style).forEach(([key, value]) => {
                if (value !== undefined && value !== null) {
                    const cssKey = key.replace(/([A-Z])/g, '-$1').toLowerCase();
                    declarations.push(`${cssKey}: ${typeof value === 'number' && !['opacity', 'zIndex', 'fontWeight'].includes(key) ? value + 'px' : value}`);
                }
            });

            rules.push(`${selector} {\n  ${declarations.join(';\n  ')};\n}`);

            node.children.forEach(generateRule);
        };

        nodes.forEach(generateRule);

        return rules.join('\n\n');
    }

    private wrapHtmlComponent(
        code: string,
        name: string,
        imports: string[],
        options: CodegenOptions
    ): string {
        return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${name}</title>
  ${options.includeStyles ? '<link rel="stylesheet" href="styles.css">' : ''}
</head>
<body>
${this.indent(code, 1, options.indentSize ?? 2)}
</body>
</html>`;
    }
}

/**
 * Get singleton instance
 */
export function getShadowCodegen(): ShadowCodegen {
    return ShadowCodegen.getInstance();
}

export default ShadowCodegen;
