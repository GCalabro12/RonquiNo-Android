package club.practicaintegral.ronquino;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pantalla completa «¡Buenos días!» cuando suena una alarma (también encima del bloqueo):
 * reloj grande arriba, botones para posponer 1, 3, 5 o 9 minutos o apagar, y la luna abajo
 * saludando y dando saltitos.
 */
public class AlarmaActivity extends Activity {
    private static WeakReference<AlarmaActivity> actual = new WeakReference<>(null);
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView reloj;
    private ImageView luna;
    private boolean aplaude;

    static void cerrar() { AlarmaActivity a = actual.get(); if (a != null) a.finish(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        actual = new WeakReference<>(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(48), dp(24), dp(24));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF7C2D12, 0xFF1E1B4B, 0xFF0B1020}));

        reloj = new TextView(this);
        reloj.setTextColor(0xFFF8FAFC); reloj.setTextSize(84); reloj.setGravity(Gravity.CENTER);
        col.addView(reloj);

        TextView hola = new TextView(this);
        hola.setText("☀️ ¡Buenos días!"); hola.setTextColor(0xFFFDE68A); hola.setTextSize(28); hola.setGravity(Gravity.CENTER);
        hola.setPadding(0, 0, 0, dp(24));
        col.addView(hola);

        TextView pos = new TextView(this);
        pos.setText("😴 Posponer"); pos.setTextColor(0xFFC7D2FE); pos.setTextSize(17); pos.setGravity(Gravity.CENTER);
        col.addView(pos);
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER);
        for (int m : new int[]{1, 3, 5, 9}) fila.addView(boton(m + " min", 0xFF312E81, 0xFFE0E7FF, 17, () -> pulsar("POSPONER", m), true));
        col.addView(fila, new LinearLayout.LayoutParams(-1, -2));

        Button apagar = boton("☀️ Apagar", 0xFFFDE68A, 0xFF1E1B4B, 24, () -> pulsar("APAGAR", 0), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(76));
        lp.setMargins(dp(8), dp(18), dp(8), 0);
        col.addView(apagar, lp);

        // La luna, abajo del todo, ocupando el espacio que sobra
        luna = new ImageView(this);
        luna.setImageResource(R.drawable.luna_saluda);
        luna.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(-1, 0, 1f);
        lpl.topMargin = dp(16);
        col.addView(luna, lpl);

        setContentView(col);
        // Con la pantalla completa a la vista, el aviso de arriba sobra (tapaba el reloj): pasa a ser discreto
        try { startService(new Intent(this, AlarmaService.class).setAction("DISCRETO")); } catch (RuntimeException ignored) {}
        animarLuna();
        tic.run();
    }

    /** Saltitos y balanceo, y cada 1,6 s cambia de saludar a aplaudir. */
    private void animarLuna() {
        luna.setPivotY(dp(220));
        ObjectAnimator salto = ObjectAnimator.ofFloat(luna, "translationY", 0, -dp(18));
        salto.setDuration(450); salto.setRepeatMode(ValueAnimator.REVERSE); salto.setRepeatCount(ValueAnimator.INFINITE);
        salto.setInterpolator(new AccelerateDecelerateInterpolator()); salto.start();
        ObjectAnimator vaiven = ObjectAnimator.ofFloat(luna, "rotation", -7, 7);
        vaiven.setDuration(800); vaiven.setRepeatMode(ValueAnimator.REVERSE); vaiven.setRepeatCount(ValueAnimator.INFINITE);
        vaiven.start();
        h.postDelayed(new Runnable() {
            @Override public void run() {
                aplaude = !aplaude;
                luna.setImageResource(aplaude ? R.drawable.luna_aplaude : R.drawable.luna_saluda);
                h.postDelayed(this, 1600);
            }
        }, 1600);
    }

    private void pulsar(String accion, int min) {
        startService(new Intent(this, AlarmaService.class).setAction(accion).putExtra("min", min));
        finish();
    }

    private Button boton(String txt, int fondo, int color, int sp, Runnable r, boolean enFila) {
        Button bt = new Button(this);
        bt.setText(txt); bt.setTextColor(color); bt.setTextSize(sp); bt.setAllCaps(false);
        bt.setSingleLine(true); bt.setPadding(0, 0, 0, 0); bt.setMinWidth(0); bt.setMinimumWidth(0);
        GradientDrawable g = new GradientDrawable(); g.setColor(fondo); g.setCornerRadius(dp(18));
        bt.setBackground(g);
        if (enFila) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
            lp.setMargins(dp(6), dp(8), dp(6), 0);
            bt.setLayoutParams(lp);
        }
        bt.setOnClickListener(v -> r.run());
        return bt;
    }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            reloj.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            h.postDelayed(this, 1000);
        }
    };

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() { /* se apaga o pospone con sus botones */ }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
