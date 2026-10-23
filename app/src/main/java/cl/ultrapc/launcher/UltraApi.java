package cl.ultrapc.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cliente de la API de solo lectura del servidor (mu-plugin ultrapc-app-api.php). */
final class UltraApi {

    static final String DEFAULT_URL = "https://www.ultrapc.cl";
    private static final String PREFS = "ultrapc_api";

    interface Callback {
        void ok(JSONObject data);
        void error(String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UltraApi() {}

    static String url(Context c) {
        return prefs(c).getString("url", DEFAULT_URL);
    }

    static String token(Context c) {
        return prefs(c).getString("token", "");
    }

    static boolean configured(Context c) {
        return !token(c).isEmpty();
    }

    static void save(Context c, String url, String token) {
        prefs(c).edit().putString("url", url).putString("token", token).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void get(Context c, String path, Callback cb) {
        String base = url(c).replaceAll("/+$", "");
        String token = token(c);
        POOL.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(base + "/wp-json/ultrapc-app/v1/" + path).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(30000); // «recalcular» del disco puede tardar
                conn.setRequestProperty("X-UltraPC-Token", token);
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
                String body = in != null ? read(in) : "";
                if (code == 401) {
                    post(cb, null, "Llave inválida o revocada");
                } else if (code == 429) {
                    post(cb, null, "Demasiadas consultas, espera un minuto");
                } else if (code >= 400) {
                    String msg = "Error del servidor (" + code + ")";
                    try { msg = new JSONObject(body).optString("message", msg); } catch (Exception ignored) {}
                    post(cb, null, msg);
                } else {
                    post(cb, new JSONObject(body), null);
                }
            } catch (Exception e) {
                post(cb, null, "Sin conexión con el servidor");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void post(Callback cb, JSONObject data, String error) {
        MAIN.post(() -> {
            if (error != null) cb.error(error);
            else cb.ok(data);
        });
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
