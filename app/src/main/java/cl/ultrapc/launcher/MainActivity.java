package cl.ultrapc.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private EditText search;
    private TextView batteryText;
    private ProgressBar batteryBar;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int pct = scale > 0 ? Math.round(level * 100f / scale) : 0;
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            batteryText.setText((charging ? "⚡ " : "▮ ") + pct + "%");
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

        search = findViewById(R.id.search);
        batteryText = findViewById(R.id.batteryText);
        batteryBar = findViewById(R.id.batteryBar);
        GridView grid = findViewById(R.id.grid);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(shownApps.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            openAppInfo(shownApps.get(position));
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });
        // Al pulsar "Ir" en el teclado abre el primer resultado
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO && !shownApps.isEmpty()) {
                launch(shownApps.get(0));
                return true;
            }
            return false;
        });

        // Mantener presionado el reloj abre el selector de fondo de pantalla
        findViewById(R.id.header).setOnLongClickListener(v -> {
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SET_WALLPAPER), "Elegir fondo"));
            return true;
        });

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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

    // Botón Inicio estando ya en el launcher: limpia la búsqueda
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resetSearch();
    }

    @Override
    public void onBackPressed() {
        resetSearch();
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
        filter(search.getText().toString());
    }

    private void filter(String query) {
        String q = normalize(query);
        shownApps.clear();
        for (AppEntry app : allApps) {
            if (q.isEmpty() || app.key.contains(q)) shownApps.add(app);
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(AppEntry app) {
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        launcherApps.startMainActivity(app.info.getComponentName(), app.info.getUser(), null, null);
        resetSearch();
    }

    private void openAppInfo(AppEntry app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.info.getComponentName().getPackageName()));
        startActivity(intent);
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    private static class AppEntry {
        final LauncherActivityInfo info;
        final String label;
        final String key;
        Drawable icon;

        AppEntry(LauncherActivityInfo info) {
            this.info = info;
            this.label = info.getLabel().toString();
            this.key = normalize(label);
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
            if (app.icon == null) app.icon = app.info.getBadgedIcon(0);
            ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(app.icon);
            ((TextView) view.findViewById(R.id.label)).setText(app.label);
            return view;
        }
    }
}
