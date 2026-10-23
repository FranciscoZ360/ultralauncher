package cl.ultrapc.launcher;

import android.content.pm.ApplicationInfo;

/** Clasifica las apps en categorías según el nombre del paquete y la categoría que declara la app. */
final class Categories {

    static final String ALL = "all";

    // Orden en que aparecen en la fila de categorías
    static final String[] IDS = {"ia", "social", "compras", "trabajo", "multimedia", "juegos", "viajes", "herramientas", "otras"};
    static final String[] LABELS = {"✨ IA", "💬 Social", "🛒 Compras", "💼 Trabajo", "🎬 Multimedia", "🎮 Juegos", "🧭 Viajes", "🛠 Herramientas", "📦 Otras"};

    // Se revisan en este orden; gana la primera coincidencia
    private static final String[][] RULES = {
            {"ia", "openai", "chaton", "gemini", "anthropic", "copilot", "perplexity", "deepseek", "characterai", "grok", "meta.ai",
                    "bard", "chat.gpt", "chatgpt", "chatbot", "aichat"},
            {"compras", "mercadolibre", "mercadopago", "walletnfcrel", "amazon", "aliexpress", "falabella", "ripley",
                    "lider", "bancochile", "santander", "bci", "bancoestado", "scotiabank", "itau", "paypal", "temu",
                    "shein", "rappi", "pedidosya", "ubereats", "tenpo", "mach", "fpay", "sodimac", "shopify", "ebay"},
            {"social", "whatsapp", "telegram", "thoughtcrime", "messenger", "facebook", "instagram", "twitter",
                    "android.gm", "contacts", "dialer", "messaging", "mms", "discord", "skype", "teams", "zoom",
                    "linkedin", "outlook", "snapchat", "pinterest", "reddit", "threads", "meet", "duo"},
            {"viajes", "maps", "waze", "uber", "cabify", "didi", "moovit", "redbus", "latam", "skyscanner",
                    "booking", "airbnb", "transit"},
            {"trabajo", "apps.docs", "docs.editors", "apps.drive", "calendar", "camscanner", "office", "keep",
                    "notion", "trello", "slack", "authenticator", "apps.tasks", "dropbox", "evernote", "adobe",
                    "github", "onedrive", "classroom", "woocommerce", "wordpress", "anydesk", "teamviewer"},
            {"multimedia", "photos", "camera", "youtube", "spotify", "netflix", "gallery", "music", "video",
                    "disney", "primevideo", "hbo", "tiktok", "twitch", "soundcloud", "deezer", "capcut", "wallpaper",
                    "podcast", "radio", "snapseed", "lightroom", "picsart", "canva"},
            {"herramientas", "settings", "calculator", "deskclock", "clock", "weather", "files", "documentsui",
                    "assistant", "googlequicksearchbox", "chrome", "browser", "firefox", "brave", "opera", "help",
                    "fitness", "health", "vending", "motorola", "flashlight", "recorder", "vpn", "security",
                    "antivirus", "cleaner", "translate", "lens", "bluetooth"},
    };

    private Categories() {}

    static String classify(ApplicationInfo info) {
        String pkg = info.packageName.toLowerCase();
        for (String[] rule : RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (pkg.contains(rule[i])) return rule[0];
            }
        }
        switch (info.category) {
            case ApplicationInfo.CATEGORY_GAME: return "juegos";
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO:
            case ApplicationInfo.CATEGORY_IMAGE: return "multimedia";
            case ApplicationInfo.CATEGORY_SOCIAL: return "social";
            case ApplicationInfo.CATEGORY_MAPS: return "viajes";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "trabajo";
            case ApplicationInfo.CATEGORY_ACCESSIBILITY: return "herramientas";
        }
        return "otras";
    }

    static String label(String id) {
        if (ALL.equals(id)) return "Todas";
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return id;
    }

    /** Nombre sin el emoji, para el título del cajón */
    static String title(String id) {
        if (ALL.equals(id)) return "Aplicaciones";
        String l = label(id);
        int space = l.indexOf(' ');
        return space >= 0 ? l.substring(space + 1) : l;
    }
}
