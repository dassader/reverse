package reverse.server.config;

public class RouteConfig {
    private String id = "";
    private String bind = "0.0.0.0";
    private int publicPort;
    private String targetHost = "127.0.0.1";
    private int targetPort;
    private ProxyMode mode = ProxyMode.TCP;
    private String tlsHost = "";
    private boolean enabled = true;

    public RouteConfig() {
    }

    public RouteConfig(String id, String bind, int publicPort, String targetHost, int targetPort, ProxyMode mode, String tlsHost, boolean enabled) {
        this.id = id;
        this.bind = bind;
        this.publicPort = publicPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.mode = mode;
        this.tlsHost = tlsHost;
        this.enabled = enabled;
    }

    public String target() {
        return targetHost + ":" + targetPort;
    }

    public String tlsName() {
        return tlsHost == null || tlsHost.isBlank() || "-".equals(tlsHost) ? targetHost : tlsHost;
    }

    public RouteConfig normalized() {
        id = id == null ? "" : id.trim();
        bind = blank(bind, "0.0.0.0");
        targetHost = blank(targetHost, "127.0.0.1");
        tlsHost = tlsHost == null ? "" : tlsHost.trim();
        if (mode == null) mode = ProxyMode.TCP;
        if (!id.matches("[A-Za-z0-9_.-]{1,48}")) throw new IllegalArgumentException("route id must match [A-Za-z0-9_.-]{1,48}");
        if (publicPort < 1 || publicPort > 65535) throw new IllegalArgumentException("public port is invalid");
        if (targetPort < 1 || targetPort > 65535) throw new IllegalArgumentException("target port is invalid");
        return this;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBind() {
        return bind;
    }

    public void setBind(String bind) {
        this.bind = bind;
    }

    public int getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(int publicPort) {
        this.publicPort = publicPort;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public ProxyMode getMode() {
        return mode;
    }

    public void setMode(ProxyMode mode) {
        this.mode = mode;
    }

    public String getTlsHost() {
        return tlsHost;
    }

    public void setTlsHost(String tlsHost) {
        this.tlsHost = tlsHost;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
