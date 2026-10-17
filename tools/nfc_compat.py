import argparse
import json
import signal
import threading
from pathlib import Path

import frida


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pid', type=int, required=True)
    parser.add_argument('--seconds', type=int, default=600)
    parser.add_argument('--enable', action='store_true')
    args = parser.parse_args()
    stopped = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    device = frida.get_device_manager().add_remote_device('127.0.0.1:27042')
    session = device.attach(args.pid)
    session.on('detached', lambda *details: (print('DETACHED', details, flush=True), stopped.set()))
    source = Path(__file__).with_suffix('.js').read_text()
    script = session.create_script(source)
    script.on('message', lambda message, data: print(json.dumps(message, ensure_ascii=False), flush=True))
    try:
        script.load()
        if args.enable:
            print(json.dumps(script.exports_sync.enable(True)), flush=True)
        print(json.dumps(script.exports_sync.status()), flush=True)
        stopped.wait(args.seconds)
    finally:
        try:
            script.unload()
            session.detach()
        except frida.InvalidOperationError:
            pass


if __name__ == '__main__':
    main()
