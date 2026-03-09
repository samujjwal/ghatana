/**
 * @doc.type command
 * @doc.purpose Open current file in YAPPC canvas
 * @doc.layer product
 * @doc.pattern Command Handler
 */

import * as vscode from 'vscode';
import { FileSyncService } from '../services/FileSyncService';

/**
 * Open in canvas command
 */
export async function openInCanvas(fileSyncService: FileSyncService): Promise<void> {
    const editor = vscode.window.activeTextEditor;

    if (!editor) {
        vscode.window.showWarningMessage('No active editor');
        return;
    }

    const document = editor.document;
    const filePath = document.uri.fsPath;

    // Get canvas configuration
    const config = vscode.workspace.getConfiguration('yappc');
    const canvasUrl = config.get('canvasUrl', 'http://localhost:3000');

    // Get canvas nodes to find matching node
    const nodes = await fileSyncService.getCanvasStructure();
    const matchingNode = findNodeByFilePath(nodes, filePath);

    if (matchingNode) {
        // Open canvas with specific node focused
        const url = `${canvasUrl}/?nodeId=${matchingNode.id}`;
        await vscode.env.openExternal(vscode.Uri.parse(url));
        vscode.window.showInformationMessage(`Opened ${matchingNode.label} in canvas`);
    } else {
        // Open canvas without specific node
        await vscode.env.openExternal(vscode.Uri.parse(canvasUrl));
        vscode.window.showInformationMessage('Opened canvas (no matching node found)');
    }
}

/**
 * Find canvas node by file path
 */
function findNodeByFilePath(nodes: any[], filePath: string): any | null {
    for (const node of nodes) {
        if (node.filePath === filePath) {
            return node;
        }

        if (node.children) {
            const found = findNodeByFilePath(node.children, filePath);
            if (found) {
                return found;
            }
        }
    }

    return null;
}
