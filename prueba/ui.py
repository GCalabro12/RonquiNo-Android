# Busca en la pantalla (volcado de uiautomator) un botón o texto y dice dónde está.
#   python3 prueba/ui.py tap "Apagar"     -> «x y» del centro del primer elemento que contiene ese texto
#   python3 prueba/ui.py exacto "4"       -> igual, pero el texto tiene que ser exactamente ese
#   python3 prueba/ui.py cuenta           -> la cuenta que pide la pantalla («23 + 48 =») ya resuelta
#   python3 prueba/ui.py hay "Haz 3"      -> 1 si sale ese texto, 0 si no
import re, subprocess, sys
xml = subprocess.run(['adb', 'exec-out', 'uiautomator', 'dump', '/dev/tty'], capture_output=True, text=True).stdout
nodos = re.findall(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
modo, arg = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else '')
if modo == 'hay':
    print(int(any(arg in t for t, *_ in nodos)))
elif modo == 'cuenta':
    for t, *_ in nodos:
        m = re.match(r'^(\d+) ([+−×]) (\d+) =$', t)
        if m:
            a, op, b = int(m[1]), m[2], int(m[3])
            print(a + b if op == '+' else a - b if op == '−' else a * b)
            break
else:
    for t, x1, y1, x2, y2 in nodos:
        if (t == arg) if modo == 'exacto' else (arg in t):
            print((int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
            break
