/**
 * @doc.type provider
 * @doc.purpose Webview provider for displaying YAPPC canvas in VS Code
 * @doc.layer product
 * @doc.pattern Webview Provider
 */

import * as vscode from 'vscode';

/**
 * Webview provider for displaying YAPPC canvas
 */
export class CanvasWebviewProvider {
    private panel: vscode.WebviewPanel | undefined = undefined;
    private readonly extensionUri: vscode.Uri;
    private readonly yappcServerPort = 5173;

    constructor(extensionUri: vscode.Uri) {
        this.extensionUri = extensionUri;
    }

    /**
     * Show the canvas webview panel
     */
    public show(): void {
        if (this.panel) {
            this.panel.reveal(vscode.ViewColumn.One);
        } else {
            this.panel = vscode.window.createWebviewPanel(
                'yappcCanvas',
                'YAPPC Canvas',
                vscode.ViewColumn.One,
                {
                    enableScripts: true,
                    retainContextWhenHidden: true,
                    localResourceRoots: [this.extensionUri]
                }
            );

            this.panel.webview.html = this.getHtmlContent(this.panel.webview);

            // Handle panel disposal
            this.panel.onDidDispose(
                () => {
                    this.panel = undefined;
                },
                null,
                []
            );

            // Handle messages from webview
            this.panel.webview.onDidReceiveMessage(
                message => {
                    switch (message.command) {
                        case 'alert':
                            vscode.window.showInformationMessage(message.text);
                            break;
                        case 'error':
                            vscode.window.showErrorMessage(message.text);
                            break;
                        case 'nodeSelected':
                            // Handle node selection from canvas
                            vscode.window.showInformationMessage(`Selected node: ${message.nodeId}`);
                            break;
                    }
                },
                undefined,
                []
            );
        }
    }

    /**
     * Send message to webview
     */
    public postMessage(message: any): void {
        if (this.panel) {
            this.panel.webview.postMessage(message);
        }
    }

    /**
     * Generate HTML content for webview
     */
    private getHtmlContent(webview: vscode.Webview): string {
        // Use asExternalUri for localhost communication
        const yappcUri = `http://localhost:${this.yappcServerPort}`;

        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta 
      http-equiv="Content-Security-Policy" 
      content="default-src 'none'; frame-src http://localhost:${this.yappcServerPort} ${webview.cspSource} https:; script-src ${webview.cspSource} 'unsafe-inline'; style-src ${webview.cspSource} 'unsafe-inline';"
    />
    <title>YAPPC Canvas</title>
    <style>
      body, html {
        margin: 0;
        padding: 0;
        width: 100%;
        height: 100vh;
        overflow: hidden;
      }
      iframe {
        width: 100%;
        height: 100%;
        border: none;
      }
      .loading {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100vh;
        font-family: var(--vscode-font-family);
        color: var(--vscode-foreground);
      }
      .error {
        display: none;
        padding: 20px;
        font-family: var(--vscode-font-family);
        color: var(--vscode-errorForeground);
      }
    </style>
</head>
<body>
    <div class="loading" id="loading">
      Loading YAPPC Canvas...
    </div>
    <div class="error" id="error">
      <h3>Failed to connect to YAPPC Canvas</h3>
      <p>Make sure the YAPPC development server is running on port ${this.yappcServerPort}</p>
      <p>Run: <code>pnpm dev</code> in the YAPPC project</p>
    </div>
    <iframe 
      id="canvas-frame" 
      src="${yappcUri}"
      sandbox="allow-scripts allow-same-origin allow-forms"
      style="display: none;"
    ></iframe>

    <script>
      const vscode = acquireVsCodeApi();
      const iframe = document.getElementById('canvas-frame');
      const loading = document.getElementById('loading');
      const error = document.getElementById('error');

      // Show iframe after load
      iframe.addEventListener('load', () => {
        loading.style.display = 'none';
        iframe.style.display = 'block';

        // Send message to canvas iframe
        iframe.contentWindow.postMessage({ 
          type: 'vscode-integration',
          source: 'yappc-extension' 
        }, '*');
      });

      // Handle load errors
      iframe.addEventListener('error', () => {
        loading.style.display = 'none';
        error.style.display = 'block';
        
        vscode.postMessage({
          command: 'error',
          text: 'Failed to load YAPPC Canvas. Is the dev server running?'
        });
      });

      // Listen for messages from iframe
      window.addEventListener('message', event => {
        // Only accept messages from YAPPC origin
        if (event.origin !== 'http://localhost:${this.yappcServerPort}') {
          return;
        }

        // Forward relevant messages to extension
        if (event.data.type === 'node-selected') {
          vscode.postMessage({
            command: 'nodeSelected',
            nodeId: event.data.nodeId
          });
        }
      });

      // Listen for messages from extension
      window.addEventListener('message', event => {
        const message = event.data;
        
        // Forward messages to canvas iframe
        if (iframe.contentWindow) {
          iframe.contentWindow.postMessage(message, '*');
        }
      });

      // Timeout check for server availability
      setTimeout(() => {
        if (loading.style.display !== 'none') {
          loading.style.display = 'none';
          error.style.display = 'block';
          vscode.postMessage({
            command: 'error',
            text: 'YAPPC Canvas connection timeout. Server may not be running.'
          });
        }
      }, 10000);
    </script>
</body>
</html>`;
    }
}
