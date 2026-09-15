const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const scriptFile = path.join(__dirname, '../../main/assets/console-websocket.js');
function page(query = '?console=kvm&node=pve&vmid=100&resize=scale&path=stale') {
    const calls = [];
    const nativeCalls = [];
    const context = vm.createContext({
        URL, EventTarget, Event, MessageEvent, Blob, Uint8Array, ArrayBuffer, DOMException,
        TextEncoder, btoa, atob, setTimeout, clearTimeout,
        crypto: require('node:crypto').webcrypto,
        location: new URL('https://pve.example:8006/' + query),
        WebSocket: class { constructor(...args) { nativeCalls.push(['socket', ...args]); } },
        XMLHttpRequest: class {
            open(...args) { nativeCalls.push(['http', ...args]); }
            setRequestHeader() {}
            send() {}
        },
        PXMXConsoleHttp: {
            request(...args) { calls.push(['http', ...args]); },
            abort(...args) { calls.push(['abort', ...args]); },
        },
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

function upstream(context, file) {
    // Execute full upstream files offline; only replace ES module imports/exports.
    let source = fs.readFileSync(path.join(__dirname, 'fixtures/console-upstream', file), 'utf8');
    source = source.replace(/^import .*;\r?\n/gm, '').replace(/export default /g, '');
    vm.runInContext(source, context, {filename: file});
}

function pvePage(type = 'kvm') {
    const env = page(`?console=${type}&node=pve&vmid=100&resize=scale&path=stale`);
    Object.assign(env.context, {
        WebUtil: {getQueryVar: key => env.context.location.searchParams.get(key)},
        PVE: {CSRFPreventionToken: 'test-csrf', UserName: 'test@pam'},
        document: {}, innerWidth: 800, innerHeight: 600,
        confirm: () => true,
        Log: {Debug() {}, Info() {}},
    });
    upstream(env.context, 'pve.js');
    upstream(env.context, 'novnc-1.6-websock.js');
    vm.runInContext(`
        var settings = {};
        var statuses = [];
        var ui = {
            forceSetting: (key, value) => settings[key] = value,
            showStatus: (...args) => statuses.push(args),
            closePVECommandPanel() {},
        };
        var pve = new PVEUI(ui);
    `, env.context);
    return env;
}

function reply(context, request, data, status = 200, reason = 'OK', contentType = 'application/json; charset=utf-8') {
    context.__pxmxHttpEvent(request[1], status, reason, contentType, JSON.stringify({data}));
}

for (const [type, suffix, form] of [
    ['kvm', '/qemu/100/vncproxy', 'websocket=1'],
    ['lxc', '/lxc/100/vncproxy', 'websocket=1&width=800&height=600'],
    ['shell', '/vncshell', 'websocket=1&width=800&height=600'],
    ['upgrade', '/vncshell', 'websocket=1&upgrade=1'],
]) {
    test(`upstream PVEUI ${type} pveStart POSTs before attaching noVNC`, () => {
        const {context, calls, nativeCalls} = pvePage(type);
        vm.runInContext(`
            var websock = new Websock();
            pve.pveStart(password => {
                window.password = password;
                websock.open('wss://pve.example:8006/' + settings.path, 'binary');
            });
        `, context);
        assert.equal(nativeCalls.length, 0, 'bootstrap must not attempt native HTTP');
        assert.equal(calls.length, 1, 'must wait for the bootstrap response before opening a socket');
        assert.deepEqual(calls[0].slice(2), [
            'https://pve.example:8006/api2/json/nodes/pve' + suffix,
            'POST', 'application/x-www-form-urlencoded', 'test-csrf', form,
        ]);
        reply(context, calls[0], {port: 5901, ticket: 'test:+/&ticket'});
        assert.equal(context.password, 'test:+/&ticket');
        assert.equal(context.ui.reconnectPassword, 'test:+/&ticket');
        assert.equal(calls[1][0], 'connect');
        assert.match(calls[1][2], /vncwebsocket\?port=5901&vncticket=test%3A%2B%2F%26ticket$/);
        assert.equal(vm.runInContext('websock.readyState', context), 'connecting');
    });
}

function xtermPage(type = 'lxc') {
    const env = page(`?console=${type}&node=pve&vmid=101&cmd=login&cmd-opts=--test`);
    const elements = new Map();
    const timers = [];
    Object.assign(env.context, {
        PVE: {CSRFPreventionToken: 'test-csrf', UserName: 'test@pam'},
        document: {
            getElementById(id) {
                if (!elements.has(id)) elements.set(id, {classList: {add() {}, remove() {}}, addEventListener() {}});
                return elements.get(id);
            },
            createElement() { return {getContext() {return null;}}; },
        },
        localStorage: {getItem() {return null;}},
        console: {log() {}, warn() {}},
        FitAddon: {FitAddon: class {fit() {}}},
        Terminal: class {
            constructor() {this.writes = [];}
            open() {} loadAddon() {} focus() {} dispose() {}
            onResize(fn) {this.resizeHandler = fn;}
            onData(fn) {this.dataHandler = fn;}
            write(data) {this.writes.push(Array.from(data));}
        },
        setTimeout(fn) {timers.push(fn); return timers.length;},
        setInterval(fn) {timers.push(fn); return timers.length;},
        clearInterval() {}, addEventListener() {}, requestAnimationFrame(fn) {fn();},
    });
    upstream(env.context, 'xterm-util.js');
    upstream(env.context, 'xterm-main.js'); // Executes real createTerminal -> status -> startConnection.
    return {...env, timers, elements};
}

for (const [type, suffix, form] of [
    ['kvm', '/qemu/101/termproxy', ''],
    ['lxc', '/lxc/101/termproxy', ''],
    ['shell', '/termproxy', ''],
    ['upgrade', '/termproxy', 'cmd=upgrade'],
    ['cmd', '/termproxy', 'cmd=login&cmd-opts=--test'],
]) {
    test(`upstream xterm ${type} startup preserves status POST auth and terminal framing`, () => {
        const {context, calls, nativeCalls, timers} = xtermPage(type);
        if (type === 'kvm' || type === 'lxc') {
            assert.equal(calls[0][3], 'GET');
            assert.match(calls[0][2], /\/status\/current\?$/);
            reply(context, calls[0], {status: 'running'});
        }
        const request = calls.at(-1);
        assert.deepEqual(request.slice(2), [
            'https://pve.example:8006/api2/json/nodes/pve' + suffix,
            'POST', 'application/x-www-form-urlencoded', 'test-csrf', form,
        ]);
        assert.equal(calls.some(call => call[0] === 'connect'), false);
        reply(context, request, {port: 5902, ticket: 'test:+/&ticket'});
        assert.match(context.socketURL, /vncticket=test%3A%2B%2F%26ticket$/);
        assert.equal(context.socket.binaryType, 'arraybuffer');
        context.__pxmxSocketEvent(context.socket.id, 'open', 'binary');
        assert.deepEqual(calls.at(-1).slice(2), ['test@pam:test:+/&ticket\n', false]);
        context.__pxmxSocketEvent(context.socket.id, 'binary', btoa('OKhello'));
        assert.equal(context.state, context.states.connected);
        assert.deepEqual(context.term.writes[0], Array.from(Buffer.from('hello')));
        context.term.dataHandler('é');
        assert.deepEqual(calls.at(-1).slice(2), ['0:2:é', false]);
        context.term.resizeHandler({cols: 80, rows: 24});
        assert.deepEqual(calls.at(-1).slice(2), ['1:80:24:', false]);
        timers[0](); // Actual upstream keepalive callback.
        assert.deepEqual(calls.at(-1).slice(2), ['2', false]);
        assert.equal(nativeCalls.length, 0);
    });
}

for (const type of ['kvm', 'lxc']) {
    test(`upstream ${type} noVNC power controls keep POST form and CSRF`, () => {
        const {context, calls, nativeCalls} = pvePage(type);
        for (const cmd of type === 'kvm' ? ['start', 'shutdown', 'stop', 'reset', 'suspend', 'resume'] : ['start', 'shutdown', 'stop']) {
            context.pve.pve_vm_command(cmd, {timeout: 30});
            assert.deepEqual(calls.at(-1).slice(2), [
                `https://pve.example:8006/api2/json/nodes/pve/${type === 'kvm' ? 'qemu' : 'lxc'}/100/status/${cmd}`,
                'POST', 'application/x-www-form-urlencoded', 'test-csrf', 'timeout=30',
            ]);
        }
        assert.equal(nativeCalls.length, 0);
    });
    test(`upstream xterm ${type} stopped guest retains start control`, () => {
        const {context, calls, nativeCalls} = xtermPage(type);
        reply(context, calls[0], {status: 'stopped'});
        assert.equal(calls.length, 1);
        context.startGuest();
        assert.deepEqual(calls[1].slice(2), [
            `https://pve.example:8006/api2/json/nodes/pve/${type === 'kvm' ? 'qemu' : 'lxc'}/101/status/start`,
            'POST', 'application/x-www-form-urlencoded', 'test-csrf', '',
        ]);
        assert.equal(nativeCalls.length, 0);
    });
}

for (const status of [401, 403, 502]) {
    test(`upstream bootstrap reports HTTP ${status} without opening sockets`, () => {
        const pve = pvePage();
        pve.context.pve.pveStart(() => assert.fail('must not connect'));
        reply(pve.context, pve.calls[0], null, status, 'Denied');
        assert.equal(pve.context.statuses[0][0], `Error ${status}: Denied`);
        const xterm = xtermPage('shell');
        reply(xterm.context, xterm.calls[0], null, status, 'Denied');
        assert.equal(xterm.context.state, xterm.context.states.disconnected);
        assert.match(xterm.elements.get('status_bar').textContent, new RegExp(`Error ${status}: Denied`));
        assert.equal([...pve.calls, ...xterm.calls].some(call => call[0] === 'connect'), false);
    });
}

test('XHR abort discards stale completion and reopening isolates the new request', () => {
    const {context, calls} = page();
    vm.runInContext(`
        var xhr = new XMLHttpRequest();
        var events = [];
        xhr.onload = () => events.push(xhr.responseText);
        xhr.onabort = () => events.push('abort');
        xhr.open('POST', '/api2/json/nodes/pve/termproxy');
        xhr.send('');
        xhr.abort();
        xhr.open('POST', '/api2/json/nodes/pve/termproxy');
        xhr.send('');
    `, context);
    reply(context, calls[0], 'stale');
    reply(context, calls[2], 'new');
    assert.equal(JSON.stringify(context.events), '["abort","{\\"data\\":\\"new\\"}"]');
    assert.equal(context.xhr.readyState, 4);
    assert.equal(calls[1][0], 'abort');
});

for (const version of ['1.5', '1.6']) {
    test(`real noVNC ${version} Websock.attach accepts the bridge and buffers frames`, () => {
        const {context, calls, nativeCalls} = page();
        context.Log = {Debug() {}, Info() {}};
        upstream(context, `novnc-${version}-websock.js`);
        vm.runInContext(`
            var channel = new WebSocket('wss://pve.example:8006/socket', 'binary');
            var websock = new Websock();
            var events = [];
            websock.on('open', () => events.push('open'));
            websock.on('message', () => events.push(Array.from(websock.rQshiftBytes(3))));
            websock.on('close', e => events.push(e.code));
            websock.attach(channel);
            window.__pxmxSocketEvent(channel.id, 'open', 'binary');
            window.__pxmxSocketEvent(channel.id, 'binary', 'AID/');
            websock.sQpushBytes(new Uint8Array([0, 128, 255]));
            websock.flush();
            websock.close();
            window.__pxmxSocketEvent(channel.id, 'close', '1000:done');
        `, context);
        assert.equal(JSON.stringify(context.events), '["open",[0,128,255],1000]');
        assert.deepEqual(calls[1].slice(2), ['AID/', true]);
        assert.equal(nativeCalls.length, 0);
    });
}

test('WebSocket event-handler properties start as null', () => {
    const {context} = page();
    const socket = vm.runInContext("new WebSocket('wss://pve.example:8006/socket')", context);
    for (const name of ['onopen', 'onmessage', 'onerror', 'onclose']) assert.equal(socket[name], null);
});

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
