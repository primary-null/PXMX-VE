// WebView does not intercept WebSocket handshakes. Keep the existing noVNC/xterm
// WebSocket API, but send it through the same pinned OkHttp transport as resources.
(() => {
    'use strict';
    const bridge = window.PXMXConsoleSocket;
    const httpBridge = window.PXMXConsoleHttp;
    const requests = new Map();
    const sockets = new Map();
    const generation = Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-');
    let sequence = 0;
    function emit(socket, event) {
        socket.dispatchEvent(event);
        const handler = socket['on' + event.type];
        if (typeof handler === 'function') handler.call(socket, event);
    }
    // Proxmox's API2Request uses asynchronous XHR, including form POST bodies
    // unavailable to shouldInterceptRequest. Do not recover a native XHR here.
    class ConsoleXMLHttpRequest extends EventTarget {
        constructor() {
            super();
            this.readyState = 0;
            this.status = 0;
            this.statusText = '';
            this.responseText = '';
            this.response = '';
            this.responseType = '';
            this.onload = this.onerror = this.onabort = this.onloadend = this.onreadystatechange = null;
            this.headers = {};
        }
        open(method, url, async = true, username = null, password = null) {
            if (!async || username !== null || password !== null) throw new DOMException('Unsupported console request', 'NotSupportedError');
            this.abort();
            this.method = String(method).toUpperCase();
            this.url = new URL(url, location.href).href;
            this.headers = {};
            this.status = 0;
            this.statusText = this.responseText = this.response = '';
            this.contentType = '';
            this.readyState = 1;
            emit(this, new Event('readystatechange'));
        }
        setRequestHeader(name, value) {
            if (this.readyState !== 1 || this.id) throw new DOMException('Request is not open', 'InvalidStateError');
            name = String(name).toLowerCase();
            if (!['content-type', 'csrfpreventiontoken', 'cache-control'].includes(name)) {
                throw new DOMException('Unsupported console header', 'SecurityError');
            }
            this.headers[name] = String(value);
        }
        getResponseHeader(name) {
            return this.readyState >= 2 && String(name).toLowerCase() === 'content-type' ? this.contentType : null;
        }
        send(body = null) {
            if (this.readyState !== 1 || this.id) throw new DOMException('Request is not open', 'InvalidStateError');
            if (body !== null && typeof body !== 'string') throw new DOMException('Expected form string', 'NotSupportedError');
            if (this.responseType !== '' && this.responseType !== 'text') throw new DOMException('Expected text response', 'NotSupportedError');
            const id = generation + '-' + (++sequence);
            this.id = id;
            requests.set(id, this);
            try {
                httpBridge.request(id, this.url, this.method, this.headers['content-type'] || '',
                    this.headers.csrfpreventiontoken || '', body === null ? '' : body);
            } catch (_) {
                // Report through upstream API2Request's onload failure path.
                setTimeout(() => window.__pxmxHttpEvent(id, 502, 'Console transport failed', 'text/plain', ''), 0);
            }
        }
        abort() {
            if (this.id) {
                requests.delete(this.id);
                httpBridge.abort(this.id);
                this.id = null;
                this.readyState = 4;
                this.status = 0;
                this.statusText = this.responseText = this.response = '';
                emit(this, new Event('readystatechange'));
                emit(this, new Event('abort'));
                emit(this, new Event('loadend'));
            }
            this.readyState = 0;
        }
    }
    Object.defineProperty(window, '__pxmxHttpEvent', {value: (id, status, reason, contentType, body) => {
        const xhr = requests.get(id);
        if (!xhr) return;
        requests.delete(id);
        xhr.id = null;
        xhr.status = status;
        xhr.statusText = reason;
        xhr.contentType = contentType;
        xhr.responseText = xhr.response = body;
        xhr.readyState = 4;
        emit(xhr, new Event('readystatechange'));
        emit(xhr, new Event('load'));
        emit(xhr, new Event('loadend'));
    }});
    Object.defineProperty(window, 'XMLHttpRequest', {value: ConsoleXMLHttpRequest, writable: false, configurable: false});

    class ConsoleWebSocket extends EventTarget {
        constructor(url, protocols = []) {
            super();
            this.url = new URL(url, location.href).href;
            this.readyState = 0;
            this.binaryType = 'blob';
            this.bufferedAmount = 0;
            this.protocol = '';
            this.extensions = '';
            // noVNC Websock.attach validates these before assigning handlers.
            this.onopen = this.onmessage = this.onerror = this.onclose = null;
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
