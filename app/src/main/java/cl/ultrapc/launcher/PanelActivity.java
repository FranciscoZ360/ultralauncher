package cl.ultrapc.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Panel con los widgets «Espacio en el servidor» y «Tráfico de red en vivo» del Escritorio de ultrapc.cl. */
public class PanelActivity extends Activity {

    private static final long NET_INTERVAL = 2000;
    private static final long NET_MAX_RUN = 15 * 60 * 1000; // como el widget: se pausa a los 15 minutos

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Double> down = new ArrayList<>();
    private final List<Double> up = new ArrayList<>();

    private TextView notice, diskPct, diskNums, siteTotal, diskTime, diskRefresh;
    private TextView downText, downBytes, upText, upBytes, netStats, netState, netToggle;
    private ProgressBar diskBar;
    private StackBar siteBar;
    private LinearLayout legend;
    private NetChart chart;

    private boolean netRunning;
    private boolean netUserPaused;
    private long netStartedAt;
    private JSONObject lastSample;
    private double peak;
    private long diskTimeValue;

    private final Runnable netTick = new Runnable() {
        @Override
        public void run() {
            if (!netRunning) return;
            if (System.currentTimeMillis() - netStartedAt > NET_MAX_RUN) {
                stopNet("Pausado tras 15 minutos");
                return;
            }
            sampleNet();
            handler.postDelayed(this, NET_INTERVAL);
        }
    };

