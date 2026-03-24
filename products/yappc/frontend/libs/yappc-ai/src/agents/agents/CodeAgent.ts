/**
 * Code Agent
 *
 * AI agent specialized in code generation and refactoring.
 * Capabilities:
 * - Generate new code from specifications
 * - Refactor existing code
 * - Generate tests
 * - Generate documentation
 * - Code transformations
 */

import { BaseAgent } from '../base/Agent';

import type { AgentConfig, TaskResult } from '../types';
import type { IAIService } from '../../core/index.js';

/**
 * Code generation input
 */
export interface CodeGenerationInput {
  /** Type of code generation task */
  type: 'component' | 'function' | 'test' | 'documentation' | 'refactor';
  /** Specification or requirements */
  specification: string;
  /** Target language/framework */
  language: string;
  framework?: string;
  /** Existing code (for refactoring) */
  existingCode?: string;
  /** Style preferences */
  style?: {
    typescript?: boolean;
    functional?: boolean;
    comments?: boolean;
    tests?: boolean;
  };
  /** Additional context */
  context?: Record<string, unknown>;
}

/**
 * Generated code result
 */
export interface CodeGenerationOutput {
  /** Generated code */
  code: string;
  /** Additional files (e.g., tests, styles) */
  additionalFiles?: Array<{
    filename: string;
    content: string;
    description: string;
  }>;
  /** Explanation of the generated code */
  explanation: string;
  /** Usage examples */
  examples?: string[];
  /** Dependencies to install */
  dependencies?: string[];
  /** Setup instructions */
  setup?: string[];
}

/**
 * Code Agent implementation
 */
export class CodeAgent extends BaseAgent<
  CodeGenerationInput,
  CodeGenerationOutput
