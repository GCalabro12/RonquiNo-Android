#!/bin/bash
# Prueba de alarmas: programa una siesta de 1 minuto desde la web, CIERRA la app y comprueba que,
# al llegar la hora, Android la hace sonar solo (servicio de alarma + pantalla «¡Buenos días!»).
set -uo pipefail
PKG=club.practicaintegral.ronquino
J() { python3 prueba/cdp.py "$1"; }
check() { if [ "$2" = "$3" ]; then echo "✅ $1"; else echo "❌ $1 (sale «$2», esperaba «$3»)"; FALLOS=$((FALLOS+1)); fi; }
FALLOS=0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell am start -n $PKG/.MainActivity
# Espera a que la web cargue dentro de la app (en el emulador la primera carga tarda)
for i in $(seq 1 30); do
  sleep 4
  PID=$(adb shell pidof $PKG | tr -d '')
  [ -n "$PID" ] && adb forward tcp:9222 localabstract:webview_devtools_remote_$PID >/dev/null 2>&1
  OK=$(python3 prueba/cdp.py "document.getElementById('ver') ? 'si' : 'no'" 2>/dev/null || true)
  echo "intento $i: $OK"
  [ "$OK" = "si" ] && break
done
J "new Promise(r => { const t = setInterval(() => { if (document.getElementById('ver')?.textContent) { clearInterval(t); r(document.getElementById('ver').textContent); } }, 500); })"
check "La web ve las alarmas nativas" "$(J '!!(window.RonquiNoAndroid && window.RonquiNoAndroid.setAlarmas)')" true
# Una alarma diaria a las 07:30 queda programada en Android
J "window.RonquiNoAndroid.setAlarmas(JSON.stringify([{id: 123, on: true, hora: '07:30', tono: 'marimba', sub: 30}])); 'ok'"
sleep 1
check "Alarma diaria programada en el reloj de Android" "$(adb shell dumpsys alarm | grep -c "$PKG" | awk '{print ($1>0)?1:0}')" 1
# Siesta de 1 minuto y se cierra la app
J "window.RonquiNoAndroid.siesta(1, 'amanecer', 0); 'siesta'"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill $PKG
sleep 1
echo "Proceso tras cerrar: '$(adb shell pidof $PKG | tr -d '\r')'"
sleep 70
adb exec-out screencap -p > captura-alarma.png
check "Suena el servicio de alarma con la app cerrada" "$(adb shell dumpsys activity services $PKG | grep -c 'AlarmaService' | awk '{print ($1>0)?1:0}')" 1
check "Sale la pantalla «¡Buenos días!»" "$(adb shell dumpsys activity activities | grep -c 'AlarmaActivity' | awk '{print ($1>0)?1:0}')" 1
adb shell dumpsys audio | grep -i -m3 "USAGE_ALARM" || true
echo "Fallos: $FALLOS"
[ "$FALLOS" = 0 ]
