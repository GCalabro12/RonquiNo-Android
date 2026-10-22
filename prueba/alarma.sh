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
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true   # «Mostrar sobre otras apps»: alarma a pantalla completa
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
J "window.RonquiNoAndroid.siesta(1, 'amanecer', 120); 'siesta con subida de 2 min'"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill $PKG
sleep 1
echo "Proceso tras cerrar: '$(adb shell pidof $PKG | tr -d '\r')'"
sleep 70
adb exec-out screencap -p > captura-alarma.png
check "Suena el servicio de alarma con la app cerrada" "$(adb shell dumpsys activity services $PKG | grep -c 'AlarmaService' | awk '{print ($1>0)?1:0}')" 1
check "Sale la pantalla «¡Buenos días!»" "$(adb shell dumpsys activity activities | grep -c 'AlarmaActivity' | awk '{print ($1>0)?1:0}')" 1
# La subida de volumen: el reproductor de la alarma debe empezar casi en silencio e ir subiendo
vol() { adb shell dumpsys audio | grep -i "USAGE_ALARM" | grep -o -i "volume[:=][ ]*[0-9.]*" | head -1 | grep -o "[0-9.]*$"; }
V1=$(vol); sleep 45; V2=$(vol)
echo "Volumen del reproductor: al empezar ${V1:-?} · a los 45 s ${V2:-?}"
if [ -n "$V1" ] && [ -n "$V2" ]; then
  check "El volumen empieza bajo" "$(python3 -c "print(int($V1 < 0.05))")" 1
  check "El volumen va subiendo" "$(python3 -c "print(int($V2 > $V1))")" 1
fi
adb shell dumpsys audio | grep -i -m3 "USAGE_ALARM" || true
# ---------- Prueba para apagar: 3 cuentas ----------
# Se apaga la siesta con su botón (sin prueba), se programa una alarma con «3 cuentas» y se resuelven en pantalla
UI() { python3 prueba/ui.py "$@"; }
toca() { XY=$(UI "$1" "$2"); [ -n "$XY" ] && adb shell input tap $XY; }
toca tap "Apagar"; sleep 3
check "La siesta se apaga con el botón" "$(adb shell dumpsys activity services $PKG | grep -c 'AlarmaService' | awk '{print ($1>0)?1:0}')" 0
adb shell am start -n $PKG/.MainActivity
for i in $(seq 1 20); do
  sleep 3
  PID=$(adb shell pidof $PKG | tr -d '\r')
  [ -n "$PID" ] && adb forward tcp:9222 localabstract:webview_devtools_remote_$PID >/dev/null 2>&1
  OK=$(python3 prueba/cdp.py "window.RonquiNoAndroid ? 'si' : 'no'" 2>/dev/null || true)
  [ "$OK" = "si" ] && break
done
HORA=$(adb shell date -d "@$(( $(adb shell date +%s | tr -d '\r') + 120 ))" +%H:%M | tr -d '\r')
echo "Alarma con prueba a las $HORA"
J "window.RonquiNoAndroid.setAlarmas(JSON.stringify([{id: 456, on: true, hora: '$HORA', tono: 'marimba', sub: 0, reto: 'cuentas', cod: ''}])); 'ok'"
adb shell input keyevent KEYCODE_HOME
for i in $(seq 1 40); do sleep 5; [ "$(adb shell dumpsys activity activities | grep -c 'AlarmaActivity')" != 0 ] && break; done
sleep 3
check "Suena la alarma con prueba" "$(adb shell dumpsys activity activities | grep -c 'AlarmaActivity' | awk '{print ($1>0)?1:0}')" 1
check "Dice qué hay que hacer para apagarla" "$(UI hay 'Para apagarla, haz 3 cuentas')" 1
toca tap "Apagar"; sleep 2
check "«Apagar» abre las cuentas (no apaga)" "$(UI hay 'Haz 3 cuentas')" 1
adb exec-out screencap -p > captura-cuentas.png
# Una respuesta mal: no se apaga
toca exacto "1"; toca exacto "✓"; sleep 1
check "Respuesta mal: sigue sonando" "$(adb shell dumpsys activity services $PKG | grep -c 'AlarmaService' | awk '{print ($1>0)?1:0}')" 1
for n in 1 2 3; do
  R=$(UI cuenta); echo "Cuenta $n: $R"
  for d in $(echo "$R" | grep -o .); do toca exacto "$d"; done
  toca exacto "✓"; sleep 2
done
sleep 2
check "Con las 3 cuentas bien, la alarma se apaga" "$(adb shell dumpsys activity services $PKG | grep -c 'AlarmaService' | awk '{print ($1>0)?1:0}')" 0
echo "Fallos: $FALLOS"
[ "$FALLOS" = 0 ]
