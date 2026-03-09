/**
 * @doc.type service
 * @doc.purpose Bidirectional file sync between VS Code and YAPPC canvas
 * @doc.layer product
 * @doc.pattern WebSocket Service
 */

import * as vscode from 'vscode';
import WebSocket from 'ws';
import { CanvasNode } from '../views/CanvasTreeView';

/**
 * File sync service
 */
export class FileSyncService {
    private ws: WebSocket.WebSocket | null = null;
    private canvasUrl: string;
    private autoSync: boolean;
    private syncInterval: number;
    private fileWatcher: vscode.FileSystemWatcher | null = null;
    private updateListeners: Array<() => void> = [];

    constructor() {
        const config = vscode.workspace.getConfiguration('yappc');
        this.canvasUrl = config.get('canvasUrl', 'http://localhost:3000');
        this.autoSync = config.get('enableAutoSync', false);
        this.syncInterval = config.get('syncInterval', 5000);
    }

    /**
     * Start the sync service
     */
    start(): void {
        this.connectWebSocket();
        if (this.autoSync) {
            this.startFileWatcher();
        }
    }

    /**
     * Stop the sync service
     */
    stop(): void {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
        if (this.fileWatcher) {
            this.fileWatcher.dispose();
            this.fileWatcher = null;
        }
    }

    /**
     * Connect to canvas via WebSocket
     */
    private connectWebSocket(): void {
        const wsUrl = this.canvasUrl.replace('http://', 'ws://').replace('https://', 'wss://');

        const ws = new WebSocket(`${wsUrl}/ws/sync`);
        this.ws = ws;

        ws.on('open', () => {
            console.log('Connected to YAPPC canvas');
            vscode.window.showInformationMessage('Connected to YAPPC canvas');
        });

        ws.on('message', (data: WebSocket.RawData) => {
            this.handleCanvasMessage(data.toString());
        });

        ws.on('error', (error) => {
            console.error('WebSocket error:', error);
            vscode.window.showErrorMessage('Failed to connect to YAPPC canvas');
        });

        ws.on('close', () => {
            console.log('Disconnected from YAPPC canvas');
            // Reconnect after 5 seconds
            setTimeout(() => this.connectWebSocket(), 5000);
        });
    }

    /**
     * Handle canvas update messages
     */
    private handleCanvasMessage(message: string): void {
        try {
            const data = JSON.parse(message);

            switch (data.type) {
                case 'node_updated':
                    this.handleNodeUpdate(data.node);
                    break;
                case 'canvas_structure':
                    this.notifyUpdateListeners();
                    break;
                default:
                    console.log('Unknown message type:', data.type);
            }
        } catch (error) {
            console.error('Failed to parse canvas message:', error);
        }
    }

    /**
     * Handle node update from canvas
     */
    private handleNodeUpdate(node: CanvasNode): void {
        if (node.filePath) {
            vscode.window.showInformationMessage(`Canvas node updated: ${node.label}`);
            this.notifyUpdateListeners();
        }
    }

    /**
     * Start file watcher for auto-sync
     */
    private startFileWatcher(): void {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) {
            return;
        }

        // Watch TypeScript/JavaScript files
        this.fileWatcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(workspaceFolders[0], '**/*.{ts,tsx,js,jsx}')
        );

        this.fileWatcher.onDidChange(uri => {
            this.syncFileToCanvas(uri);
        });

        this.fileWatcher.onDidCreate(uri => {
            this.syncFileToCanvas(uri);
        });

        this.fileWatcher.onDidDelete(uri => {
            this.deleteFileFromCanvas(uri);
        });
    }

    /**
     * Sync file to canvas
     */
    private async syncFileToCanvas(uri: vscode.Uri): Promise<void> {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }

        try {
            const document = await vscode.workspace.openTextDocument(uri);
            const content = document.getText();

            this.ws.send(JSON.stringify({
                type: 'file_updated',
                filePath: uri.fsPath,
                content,
                timestamp: Date.now(),
            }));
        } catch (error) {
            console.error('Failed to sync file:', error);
        }
    }

    /**
     * Delete file from canvas
     */
    private deleteFileFromCanvas(uri: vscode.Uri): void {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }

        this.ws.send(JSON.stringify({
            type: 'file_deleted',
            filePath: uri.fsPath,
            timestamp: Date.now(),
        }));
    }

    /**
     * Sync all files to canvas
     */
    async syncAllToCanvas(): Promise<void> {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) {
            vscode.window.showWarningMessage('No workspace folder open');
            return;
        }

        vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: 'Syncing files to canvas...',
            cancellable: false,
        }, async () => {
            const files = await vscode.workspace.findFiles('**/*.{ts,tsx,js,jsx}');

            for (const file of files) {
                await this.syncFileToCanvas(file);
            }

            vscode.window.showInformationMessage('All files synced to canvas');
        });
    }

    /**
     * Get canvas structure
     */
    async getCanvasStructure(): Promise<CanvasNode[]> {
        return new Promise((resolve) => {
            if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
                resolve([]);
                return;
            }

            const messageHandler = (data: WebSocket.Data) => {
                try {
                    const response = JSON.parse(data.toString());
                    if (response.type === 'canvas_structure') {
                        resolve(response.nodes || []);
                        this.ws?.removeListener('message', messageHandler);
                    }
                } catch (error) {
                    console.error('Failed to parse structure response:', error);
                    resolve([]);
                }
            };

            this.ws.on('message', messageHandler);

            this.ws.send(JSON.stringify({
                type: 'get_structure',
                timestamp: Date.now(),
            }));

            // Timeout after 5 seconds
            setTimeout(() => {
                this.ws?.removeListener('message', messageHandler);
                resolve([]);
            }, 5000);
        });
    }

    /**
     * Register canvas update listener
     */
    onCanvasUpdate(listener: () => void): void {
        this.updateListeners.push(listener);
    }

    /**
     * Notify update listeners
     */
    private notifyUpdateListeners(): void {
        this.updateListeners.forEach(listener => listener());
    }
}
