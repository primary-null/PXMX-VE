const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const scriptFile = path.join(__dirname, '../../main/assets/console-websocket.js');
function page() {
    const calls = [];
    const nativeCalls = [];
    const context = vm.createContext({
        URL, EventTarget, Event, MessageEvent, Blob, Uint8Array, ArrayBuffer, DOMException,
        TextEncoder, btoa, atob, setTimeout, clearTimeout,
        crypto: require('node:crypto').webcrypto,
        location: { href: 'https://pve.example:8006/?console=kvm' },
        WebSocket: class { constructor(...args) { nativeCalls.push(args); } },
        PXMXConsoleSocket: {
            connect(...args) { calls.push(['connect', ...args]); },
            send(...args) { calls.push(['send', ...args]); },
            close(...args) { calls.push(['close', ...args]); },
        },
    });
    context.window = context;
    vm.runInContext(fs.existsSync(scriptFile) ? fs.readFileSync(scriptFile, 'utf8') : '', context);
    return { context, calls, nativeCalls };
}

test('close preserves lifecycle and discards late messages', () => {
    const {context, calls} = page();
    vm.runInContext(`
        var socket = new WebSocket('wss://pve.example:8006/socket');
        var received = [];
        socket.onclose = e => received.push([e.code, e.reason, e.wasClean]);
        socket.onmessage = () => received.push('late');
        window.__pxmxSocketEvent(socket.id, 'open', '');
        socket.close(1000, 'done');
    `, context);
    assert.equal(context.socket.readyState, 2);
    assert.deepEqual(calls[1].slice(2), [1000, 'done']);
    vm.runInContext(`
        window.__pxmxSocketEvent(socket.id, 'close', '1000:done');
        window.__pxmxSocketEvent(socket.id, 'text', 'late');
    `, context);
    assert.equal(context.socket.readyState, 3);
    assert.equal(JSON.stringify(context.received), '[[1000,"done",true]]');
});

test('TLS failure emits error then abnormal close without falling back to native WebSocket', () => {
    const {context, nativeCalls} = page();
    vm.runInContext(`
        var socket = new WebSocket('wss://pve.example:8006/socket');
        var events = [];
        socket.onerror = () => events.push('error');
        socket.onclose = e => events.push([e.type, e.code, e.wasClean]);
        window.__pxmxSocketEvent(socket.id, 'error', 'TLS failed');
    `, context);
    assert.equal(JSON.stringify(context.events), JSON.stringify(['error', ['close', 1006, false]]));
    assert.equal(context.socket.readyState, 3);
    assert.equal(nativeCalls.length, 0);
});

test('bridge delivers noVNC binary frames and xterm text in order', () => {
    const { context, calls } = page();
    vm.runInContext(`
        var socket = new WebSocket('wss://pve.example:8006/socket');
        socket.binaryType = 'arraybuffer';
        var received = [];
        socket.onopen = () => received.push('open');
        socket.addEventListener('message', e => received.push(e.data));
        window.__pxmxSocketEvent(socket.id, 'open', 'binary');
        socket.send(new Uint8Array([0, 128, 255]));
        socket.send('terminal input');
        window.__pxmxSocketEvent(socket.id, 'binary', 'AID/');
        window.__pxmxSocketEvent(socket.id, 'text', 'terminal output');
    `, context);
    assert.equal(context.received[0], 'open');
    assert.deepEqual([...new Uint8Array(context.received[1])], [0, 128, 255]);
    assert.equal(context.received[2], 'terminal output');
    assert.deepEqual(calls.slice(1).map(c => [c[0], c[2], c[3]]), [
        ['send', 'AID/', true], ['send', 'terminal input', false]
    ]);
    assert.equal(context.socket.readyState, context.WebSocket.OPEN);
    assert.equal(context.socket.protocol, 'binary');
});

test('noVNC WebSocket connects through app TLS bridge, never native networking', () => {
    const {context, calls, nativeCalls} = page();
    vm.runInContext("new WebSocket('wss://pve.example:8006/api2/json/nodes/pve/vncwebsocket', ['binary'])", context);
    assert.equal(nativeCalls.length, 0, 'native WebSocket bypasses the certificate pin');
    assert.equal(calls[0][0], 'connect');
    assert.equal(calls[0][2], 'wss://pve.example:8006/api2/json/nodes/pve/vncwebsocket');
    assert.equal(calls[0][3], 'binary');
});
