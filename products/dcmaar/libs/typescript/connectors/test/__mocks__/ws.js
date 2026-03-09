class WebSocketStub {
  constructor() {
    this.readyState = WebSocketStub.OPEN;
    this._handlers = new Map();
  }

  on(event, handler) {
    this._handlers.set(event, handler);
    return this;
  }

  send() {}

  close() {
    this.readyState = WebSocketStub.CLOSED;
  }

  terminate() {
    this.readyState = WebSocketStub.CLOSED;
  }

  ping() {}

  pong() {}

  removeAllListeners() {
    this._handlers.clear();
  }
}

WebSocketStub.OPEN = 1;
WebSocketStub.CLOSING = 2;
WebSocketStub.CLOSED = 3;
WebSocketStub.CONNECTING = 0;

module.exports = WebSocketStub;
module.exports.default = WebSocketStub;
