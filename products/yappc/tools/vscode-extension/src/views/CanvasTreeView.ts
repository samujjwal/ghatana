/**
 * @doc.type view
 * @doc.purpose Tree view for YAPPC canvas nodes
 * @doc.layer product
 * @doc.pattern TreeView Provider
 */

import * as vscode from 'vscode';
import * as path from 'path';
import { FileSyncService } from '../services/FileSyncService';

/**
 * Canvas node interface
 */
export interface CanvasNode {
    id: string;
    type: string;
    label: string;
    filePath?: string;
    children?: CanvasNode[];
}

/**
 * Tree item for canvas nodes
 */
export class CanvasTreeItem extends vscode.TreeItem {
    constructor(
        public readonly node: CanvasNode,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly command?: vscode.Command
    ) {
        super(node.label, collapsibleState);

        this.tooltip = `${node.type}: ${node.label}`;
        this.description = node.type;

        if (node.filePath) {
            this.resourceUri = vscode.Uri.file(node.filePath);
            this.command = {
                command: 'vscode.open',
                title: 'Open File',
                arguments: [this.resourceUri],
            };
        }

        this.iconPath = this.getIconPath(node.type);
    }

    private getIconPath(type: string): vscode.ThemeIcon {
        switch (type) {
            case 'component':
                return new vscode.ThemeIcon('symbol-class');
            case 'api':
                return new vscode.ThemeIcon('symbol-method');
            case 'database':
                return new vscode.ThemeIcon('database');
            case 'service':
                return new vscode.ThemeIcon('gear');
            case 'screen':
                return new vscode.ThemeIcon('device-mobile');
            default:
                return new vscode.ThemeIcon('circle-outline');
        }
    }
}

/**
 * Canvas tree view provider
 */
export class CanvasTreeView implements vscode.TreeDataProvider<CanvasTreeItem> {
    private _onDidChangeTreeData = new vscode.EventEmitter<CanvasTreeItem | undefined | null | void>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    private nodes: CanvasNode[] = [];

    constructor(
        private context: vscode.ExtensionContext,
        private fileSyncService: FileSyncService
    ) {
        this.loadCanvasNodes();

        // Listen for canvas updates
        this.fileSyncService.onCanvasUpdate(() => {
            this.loadCanvasNodes();
            this.refresh();
        });
    }

    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: CanvasTreeItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: CanvasTreeItem): Thenable<CanvasTreeItem[]> {
        if (!element) {
            // Root level
            return Promise.resolve(this.nodes.map(node => this.createTreeItem(node)));
        }

        // Children
        if (element.node.children) {
            return Promise.resolve(element.node.children.map(child => this.createTreeItem(child)));
        }

        return Promise.resolve([]);
    }

    private createTreeItem(node: CanvasNode): CanvasTreeItem {
        const hasChildren = node.children && node.children.length > 0;
        const collapsibleState = hasChildren
            ? vscode.TreeItemCollapsibleState.Collapsed
            : vscode.TreeItemCollapsibleState.None;

        return new CanvasTreeItem(node, collapsibleState);
    }

    private async loadCanvasNodes(): Promise<void> {
        // Load canvas structure from sync service
        this.nodes = await this.fileSyncService.getCanvasStructure();
    }
}
