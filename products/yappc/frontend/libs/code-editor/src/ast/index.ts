/**
 * AST parser for React component analysis.
 *
 * <p><b>Purpose</b><br>
 * Provides TypeScript AST parsing utilities for extracting component metadata,
 * prop types, and component structure information.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ComponentParser } from '@ghatana/yappc-code-editor/ast';
 *
 * const parser = new ComponentParser();
 * const metadata = parser.parse(componentCode);
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose AST parsing for components
 * @doc.layer product
 * @doc.pattern Parser
 */

import * as ts from 'typescript';
import type {
  ComponentMetadata,
  PropDefinition,
  TypeDefinition,
  InterfaceDefinition,
} from './types';

/**
 * Parser for React component AST analysis.
 *
 * <p><b>Purpose</b><br>
 * Parses TypeScript/JavaScript component code to extract metadata including
 * prop types, component structure, and type definitions.
 *
 * @doc.type class
 * @doc.purpose Component AST parser
 * @doc.layer product
 * @doc.pattern Parser
 */
export class ComponentParser {
  /**
   * Parses component source code and extracts metadata.
   *
   * <p><b>Purpose</b><br>
   * Analyzes component code to extract comprehensive metadata including
   * prop definitions, type information, and component structure.
   *
   * @param code - Component source code
   * @param filePath - File path for context
   * @returns Component metadata
   *
   * @doc.type method
   * @doc.purpose Parse component code
   * @doc.layer product
   * @doc.pattern Parser
   */
  parse(code: string, filePath: string = 'component.tsx'): ComponentMetadata {
    const sourceFile = ts.createSourceFile(
      filePath,
      code,
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TSX
    );

    const props = this.extractProps(sourceFile);
    const types = this.extractTypes(sourceFile);
    const interfaces = this.extractInterfaces(sourceFile);
    const componentName = this.extractComponentName(sourceFile);

    return {
      name: componentName,
      file: filePath,
      props,
      types,
      interfaces,
      source: code,
    };
  }

  /**
   * Extracts prop definitions from component.
   *
   * <p><b>Purpose</b><br>
   * Analyzes component props interface or function parameters to extract
   * prop definitions with type information.
   *
   * @param sourceFile - TypeScript source file
   * @returns Array of prop definitions
   *
   * @doc.type method
   * @doc.purpose Extract props
   * @doc.layer product
   * @doc.pattern Parser
   */
  private extractProps(sourceFile: ts.SourceFile): PropDefinition[] {
    const props: PropDefinition[] = [];

    const visit = (node: ts.Node) => {
      // Look for Props interface
      if (
        ts.isInterfaceDeclaration(node) &&
        node.name.text === 'Props'
      ) {
        if (node.members) {
          for (const member of node.members) {
            if (ts.isPropertySignature(member) && member.name) {
              const propName = this.getNodeText(member.name, sourceFile);
              const propType = member.type
                ? this.getNodeText(member.type, sourceFile)
                : 'unknown';
              const isRequired = !member.questionToken;

              props.push({
                name: propName,
                type: propType,
                required: isRequired,
              });
            }
          }
        }
      }

      ts.forEachChild(node, visit);
    };

    visit(sourceFile);
    return props;
  }

  /**
   * Extracts type definitions from component.
   *
   * <p><b>Purpose</b><br>
   * Finds all type definitions in the component file.
   *
   * @param sourceFile - TypeScript source file
   * @returns Array of type definitions
   *
   * @doc.type method
   * @doc.purpose Extract types
   * @doc.layer product
   * @doc.pattern Parser
   */
  private extractTypes(sourceFile: ts.SourceFile): TypeDefinition[] {
    const types: TypeDefinition[] = [];

    const visit = (node: ts.Node) => {
      if (ts.isTypeAliasDeclaration(node)) {
        const typeName = node.name.text;
        const typeText = this.getNodeText(node.type, sourceFile);

        types.push({
          name: typeName,
          definition: typeText,
          kind: 'type-alias',
        });
      }

      ts.forEachChild(node, visit);
    };

    visit(sourceFile);
    return types;
  }

  /**
   * Extracts interface definitions from component.
   *
   * <p><b>Purpose</b><br>
   * Finds all interface definitions in the component file.
   *
   * @param sourceFile - TypeScript source file
   * @returns Array of interface definitions
   *
   * @doc.type method
   * @doc.purpose Extract interfaces
   * @doc.layer product
   * @doc.pattern Parser
   */
  private extractInterfaces(sourceFile: ts.SourceFile): InterfaceDefinition[] {
    const interfaces: InterfaceDefinition[] = [];

    const visit = (node: ts.Node) => {
      if (ts.isInterfaceDeclaration(node)) {
        const interfaceName = node.name.text;
        const members: Record<string, string> = {};

        if (node.members) {
          for (const member of node.members) {
            if (ts.isPropertySignature(member) && member.name) {
              const memberName = this.getNodeText(member.name, sourceFile);
              const memberType = member.type
                ? this.getNodeText(member.type, sourceFile)
                : 'unknown';

              members[memberName] = memberType;
            }
          }
        }

        interfaces.push({
          name: interfaceName,
          members,
        });
      }

      ts.forEachChild(node, visit);
    };

    visit(sourceFile);
    return interfaces;
  }

  /**
   * Extracts component name from source file.
   *
   * <p><b>Purpose</b><br>
   * Determines the component name from export statement or file name.
   *
   * @param sourceFile - TypeScript source file
   * @returns Component name
   *
   * @doc.type method
   * @doc.purpose Extract component name
   * @doc.layer product
   * @doc.pattern Parser
   */
  private extractComponentName(sourceFile: ts.SourceFile): string {
    let componentName = 'Component';

    const visit = (node: ts.Node) => {
      // Look for function declaration or const with export
      if (
        (ts.isFunctionDeclaration(node) || ts.isVariableStatement(node)) &&
        node.modifiers?.some((m) => m.kind === ts.SyntaxKind.ExportKeyword)
      ) {
        if (ts.isFunctionDeclaration(node)) {
          componentName = node.name?.text || componentName;
        } else if (ts.isVariableStatement(node)) {
          const declaration = node.declarationList.declarations[0];
          if (declaration.name && ts.isIdentifier(declaration.name)) {
            componentName = declaration.name.text;
          }
        }
      }

      ts.forEachChild(node, visit);
    };

    visit(sourceFile);
    return componentName;
  }

  /**
   * Gets text representation of a node.
   *
   * <p><b>Purpose</b><br>
   * Extracts the text content of an AST node.
   *
   * @param node - AST node
   * @param sourceFile - Source file
   * @returns Node text
   *
   * @doc.type method
   * @doc.purpose Get node text
   * @doc.layer product
   * @doc.pattern Utility
   */
  private getNodeText(node: ts.Node, sourceFile: ts.SourceFile): string {
    return node.getText(sourceFile).trim();
  }
}

export type { ComponentMetadata, PropDefinition, TypeDefinition, InterfaceDefinition } from './types';
