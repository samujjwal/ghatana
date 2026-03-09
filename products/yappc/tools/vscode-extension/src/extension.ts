/**
 * @doc.type extension
 * @doc.purpose VS Code extension entry point for YAPPC Canvas Sync
 * @doc.layer product
 * @doc.pattern Extension Activation
 */

import * as vscode from 'vscode';
import { CanvasTreeView } from './views/CanvasTreeView';
import { FileSyncService } from './services/FileSyncService';
import { scaffoldCode } from './commands/scaffoldCode';
import { openInCanvas } from './commands/openInCanvas';
import { CanvasWebviewProvider } from './providers/webviewProvider';

/**
 * Extension activation
 */
export function activate(context: vscode.ExtensionContext) {
    console.log('YAPPC Canvas Sync extension is now active');

    // Initialize services
    const fileSyncService = new FileSyncService();
    const canvasTreeView = new CanvasTreeView(context, fileSyncService);
    const webviewProvider = new CanvasWebviewProvider(context.extensionUri);

    // Register tree view
    const treeView = vscode.window.createTreeView('yappcCanvasTree', {
        treeDataProvider: canvasTreeView,
        showCollapseAll: true,
    });

    // Register commands
    context.subscriptions.push(
        vscode.commands.registerCommand('yappc.openCanvas', () => webviewProvider.show()),
        vscode.commands.registerCommand('yappc.openInCanvas', () => openInCanvas(fileSyncService)),
        vscode.commands.registerCommand('yappc.scaffoldCode', () => scaffoldCode(fileSyncService)),
        vscode.commands.registerCommand('yappc.refreshCanvasTree', () => canvasTreeView.refresh()),
        vscode.commands.registerCommand('yappc.syncToCanvas', () => fileSyncService.syncAllToCanvas()),
        treeView
    );

    // Start file sync service
    fileSyncService.start();

    // Show welcome message
    vscode.window.showInformationMessage('YAPPC Canvas Sync: Connected to canvas');
}

/**
 * Extension deactivation
 */
export function deactivate() {
    console.log('YAPPC Canvas Sync extension is now inactive');
}
