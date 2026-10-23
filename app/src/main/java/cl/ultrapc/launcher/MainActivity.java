package cl.ultrapc.launcher;

import android.animation.AnimatorInflater;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final int DOCK_MAX = 5;
    private static final String PREFS = "launcher";
    private static final String KEY_DOCK = "dock";

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private final List<String> dock = new ArrayList<>();
    private AppAdapter adapter;
    private EditText search;
    private GridView grid;
    private TextView batteryText;
    private TextView greeting;
    private TextView count;
    private ProgressBar batteryBar;
    private LinearLayout dockView;
    private View dockHint;
    private View clear;
    private GestureDetector gestures;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int pct = scale > 0 ? Math.round(level * 100f / scale) : 0;
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            int color = getColor(charging ? R.color.bat_charge
                    : pct <= 15 ? R.color.bat_low
                    : pct <= 40 ? R.color.bat_mid
                    : R.color.accent);
            batteryText.setText((charging ? "⚡ " : "") + pct + "%");
            batteryText.setTextColor(color);
            batteryBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            batteryBar.setProgress(pct);
        }
    };

    // Recarga la lista cuando se instala o desinstala una app
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Sin franja gris detrás de la barra de navegación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }

        search = findViewById(R.id.search);
        grid = findViewById(R.id.grid);
        batteryText = findViewById(R.id.batteryText);
        batteryBar = findViewById(R.id.batteryBar);
        greeting = findViewById(R.id.greeting);
        count = findViewById(R.id.count);
        dockView = findViewById(R.id.dock);
        dockHint = findViewById(R.id.dockHint);
        clear = findViewById(R.id.clear);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setEmptyView(findViewById(R.id.empty));
        grid.setOnItemClickListener((parent, view, position, id) -> launch(shownApps.get(position), view));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppMenu(shownApps.get(position), view);
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                filter(s.toString());
            }
        });
        // Al pulsar "Ir" en el teclado abre el primer resultado
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO && !shownApps.isEmpty()) {
                launch(shownApps.get(0), null);
                return true;
            }
            return false;
        });
        clear.setOnClickListener(v -> search.setText(""));

        // Reloj → alarmas, fecha → calendario, batería → uso de batería
        findViewById(R.id.clock).setOnClickListener(v ->
                tryStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        findViewById(R.id.date).setOnClickListener(v -> tryStart(new Intent(Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                        .appendPath(Long.toString(System.currentTimeMillis())).build())));
        findViewById(R.id.battery).setOnClickListener(v ->
                tryStart(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));

        // Mantener presionado el encabezado abre el selector de fondo de pantalla
        findViewById(R.id.header).setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SET_WALLPAPER), "Elegir fondo"));
            return true;
        });

        // Deslizar hacia abajo (con el cajón arriba del todo) abre las notificaciones
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (dy > 150 && Math.abs(vy) > Math.abs(vx) && !grid.canScrollVertically(-1)) {
                    expandNotifications();
                    return true;
                }
                return false;
            }
        });

        applyClockGradient(findViewById(R.id.clock));

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter);

        loadDock();
        loadApps();
        animateIn();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateGreeting();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
    }

    // Botón Inicio estando ya en el launcher: limpia la búsqueda y vuelve arriba
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resetSearch();
        grid.smoothScrollToPosition(0);
    }

    @Override
    public void onBackPressed() {
        resetSearch();
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? "Buenos días" : h >= 12 && h < 20 ? "Buenas tardes" : "Buenas noches");
    }

    private void applyClockGradient(TextClock clock) {
        clock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            clock.getPaint().setShader(new LinearGradient(0, 0, r - l, 0,
                    getColor(R.color.accent), getColor(R.color.accent2), Shader.TileMode.CLAMP));
            clock.invalidate();
        });
    }

    private void animateIn() {
        int[] ids = {R.id.header, R.id.searchBox, R.id.drawer, R.id.dockBar};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            v.setAlpha(0f);
            v.setTranslationY(dp(24));
            v.animate().alpha(1f).translationY(0).setStartDelay(i * 70L).setDuration(380).start();
        }
    }

    private void resetSearch() {
        search.setText("");
        search.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void loadApps() {
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        allApps.clear();
        for (UserHandle user : launcherApps.getProfiles()) {
            for (LauncherActivityInfo info : launcherApps.getActivityList(null, user)) {
                if (info.getComponentName().getPackageName().equals(getPackageName())) continue;
                allApps.add(new AppEntry(info));
            }
        }
        Collections.sort(allApps, (a, b) -> a.key.compareTo(b.key));
        count.setText(String.valueOf(allApps.size()));
        filter(search.getText().toString());
        renderDock();
    }

    private void filter(String query) {
        String q = normalize(query);
        shownApps.clear();
        // Primero las que empiezan con lo buscado, después las que lo contienen
        List<AppEntry> contains = new ArrayList<>();
        for (AppEntry app : allApps) {
            if (q.isEmpty() || app.key.startsWith(q)) shownApps.add(app);
            else if (app.key.contains(q)) contains.add(app);
        }
        shownApps.addAll(contains);
        adapter.notifyDataSetChanged();
    }

    private void launch(AppEntry app, View from) {
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        android.graphics.Rect bounds = null;
        Bundle opts = null;
        if (from != null) {
            int[] pos = new int[2];
            from.getLocationOnScreen(pos);
            bounds = new android.graphics.Rect(pos[0], pos[1], pos[0] + from.getWidth(), pos[1] + from.getHeight());
            opts = android.app.ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        }
        try {
            launcherApps.startMainActivity(app.info.getComponentName(), app.info.getUser(), bounds, opts);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir " + app.label, Toast.LENGTH_SHORT).show();
        }
        resetSearch();
    }

    private void showAppMenu(AppEntry app, View anchor) {
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(this, R.style.PopupTheme), anchor);
        boolean pinned = dock.contains(app.id);
        menu.getMenu().add(Menu.NONE, 1, 1, pinned ? "Quitar del dock" : "Fijar en el dock");
        menu.getMenu().add(Menu.NONE, 2, 2, "Información de la app");
        menu.getMenu().add(Menu.NONE, 3, 3, "Desinstalar");
        menu.setOnMenuItemClickListener(item -> {
            String pkg = app.info.getComponentName().getPackageName();
            switch (item.getItemId()) {
                case 1:
                    if (pinned) dock.remove(app.id);
                    else if (dock.size() >= DOCK_MAX) {
                        Toast.makeText(this, "El dock tiene máximo " + DOCK_MAX + " apps", Toast.LENGTH_SHORT).show();
                        return true;
                    } else dock.add(app.id);
                    saveDock();
                    renderDock();
                    return true;
                case 2:
                    tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                    return true;
                case 3:
                    tryStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                    return true;
            }
            return false;
        });
        menu.show();
    }

    private void loadDock() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_DOCK, "");
        dock.clear();
        if (!saved.isEmpty()) dock.addAll(Arrays.asList(saved.split(";")));
    }

    private void saveDock() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_DOCK, TextUtils.join(";", dock)).apply();
    }

    private void renderDock() {
        dockView.removeAllViews();
        for (String id : dock) {
            AppEntry app = findApp(id);
            if (app == null) continue; // desinstalada
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            icon.setLayoutParams(lp);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(app.icon());
            icon.setContentDescription(app.label);
            icon.setOnClickListener(v -> launch(app, v));
            icon.setOnLongClickListener(v -> {
                showAppMenu(app, v);
                return true;
            });
            icon.setStateListAnimator(AnimatorInflater.loadStateListAnimator(this, R.animator.press_scale));
            dockView.addView(icon);
        }
        dockHint.setVisibility(dockView.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private AppEntry findApp(String id) {
        for (AppEntry app : allApps) if (app.id.equals(id)) return app;
        return null;
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
            // Algunos fabricantes no lo permiten
        }
    }

    private void tryStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No disponible en este teléfono", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    private static class AppEntry {
        final LauncherActivityInfo info;
        final String id;
        final String label;
        final String key;
        private Drawable icon;

        AppEntry(LauncherActivityInfo info) {
            this.info = info;
            this.id = info.getComponentName().flattenToString();
            this.label = info.getLabel().toString();
            this.key = normalize(label);
        }

        Drawable icon() {
            if (icon == null) icon = info.getBadgedIcon(0);
            return icon;
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int position) { return shownApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_app, parent, false);
            AppEntry app = shownApps.get(position);
            ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(app.icon());
            ((TextView) view.findViewById(R.id.label)).setText(app.label);
            return view;
        }
    }
}
