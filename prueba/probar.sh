#!/bin/bash
# Prueba en un emulador: abre la app, pulsa «Activar No molestar» en la hoja «Antes de dormir»,
# empieza la noche y la termina, y comprueba en Android que «No molestar» se pone y se quita de verdad.
set -euo pipefail
PKG=club.practicaintegral.ronquino
J() { python3 prueba/cdp.py "$1"; }
zen() { adb shell settings get global zen_mode | tr -d '\r'; }   # 0 = apagado, 1 = No molestar (prioridad)
check() { if [ "$2" = "$3" ]; then echo "✅ $1"; else echo "❌ $1 (sale «$2», esperaba «$3»)"; FALLOS=$((FALLOS+1)); fi; }
FALLOS=0

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell settings put global zen_mode 0
check "Antes: No molestar apagado" "$(zen)" 0

adb shell am start -n $PKG/.MainActivity
sleep 8
PID=$(adb shell pidof $PKG | tr -d '\r')
adb forward tcp:9222 localabstract:webview_devtools_remote_$PID

# Espera a que cargue la web v1.6
J "new Promise(r => { const t = setInterval(() => { if (document.getElementById('ver')?.textContent) { clearInterval(t); r(document.getElementById('ver').textContent); } }, 500); })"
# 5. La musiquita suena sola: la bienvenida solo se cierra sola si el sonido pudo empezar sin tocar
SPLASH=$(J "new Promise(r => setTimeout(() => r(!document.getElementById('splash')), 6000))")
echo "Bienvenida cerrada sola (musiquita sin tocar): $SPLASH"
check "La musiquita suena sola al abrir (la bienvenida se cierra sin tocar)" "$SPLASH" true

# 3. El puente existe y la hoja muestra el botón
check "La web ve el puente de Android" "$(J '!!window.RonquiNoAndroid')" true
J "window.alert = m => { window.__alertas = (window.__alertas || []).concat(m); }; 'avisos capturados'"
J "document.getElementById('splash')?.click(); document.getElementById('goSnore').click(); document.getElementById('startBtn').click(); document.getElementById('dndBtn').textContent"

# 1a. Primera vez, sin permiso: el botón abre los ajustes «Acceso a No molestar»
J "document.getElementById('dndBtn').click(); 'pulsado'"
sleep 3
adb shell dumpsys activity activities | grep -m1 -i "ZenAccess\|NOTIFICATION_POLICY_ACCESS\|mResumedActivity" || true
adb exec-out screencap -p > captura-permiso.png
# La persona da el permiso (aquí, por comando) y vuelve a la app
adb shell cmd notification allow_dnd $PKG
adb shell am start -n $PKG/.MainActivity
sleep 3
echo "Después de volver con permiso: zen_mode=$(zen)"
check "Al volver con el permiso, No molestar se activa solo" "$(zen)" 1
J "document.getElementById('dndBtn').textContent"
adb exec-out screencap -p > captura-hoja.png

# 4. Empieza la noche: micrófono, pantalla encendida y servicio en primer plano
ESCUCHA=$(J "document.getElementById('prepGo').click(); new Promise(r => setTimeout(() => r(document.getElementById('listen').classList.contains('on')), 5000))")
J "window.__alertas || []"
check "Empieza a escuchar con el micrófono" "$ESCUCHA" true
check "Servicio de noche en primer plano" "$(adb shell dumpsys activity services $PKG | grep -c 'isForeground=true' || true)" 1
adb exec-out screencap -p > captura-escuchando.png
check "Durante la noche sigue en No molestar" "$(zen)" 1

# Termina la noche: se restaura
J "window.confirm = () => true; document.getElementById('stopBtn').click(); new Promise(r => setTimeout(() => r(document.getElementById('nTitle').textContent), 3000))"
sleep 2
echo "Después de terminar: zen_mode=$(zen)"
check "Al terminar la noche, No molestar vuelve a como estaba" "$(zen)" 0

# Ruido para dormir: el servicio se pone en marcha
J "document.getElementById('backBtn').click(); document.getElementById('homeBack').click(); document.getElementById('goMask').click(); document.getElementById('playBtn').click(); new Promise(r => setTimeout(() => r(document.getElementById('playBtn').textContent), 3000))"
check "El ruido arranca el servicio" "$(adb shell dumpsys activity services $PKG | grep -c 'isForeground=true' || true)" 1
J "document.getElementById('playBtn').click(); 'parado'"
echo "Fallos: $FALLOS"
[ "$FALLOS" = 0 ]