    private final Runnable diskClock = new Runnable() {
        @Override
        public void run() {
            updateDiskTime();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        notice = findViewById(R.id.notice);
        diskPct = findViewById(R.id.diskPct);
        diskNums = findViewById(R.id.diskNums);
        siteTotal = findViewById(R.id.siteTotal);
        diskTime = findViewById(R.id.diskTime);
        diskRefresh = findViewById(R.id.diskRefresh);
        diskBar = findViewById(R.id.diskBar);
        siteBar = findViewById(R.id.siteBar);
        legend = findViewById(R.id.legend);
        downText = findViewById(R.id.down);
        downBytes = findViewById(R.id.downBytes);
        upText = findViewById(R.id.up);
        upBytes = findViewById(R.id.upBytes);
        chart = findViewById(R.id.chart);
        netStats = findViewById(R.id.netStats);
        netState = findViewById(R.id.netState);
        netToggle = findViewById(R.id.netToggle);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.settings).setOnClickListener(v -> showSettings());
        diskRefresh.setOnClickListener(v -> loadDisk(true));
        netToggle.setOnClickListener(v -> {
            if (netRunning) {
                netUserPaused = true;
                stopNet("Pausado");
            } else {
                netUserPaused = false;
                startNet();
            }
        });

        if (!UltraApi.configured(this)) showSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!UltraApi.configured(this)) {
            showNotice("Toca ⚙ y pega la llave de la app. Se crea en wp-admin → Herramientas → App UltraPC.");
            return;
        }
        loadDisk(false);
        if (!netUserPaused) startNet();
        handler.removeCallbacks(diskClock);
        handler.post(diskClock);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Fuera de la pantalla no se consulta nada, para no gastar batería ni datos
        stopNet("Pausado");
        handler.removeCallbacks(diskClock);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }

    /* ---------- Configuración ---------- */

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText url = new EditText(this);
        url.setHint("Dirección del sitio");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(UltraApi.url(this));
        EditText token = new EditText(this);
        token.setHint("Llave (upc_…)");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(UltraApi.token(this));
        TextView help = new TextView(this);
        help.setText("La llave se crea en wp-admin → Herramientas → App UltraPC. Solo permite ver estos datos y se puede revocar.");
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        help.setPadding(0, dp(10), 0, 0);
        box.addView(url);
        box.addView(token);
        box.addView(help);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Conectar con UltraPC")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (!u.startsWith("https://")) u = UltraApi.DEFAULT_URL;
                    UltraApi.save(this, u, token.getText().toString().trim());
                    resetNet();
                    onResume();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
    }

    private void hideNotice() {
        notice.setVisibility(View.GONE);
    }

    /* ---------- Espacio en el servidor ---------- */

    private void loadDisk(boolean recalc) {
        diskRefresh.setEnabled(false);
        diskRefresh.setText(recalc ? "Calculando…" : "Cargando…");
        UltraApi.get(this, recalc ? "disco?recalcular=1" : "disco", new UltraApi.Callback() {
            @Override
            public void ok(JSONObject d) {
                hideNotice();
                diskRefresh.setEnabled(true);
                diskRefresh.setText("Recalcular");
                renderDisk(d);
            }

            @Override
            public void error(String message) {
                diskRefresh.setEnabled(true);
                diskRefresh.setText("Reintentar");
                showNotice(message);
            }
        });
    }

    private void renderDisk(JSONObject d) {
        double total = d.optDouble("total", 0), free = d.optDouble("free", 0);
        double used = Math.max(0, total - free);
        int pct = total > 0 ? (int) Math.round(used / total * 100) : 0;
        int fg, bg;
        if (pct >= 90) { fg = 0xFFFF8A80; bg = 0x40D63638; }
        else if (pct >= 75) { fg = 0xFFFFC266; bg = 0x40F5A623; }
        else { fg = 0xFF6EE7A0; bg = 0x40127A45; }
        diskPct.setText(pct + "% usado");
        diskPct.setTextColor(fg);
        diskPct.setBackground(pill(bg));
        diskBar.setProgress(pct);
        diskBar.setProgressTintList(pct >= 75 ? ColorStateList.valueOf(pct >= 90 ? 0xFFD63638 : 0xFFF5A623) : null);
        diskNums.setText(fmtSize(used) + " usados   ·   " + fmtSize(free) + " libres   ·   de " + fmtSize(total));

        JSONArray parts = d.optJSONArray("parts");
        legend.removeAllViews();
        if (parts != null) {
            float[] values = new float[parts.length()];
            int[] colors = new int[parts.length()];
            double sum = 0;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.optJSONObject(i);
                values[i] = (float) p.optDouble("bytes", 0);
                colors[i] = partColor(p.optString("key"));
                sum += values[i];
                legend.addView(legendRow(colors[i], p.optString("label"), fmtSize(values[i])));
            }
            siteBar.setData(values, colors);
            siteTotal.setText(fmtSize(sum) + " en total");
        }
        diskTimeValue = d.optLong("time", 0) * 1000;
        updateDiskTime();
    }

    private void updateDiskTime() {
        if (diskTimeValue == 0) return;
        diskTime.setText("Actualizado " + DateUtils.getRelativeTimeSpanString(diskTimeValue,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
    }

    private View legendRow(int color, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(3));
        dot.setBackground(g);
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(getColor(R.color.text_dim));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setPadding(dp(10), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.text));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(v);
        return row;
    }

    // Mismos colores que el widget del Escritorio
    private static int partColor(String key) {
        switch (key) {
            case "uploads": return 0xFF087CFF;
            case "plugins": return 0xFF7C4DFF;
            case "themes": return 0xFF00A3A3;
            case "cache": return 0xFFF5A623;
            case "db": return 0xFFE8590C;
            default: return 0xFF9AA7BD;
        }
    }

    /* ---------- Tráfico de red en vivo ---------- */

    private void startNet() {
        if (netRunning) return;
        netRunning = true;
        netStartedAt = System.currentTimeMillis();
        lastSample = null; // la primera muestra solo sirve de referencia
        netToggle.setText("Pausar");
        netState.setText("Conectando…");
        handler.post(netTick);
    }

    private void stopNet(String msg) {
        netRunning = false;
        handler.removeCallbacks(netTick);
        netToggle.setText("Reanudar");
        netState.setText(msg);
    }

    private void resetNet() {
        down.clear();
        up.clear();
        peak = 0;
        lastSample = null;
    }

    private void sampleNet() {
        UltraApi.get(this, "red", new UltraApi.Callback() {
            @Override
            public void ok(JSONObject c) {
                if (!netRunning) return;
                hideNotice();
                JSONObject prev = lastSample;
                lastSample = c;
                JSONArray load = c.optJSONArray("load");
                String cpu = load == null ? "—" : String.format(Locale.US, "%.2f · %.2f · %.2f",
                        load.optDouble(0), load.optDouble(1), load.optDouble(2));
                netState.setText("En vivo · interfaz " + c.optString("if", "?") + " · cada 2 s");
                if (prev == null) return;
                double dt = c.optDouble("t") - prev.optDouble("t");
                if (dt <= 0) return;
                double rx = Math.max(0, (c.optDouble("rx") - prev.optDouble("rx")) / dt) * 8;
                double tx = Math.max(0, (c.optDouble("tx") - prev.optDouble("tx")) / dt) * 8;
                push(down, rx);
                push(up, tx);
                peak = Math.max(peak, rx);
                downText.setText(fmtBits(rx));
                downBytes.setText(fmtBytes(rx / 8));
                upText.setText(fmtBits(tx));
                upBytes.setText(fmtBytes(tx / 8));
                netStats.setText("Pico " + fmtBits(peak) + "    Promedio ▼ " + fmtBits(avg(down)) + " ▲ " + fmtBits(avg(up))
                        + "\nCarga CPU " + cpu);
                chart.setData(down, up);
            }

            @Override
            public void error(String message) {
                stopNet(message);
                showNotice(message);
            }
        });
    }

    private static void push(List<Double> list, double v) {
        list.add(v);
        if (list.size() > NetChart.MAX_POINTS) list.remove(0);
    }

    private static double avg(List<Double> list) {
        if (list.isEmpty()) return 0;
        double s = 0;
        for (double v : list) s += v;
        return s / list.size();
    }

    /* ---------- Formatos (como el widget) ---------- */

    static String fmtBits(double bps) {
        if (bps >= 1e9) return String.format(Locale.US, "%.2f Gb/s", bps / 1e9);
        if (bps >= 1e6) return String.format(Locale.US, "%.2f Mb/s", bps / 1e6);
        double k = bps / 1e3;
        return String.format(Locale.US, k >= 100 ? "%.0f Kb/s" : "%.1f Kb/s", k);
    }

    static String fmtBytes(double bytesPerSec) {
        if (bytesPerSec >= 1e6) return String.format(Locale.US, "%.2f MB/s", bytesPerSec / 1e6);
        return String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1e3);
    }

    // size_format() de WordPress usa base 1024
    static String fmtSize(double b) {
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (b >= 1024 && i < u.length - 1) {
            b /= 1024;
            i++;
        }
        return String.format(Locale.US, i == 0 ? "%.0f %s" : "%.1f %s", b, u[i]);
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(999));
        return g;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
