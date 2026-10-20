# Ejecuta JavaScript dentro de la web de la app (WebView de la versión debug) y escribe el resultado.
import json
import sys
import time
import urllib.request

import websocket


def pagina():
    for _ in range(60):
        try:
            for p in json.load(urllib.request.urlopen('http://localhost:9222/json', timeout=5)):
                if p.get('type') == 'page' and 'RonquiNo' in p.get('url', ''):
                    return p['webSocketDebuggerUrl']
        except Exception:
            pass
        time.sleep(2)
    sys.exit('No encuentro la web de RonquiNo dentro de la app')


ws = websocket.create_connection(pagina(), suppress_origin=True, timeout=60)
ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate',
                    'params': {'expression': sys.argv[1], 'awaitPromise': True, 'returnByValue': True}}))
while True:
    r = json.loads(ws.recv())
    if r.get('id') == 1:
        break
res = r.get('result', {})
if 'exceptionDetails' in res:
    sys.exit('Error de JavaScript: ' + json.dumps(res['exceptionDetails'])[:500])
print(json.dumps(res.get('result', {}).get('value'), ensure_ascii=False))
