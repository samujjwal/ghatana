/**
 * @doc.type command
 * @doc.purpose Scaffold code from canvas node selection
 * @doc.layer product
 * @doc.pattern Command Handler
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { FileSyncService } from '../services/FileSyncService';

/**
 * Scaffold code command
 */
export async function scaffoldCode(fileSyncService: FileSyncService): Promise<void> {
    // Get canvas nodes
    const nodes = await fileSyncService.getCanvasStructure();

    if (nodes.length === 0) {
        vscode.window.showWarningMessage('No canvas nodes available');
        return;
    }

    // Show quick pick for node selection
    const items = nodes.map(node => ({
        label: node.label,
        description: node.type,
        node,
    }));

    const selected = await vscode.window.showQuickPick(items, {
        placeHolder: 'Select a canvas node to scaffold code',
    });

    if (!selected) {
        return;
    }

    // Scaffold based on node type
    switch (selected.node.type) {
        case 'component':
            await scaffoldComponent(selected.node);
            break;
        case 'api':
            await scaffoldAPI(selected.node);
            break;
        case 'service':
            await scaffoldService(selected.node);
            break;
        case 'database':
            await scaffoldSchema(selected.node);
            break;
        default:
            vscode.window.showWarningMessage(`No scaffold template for type: ${selected.node.type}`);
    }
}

/**
 * Scaffold React component
 */
async function scaffoldComponent(node: any): Promise<void> {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders) {
        return;
    }

    const componentName = node.label.replace(/\s+/g, '');
    const fileName = `${componentName}.tsx`;
    const filePath = path.join(workspaceFolders[0].uri.fsPath, 'src', 'components', fileName);

    const template = `import React from 'react';

export interface ${componentName}Props {
    // Add props here
}

/**
 * ${node.label} component
 */
export const ${componentName}: React.FC<${componentName}Props> = (props) => {
    return (
        <div>
            <h1>${node.label}</h1>
            {/* Add component content */}
        </div>
    );
};
`;

    await writeFile(filePath, template);
    await vscode.window.showTextDocument(vscode.Uri.file(filePath));
}

/**
 * Scaffold API endpoint
 */
async function scaffoldAPI(node: any): Promise<void> {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders) {
        return;
    }

    const fileName = `${node.label.toLowerCase().replace(/\s+/g, '-')}.ts`;
    const filePath = path.join(workspaceFolders[0].uri.fsPath, 'src', 'api', fileName);

    const template = `import { Request, Response } from 'express';

/**
 * ${node.label} API endpoint
 */
export async function ${node.label.replace(/\s+/g, '')}(req: Request, res: Response): Promise<void> {
    try {
        // TODO: Implement endpoint logic
        res.json({ message: 'Not implemented' });
    } catch (error) {
        console.error('API error:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}
`;

    await writeFile(filePath, template);
    await vscode.window.showTextDocument(vscode.Uri.file(filePath));
}

/**
 * Scaffold service
 */
async function scaffoldService(node: any): Promise<void> {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders) {
        return;
    }

    const serviceName = node.label.replace(/\s+/g, '');
    const fileName = `${serviceName}.ts`;
    const filePath = path.join(workspaceFolders[0].uri.fsPath, 'src', 'services', fileName);

    const template = `/**
 * ${node.label} service
 */
export class ${serviceName} {
    constructor() {
        // Initialize service
    }

    /**
     * Service method
     */
    async execute(): Promise<void> {
        // TODO: Implement service logic
    }
}
`;

    await writeFile(filePath, template);
    await vscode.window.showTextDocument(vscode.Uri.file(filePath));
}

/**
 * Scaffold database schema
 */
async function scaffoldSchema(node: any): Promise<void> {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders) {
        return;
    }

    const tableName = node.label.toLowerCase().replace(/\s+/g, '_');
    const fileName = `${tableName}.sql`;
    const filePath = path.join(workspaceFolders[0].uri.fsPath, 'db', 'migrations', fileName);

    const template = `-- ${node.label} table schema

CREATE TABLE IF NOT EXISTS ${tableName} (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_${tableName}_created_at ON ${tableName}(created_at);
`;

    await writeFile(filePath, template);
    await vscode.window.showTextDocument(vscode.Uri.file(filePath));
}

/**
 * Write file to disk
 */
async function writeFile(filePath: string, content: string): Promise<void> {
    const directory = path.dirname(filePath);

    // Create directory if it doesn't exist
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
    }

    fs.writeFileSync(filePath, content, 'utf8');
    vscode.window.showInformationMessage(`Scaffolded: ${path.basename(filePath)}`);
}
