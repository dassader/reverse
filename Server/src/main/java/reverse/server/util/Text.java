package reverse.server.util;

import java.util.Locale;

public final class Text {
    private Text() {
    }

    public static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    public static String age(long now, long then) {
        long ms = msAgo(now, then);
        if (ms < 0) return "-";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        return h + "h";
    }

    public static long msAgo(long now, long then) {
        return then <= 0 ? -1 : Math.max(0, now - then);
    }

    public static String bytes(long n) {
        if (n < 1024) return n + " B";
        double v = n;
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.1f %s", v, units[i]);
    }
}
