const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../tools/nfc_compat.js'), 'utf8');
let handler;
let pending = 1;
let writes = 0;
const context = {
    Process: {getModuleByName: () => ({path: 'fixture', enumerateExports: () => ['getNumDiscNtfEv', 'setNumDiscNtfEi', 'createNativeNfcTag'].map(name => ({name, address: name}))})},
    NativeFunction: function (address) {
        return address === 'getNumDiscNtfEv' ? () => pending : (_, value) => { pending = value; writes++; };
    },
    Interceptor: {attach: (_, callbacks) => { handler = callbacks.onEnter; }},
    rpc: {exports: {}},
    send: () => {}
};
vm.runInNewContext(source, context);
function activate(data) {
    handler([{}, {readByteArray: () => Uint8Array.from(Buffer.from(data, 'hex')).buffer}]);
}
const target = '0104000400041122334400000000000028';
activate(target);
assert.equal(writes, 0);
context.rpc.exports.enable(true);
activate(target.replace('11223344', '01020304'));
assert.equal(writes, 1);
activate(target.replace('010400', '019000'));
assert.equal(writes, 1);
pending = 2;
activate(target);
assert.equal(writes, 1);
pending = 1;
activate(target);
assert.equal(writes, 2);
assert.equal(pending, 0);
context.rpc.exports.enable(false);
pending = 1;
activate(target);
assert.equal(writes, 2);
console.log('NFC compatibility guard tests passed');
