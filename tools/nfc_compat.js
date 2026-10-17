'use strict';

const library = Process.getModuleByName('libstnfc_nci_jni.so');
const exported = library.enumerateExports();
function symbol(part) {
    const matches = exported.filter(entry => entry.name.includes(part));
    if (matches.length !== 1) throw new Error('Ambiguous native symbol: ' + part);
    return matches[0].address;
}
function hex(address, length) {
    return Array.from(new Uint8Array(address.readByteArray(length)), value => value.toString(16).padStart(2, '0')).join('').toUpperCase();
}
let enabled = false;
let matches = 0;
let inspected = 0;
const getPending = new NativeFunction(symbol('getNumDiscNtfEv'), 'int', ['pointer']);
const setPending = new NativeFunction(symbol('setNumDiscNtfEi'), 'void', ['pointer', 'int']);
Interceptor.attach(symbol('createNativeNfcTag'), {
    onEnter(args) {
        const data = hex(args[1], 17);
        const pending = getPending(args[0]);
        if (inspected++ < 3) send({event: 'ACTIVATION_INSPECT', enabled, pending, data});
        if (!enabled) return;
        if (!data.startsWith('010400040004') || !data.endsWith('00000000000028') || pending !== 1) return;
        setPending(args[0], 0);
        matches++;
        send({event: 'COMPAT_APPLIED', uid: data.substring(12, 20), pendingBefore: pending, pendingAfter: getPending(args[0]), matches});
    }
});
rpc.exports = {
    status() { return {enabled, matches, library: library.path}; },
    enable(value) { enabled = value === true; return {enabled}; }
};
send({event: 'HOOK_READY', mode: 'same-profile-all-uids', enabled, library: library.path});
