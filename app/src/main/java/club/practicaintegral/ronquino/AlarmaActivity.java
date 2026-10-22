package club.practicaintegral.ronquino;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pantalla «¡Buenos días!» que sale encima del bloqueo cuando suena una alarma. */
public class AlarmaActivity extends Activity {
    private static WeakReference<AlarmaActivity> actual = new WeakReference<>(null);
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView reloj;

    static void cerrar() { AlarmaActivity a = actual.get(); if (a != null) a.finish(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        actual = new WeakReference<>(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(48, 48, 48, 48);
        GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF7C2D12, 0xFF1E1B4B, 0xFF0B1020});
        col.setBackground(fondo);

        ImageView luna = new ImageView(this);
        luna.setImageResource(R.drawable.luna_saluda);
        col.addView(luna, new LinearLayout.LayoutParams(dp(170), dp(170)));

        reloj = new TextView(this);
        reloj.setTextColor(0xFFE2E8F0); reloj.setTextSize(64); reloj.setGravity(Gravity.CENTER);
        col.addView(reloj);

        TextView hola = new TextView(this);
        hola.setText("☀️ ¡Buenos días!"); hola.setTextColor(0xFFFDE68A); hola.setTextSize(24); hola.setGravity(Gravity.CENTER);
        hola.setPadding(0, 0, 0, dp(28));
        col.addView(hola);

        col.addView(boton("😴 Posponer 9 min", 0xFF312E81, 0xFFE0E7FF, "POSPONER"));
        col.addView(boton("☀️ Apagar", 0xFFFDE68A, 0xFF1E1B4B, "APAGAR"));
        setContentView(col);
        tic.run();
    }

    private Button boton(String txt, int fondo, int color, String accion) {
        Button bt = new Button(this);
        bt.setText(txt); bt.setTextColor(color); bt.setTextSize(19); bt.setAllCaps(false);
        GradientDrawable g = new GradientDrawable(); g.setColor(fondo); g.setCornerRadius(dp(16));
        bt.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(270), dp(60));
        lp.setMargins(0, dp(8), 0, dp(8));
        bt.setLayoutParams(lp);
        bt.setOnClickListener(v -> {
            startService(new Intent(this, AlarmaService.class).setAction(accion));
            finish();
        });
        return bt;
    }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            reloj.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            h.postDelayed(this, 1000);
        }
    };

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() { /* se apaga con sus botones */ }
    @Override protected void onDestroy() { h.removeCallbacks(tic); super.onDestroy(); }
}
