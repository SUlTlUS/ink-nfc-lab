const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../assets/mifare-compat.js'), 'utf8');
let handler;
let pending = 1;
let writes = 0;
let detached = false;
let expiry;
const messages = [];
const context = {
    Process: {getModuleByName: () => ({path: 'fixture', enumerateExports: () => ['getNumDiscNtfEv', 'setNumDiscNtfEi', 'createNativeNfcTag'].map(name => ({name, address: name}))})},
    NativeFunction: function (address) {
        return address === 'getNumDiscNtfEv' ? () => pending : (_, value) => { pending = value; writes++; };
    },
    Interceptor: {attach: (_, callbacks) => { handler = callbacks.onEnter; return {detach: () => { detached = true; }}; }},
    send: message => messages.push(message),
    setTimeout: (callback, milliseconds) => { expiry = callback; assert.equal(milliseconds, 240000); return 1; }
};
vm.runInNewContext(source, context);
function activate(data) {
    const bytes = Buffer.from(data, 'hex');
    handler([{}, {readByteArray: () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)}]);
}
const target = '0104000400041122334400000000000028';
assert.equal(messages[0], 'INK_NFC_MIFARE_COMPAT_READY');
activate(target.replace('11223344', '01020304'));
assert.equal(writes, 1);
assert.equal(messages.at(-1).uid, '01020304');
pending = 1;
activate(target.replace('11223344', '55667788'));
assert.equal(writes, 2);
assert.equal(messages.at(-1).uid, '55667788');
pending = 1;
activate(target.replace('010400', '019000'));
activate(target.slice(0, -2) + '08');
assert.equal(writes, 2);
pending = 2;
activate(target);
assert.equal(writes, 2);
pending = 1;
activate(target);
assert.equal(writes, 3);
assert.equal(pending, 0);
expiry();
assert.equal(detached, true);
pending = 1;
activate(target);
assert.equal(writes, 3);
assert.equal(messages.at(-1), 'INK_NFC_MIFARE_COMPAT_EXPIRED');
console.log('MIFARE compatibility guard tests passed');
