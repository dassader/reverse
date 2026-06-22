package reverse.server.config;

import java.util.Locale;

public enum ProxyMode {
    TCP("tcp"),
    HTTP("http"),
    HTTPS("https"),
    HTTPS_CLEAN("https-clean");

    private final String label;

    ProxyMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean http() {
        return this != TCP;
    }

    public boolean tls() {
        return this == HTTPS || this == HTTPS_CLEAN;
    }

    public boolean clean() {
        return this == HTTPS_CLEAN;
    }

    public static ProxyMode parse(String value) {
        String v = value == null ? "tcp" : value.trim().toLowerCase(Locale.ROOT);
        for (ProxyMode mode : values()) {
            if (mode.label.equals(v) || mode.name().equalsIgnoreCase(v)) return mode;
        }
        throw new IllegalArgumentException("mode must be tcp, http, https, or https-clean");
    }
}
