// WebView does not intercept WebSocket handshakes. Keep the existing noVNC/xterm
// WebSocket API, but send it through the same pinned OkHttp transport as resources.
(() => {
    'use strict';
    const bridge = window.PXMXConsoleSocket;
    const sockets = new Map();
    const generation = Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-');
    let sequence = 0;
    function emit(socket, event) {
        socket.dispatchEvent(event);
        const handler = socket['on' + event.type];
        if (typeof handler === 'function') handler.call(socket, event);
    }
    class ConsoleWebSocket extends EventTarget {
        constructor(url, protocols = []) {
            super();
            this.url = new URL(url, location.href).href;
            this.readyState = 0;
            this.binaryType = 'blob';
            this.bufferedAmount = 0;
            this.protocol = '';
            this.extensions = '';
            this.id = generation + '-' + (++sequence);
            sockets.set(this.id, this);
            bridge.connect(this.id, this.url, typeof protocols === 'string' ? protocols : protocols.join(','));
        }
        close(code = 1000, reason = '') {
            if (code !== 1000 && (code < 3000 || code > 4999)) throw new DOMException('Invalid close code', 'InvalidAccessError');
            if (new TextEncoder().encode(reason).length > 123) throw new DOMException('Close reason too long', 'SyntaxError');
            if (this.readyState >= 2) return;
            this.readyState = 2;
            bridge.close(this.id, code, reason);
        }
        send(data) {
            if (this.readyState !== 1) throw new DOMException('WebSocket is not open', 'InvalidStateError');
            if (typeof data === 'string') {
                bridge.send(this.id, data, false);
            } else {
                const bytes = ArrayBuffer.isView(data)
                    ? new Uint8Array(data.buffer, data.byteOffset, data.byteLength) : new Uint8Array(data);
                let binary = '';
                for (let i = 0; i < bytes.length; i += 8192) {
                    binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
                }
                bridge.send(this.id, btoa(binary), true);
            }
        }
    }
    for (const [name, value] of Object.entries({CONNECTING: 0, OPEN: 1, CLOSING: 2, CLOSED: 3})) {
        Object.defineProperty(ConsoleWebSocket, name, {value});
        Object.defineProperty(ConsoleWebSocket.prototype, name, {value});
    }
    Object.defineProperty(window, '__pxmxSocketEvent', {value: (id, kind, data) => {
        const socket = sockets.get(id);
        if (!socket) return;
        let event;
        if (kind === 'open') {
            socket.readyState = 1;
            socket.protocol = data;
            event = new Event('open');
        } else if (kind === 'text' || kind === 'binary') {
            if (kind === 'binary') {
                const bytes = Uint8Array.from(atob(data), c => c.charCodeAt(0));
                data = socket.binaryType === 'arraybuffer' ? bytes.buffer : new Blob([bytes]);
            }
            event = new MessageEvent('message', {data});
        } else if (kind === 'close') {
            socket.readyState = 3;
            sockets.delete(id);
            const separator = data.indexOf(':');
            event = new Event('close');
            Object.assign(event, {code: Number(data.slice(0, separator)), reason: data.slice(separator + 1), wasClean: true});
        } else if (kind === 'error') {
            socket.readyState = 3;
            sockets.delete(id);
            emit(socket, new Event('error'));
            event = new Event('close');
            Object.assign(event, {code: 1006, reason: '', wasClean: false});
        } else return;
        emit(socket, event);
    }});
    Object.defineProperty(window, 'WebSocket', {value: ConsoleWebSocket, writable: false, configurable: false});
})();
