export class WebSocketBridgeTransport {
    constructor(options) {
        this.socket = null;
        this.messageHandler = () => { };
        this.errorHandler = () => { };
        this.closeHandler = () => { };
        this.options = { connectTimeoutMs: 5000, ...options };
    }
    async connect() {
        if (this.socket && this.socket.readyState === 1) {
            return;
        }
        const WebSocketCtor = typeof globalThis !== 'undefined' ? globalThis.WebSocket : undefined;
        if (!WebSocketCtor) {
            throw new Error('WebSocketBridgeTransport requires WebSocket support in the current environment');
        }
        if (this.connectPromise) {
            return this.connectPromise;
        }
        this.connectPromise = new Promise((resolve, reject) => {
            try {
                this.socket = new WebSocketCtor(this.options.url, this.options.protocols);
            }
            catch (error) {
                this.connectPromise = undefined;
                reject(error);
                return;
            }
            const socket = this.socket;
            let settled = false;
            const timeout = setTimeout(() => {
                if (!settled) {
                    settled = true;
                    socket.close();
                    this.connectPromise = undefined;
                    reject(new Error(`Timed out connecting to ${this.options.url}`));
                }
            }, this.options.connectTimeoutMs);
            socket.addEventListener('open', () => {
                if (settled) {
                    return;
                }
                clearTimeout(timeout);
                settled = true;
                this.connectPromise = undefined;
                resolve();
            });
            socket.addEventListener('message', (event) => {
                const data = typeof event.data === 'string' ? event.data : String(event.data);
                this.messageHandler(data);
            });
            socket.addEventListener('error', (event) => {
                if (typeof this.errorHandler === 'function') {
                    this.errorHandler(event instanceof Error ? event : new Error('WebSocket error'));
                }
            });
            socket.addEventListener('close', (event) => {
                if (typeof this.closeHandler === 'function') {
                    this.closeHandler(event.code, event.reason);
                }
                this.socket = null;
                this.connectPromise = undefined;
            });
        });
        return this.connectPromise;
    }
    async disconnect() {
        if (this.socket && this.socket.readyState <= 1) {
            this.socket.close();
        }
        this.socket = null;
        this.connectPromise = undefined;
    }
    async send(data) {
        if (!this.socket || this.socket.readyState !== 1) {
            throw new Error('WebSocketBridgeTransport is not connected');
        }
        this.socket.send(data);
    }
    setMessageHandler(handler) {
        this.messageHandler = handler;
    }
    setErrorHandler(handler) {
        this.errorHandler = handler;
    }
    setCloseHandler(handler) {
        this.closeHandler = handler;
    }
}