> {
  /**
   *
   */
  constructor(config: Omit<AgentConfig, 'capabilities'>) {
    super({
      ...config,
      capabilities: [
        'code-generation',
        'refactoring',
        'testing',
        'documentation',
      ],
    });
  }

  /**
   * Execute code generation task
   */
  protected async executeTask(
    input: CodeGenerationInput
  ): Promise<TaskResult<CodeGenerationOutput>> {
    if (!this._aiService) {
      return {
        success: false,
        confidence: 0,
        errors: ['AI service is required for code generation'],
      };
    }

    try {
      let output: CodeGenerationOutput;

      switch (input.type) {
        case 'component':
          output = await this.generateComponent(input);
          break;
        case 'function':
          output = await this.generateFunction(input);
          break;
        case 'test':
          output = await this.generateTest(input);
          break;
        case 'documentation':
          output = await this.generateDocumentation(input);
          break;
        case 'refactor':
          output = await this.refactorCode(input);
          break;
        default:
          throw new Error(`Unknown generation type: ${input.type}`);
      }

      return {
        success: true,
        output,
        confidence: 0.85,
        suggestions: [
          'Review generated code before use',
          'Run tests to verify functionality',
          'Adjust code style to match project conventions',
        ],
      };
    } catch (error) {
      return {
        success: false,
        confidence: 0,
        errors: [(error as Error).message],
      };
    }
  }

  /**
   * Generate a React/Vue component
   */
  private async generateComponent(
    input: CodeGenerationInput
  ): Promise<CodeGenerationOutput> {
    const { specification, language, framework, style } = input;
    const useTypeScript = style?.typescript ?? true;
    const useFunctional = style?.functional ?? true;

    const prompt = this.buildComponentPrompt(
      specification,
      language,
      framework || 'react',
      useTypeScript,
      useFunctional
    );

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 2000,
      temperature: 0.7,
    });

    const code = this.extractCodeFromResponse(response.content);
    const additionalFiles: CodeGenerationOutput['additionalFiles'] = [];

    // Generate test if requested
    if (style?.tests) {
      const testCode = await this.generateTestForComponent(
        code,
        language,
        framework
      );
      additionalFiles.push({
        filename: this.getTestFilename(specification, language),
        content: testCode,
        description: 'Unit tests for the component',
      });
    }

    return {
      code,
      additionalFiles,
      explanation: `Generated ${framework} component based on specification`,
      examples: this.generateUsageExamples(code, framework),
      dependencies: this.extractDependencies(code, framework),
      setup: this.generateSetupInstructions(framework),
    };
  }

  /**
   * Generate a standalone function
   */
  private async generateFunction(
    input: CodeGenerationInput
  ): Promise<CodeGenerationOutput> {
    const { specification, language, style } = input;
    const useTypeScript = style?.typescript ?? true;

    const prompt = `Generate a ${language} function that ${specification}.
${useTypeScript ? 'Use TypeScript with full type annotations.' : ''}
Include:
1. Function implementation
2. JSDoc comments
3. Error handling
4. Input validation

Return only the code, no explanations.`;

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 1000,
      temperature: 0.5,
    });

    const code = this.extractCodeFromResponse(response.content);
    const additionalFiles: CodeGenerationOutput['additionalFiles'] = [];

    // Generate test if requested
    if (style?.tests) {
      const testPrompt = `Generate unit tests for this function using ${this.getTestFramework(language)}:\n\n${code}\n\nInclude edge cases and error scenarios.`;
      const testResponse = await this._aiService!.complete({
        messages: [{ role: 'user', content: testPrompt }],
        maxTokens: 800,
      });

      additionalFiles.push({
        filename: this.getTestFilename(specification, language),
        content: this.extractCodeFromResponse(testResponse.content),
        description: 'Unit tests',
      });
    }

    return {
      code,
      additionalFiles,
      explanation: `Generated ${language} function for: ${specification}`,
      examples: [`// Example usage:\n${this.generateFunctionExample(code)}`],
    };
  }

  /**
   * Generate tests for existing code
   */
  private async generateTest(
    input: CodeGenerationInput
  ): Promise<CodeGenerationOutput> {
    const { existingCode, language, specification } = input;

    if (!existingCode) {
      throw new Error('Existing code is required for test generation');
    }

    const testFramework = this.getTestFramework(language);
    const prompt = `Generate comprehensive unit tests for this code using ${testFramework}:

\`\`\`${language}
${existingCode}
\`\`\`

Requirements:
${specification}

Include:
1. Happy path tests
2. Edge cases
3. Error scenarios
4. Mocking where needed

Return only the test code.`;

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 1500,
      temperature: 0.6,
    });

    const code = this.extractCodeFromResponse(response.content);

    return {
      code,
      explanation: `Generated tests using ${testFramework}`,
      examples: ['Run tests with: npm test'],
      dependencies: this.getTestDependencies(testFramework),
    };
  }

  /**
   * Generate documentation
   */
  private async generateDocumentation(
    input: CodeGenerationInput
  ): Promise<CodeGenerationOutput> {
    const { existingCode, specification } = input;

    if (!existingCode) {
      throw new Error('Existing code is required for documentation generation');
    }

    const prompt = `Generate comprehensive documentation for this code:

\`\`\`
${existingCode}
\`\`\`

${specification ? `Focus on: ${specification}` : ''}

Include:
1. Overview and purpose
2. API documentation (params, returns)
3. Usage examples
4. Important notes or caveats

Format as Markdown.`;

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 1200,
      temperature: 0.7,
    });

    return {
      code: response.content,
      explanation: 'Generated Markdown documentation',
      examples: ['Include in README.md or docs/ folder'],
    };
  }

  /**
   * Refactor existing code
   */
  private async refactorCode(
    input: CodeGenerationInput
  ): Promise<CodeGenerationOutput> {
    const { existingCode, specification, language } = input;

    if (!existingCode) {
      throw new Error('Existing code is required for refactoring');
    }

    const prompt = `Refactor this ${language} code:

\`\`\`${language}
${existingCode}
\`\`\`

Refactoring goals:
${specification}

Maintain:
- Same functionality
- Existing public API
- Test compatibility

Improve:
- Code readability
- Performance
- Maintainability
- Best practices

Return the refactored code with comments explaining changes.`;

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 2000,
      temperature: 0.5,
    });

    const code = this.extractCodeFromResponse(response.content);

    return {
      code,
      explanation: 'Refactored code with improvements',
      examples: ['Review changes carefully', 'Run existing tests to verify'],
      setup: ['Compare with original using diff tools'],
    };
  }

  /**
   * Build component generation prompt
   */
  private buildComponentPrompt(
    spec: string,
    language: string,
    framework: string,
    useTypeScript: boolean,
    useFunctional: boolean
  ): string {
    return `Generate a ${framework} component that ${spec}.

Requirements:
- Language: ${useTypeScript ? 'TypeScript' : language}
- Style: ${useFunctional ? 'Functional component with hooks' : 'Class component'}
- Include prop types/interfaces
- Add JSDoc comments
- Follow best practices
- Include basic styling

Return only the component code, no explanations.`;
  }

  /**
   * Generate test for component
   */
  private async generateTestForComponent(
    code: string,
    language: string,
    framework?: string
  ): Promise<string> {
    const testLib =
      framework === 'react' ? 'React Testing Library' : 'Vue Test Utils';

    const prompt = `Generate tests for this component using ${testLib}:

\`\`\`${language}
${code}
\`\`\`

Include:
1. Rendering tests
2. Interaction tests
3. Props/state tests
4. Edge cases

Return only test code.`;

    const response = await this._aiService!.complete({
      messages: [{ role: 'user', content: prompt }],
      maxTokens: 1200,
    });

    return this.extractCodeFromResponse(response.content);
  }

  /**
   * Extract code blocks from AI response
   */
  private extractCodeFromResponse(response: string): string {
    // Extract code from markdown code blocks
    const codeBlockMatch = response.match(/```(?:\w+)?\n([\s\S]*?)\n```/);
    if (codeBlockMatch) {
      return codeBlockMatch[1].trim();
    }

    // Return as-is if no code block found
    return response.trim();
  }

  /**
   * Get test filename
   */
  private getTestFilename(spec: string, language: string): string {
    const name = spec.split(' ').slice(0, 3).join('-').toLowerCase();
    const ext = language === 'typescript' ? 'ts' : 'js';
    return `${name}.test.${ext}`;
  }

  /**
   * Get test framework for language
   */
  private getTestFramework(language: string): string {
    const frameworks: Record<string, string> = {
      javascript: 'Jest',
      typescript: 'Jest',
      python: 'pytest',
      java: 'JUnit',
      go: 'testing package',
    };

    return frameworks[language.toLowerCase()] || 'Jest';
  }

  /**
   * Get test dependencies
   */
  private getTestDependencies(framework: string): string[] {
    const deps: Record<string, string[]> = {
      Jest: ['jest', '@types/jest', 'ts-jest'],
      pytest: ['pytest', 'pytest-cov'],
      JUnit: ['junit:junit:4.13.2'],
    };

    return deps[framework] || [];
  }

  /**
   * Generate usage examples
   */
  private generateUsageExamples(code: string, framework?: string): string[] {
    const examples: string[] = [];

    if (framework === 'react') {
      const componentName = this.extractComponentName(code);
      examples.push(
        `import ${componentName} from './${componentName}';\n\nfunction App() {\n  return <${componentName} />;\n}`
      );
    }

    return examples;
  }

  /**
   * Extract component name from code
   */
  private extractComponentName(code: string): string {
    const match = code.match(/(?:function|const|class)\s+(\w+)/);
    return match ? match[1] : 'Component';
  }

  /**
   * Extract dependencies from code
   */
  private extractDependencies(code: string, framework?: string): string[] {
    const deps: string[] = [];

    if (framework === 'react') {
      deps.push('react');
      if (code.includes('useState') || code.includes('useEffect')) {
        // Already included in 'react'
      }
    }

    if (code.includes('@mui/material')) {
      deps.push('@mui/material', '@emotion/react', '@emotion/styled');
    }

    return [...new Set(deps)];
  }

  /**
   * Generate setup instructions
   */
  private generateSetupInstructions(framework?: string): string[] {
    const instructions: string[] = [];

    if (framework === 'react') {
      instructions.push('npm install react react-dom');
      instructions.push('Import the component in your app');
    }

    return instructions;
  }

  /**
   * Generate function usage example
   */
  private generateFunctionExample(code: string): string {
    const functionName = this.extractFunctionName(code);
    return `const result = ${functionName}(/* params */);\nconsole.log(result);`;
  }

  /**
   * Extract function name from code
   */
  private extractFunctionName(code: string): string {
    const match = code.match(/(?:function|const)\s+(\w+)/);
    return match ? match[1] : 'myFunction';
  }
}
