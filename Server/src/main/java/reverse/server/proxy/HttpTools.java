package reverse.server.proxy;

import reverse.server.config.RouteConfig;
import reverse.server.stats.StatsService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class HttpTools {
    private HttpTools() {
    }

    public static String forwardFirstRequest(RouteConfig route, StatsService stats, String consumer, Socket from, Socket to, String host, boolean clean) throws IOException {
        byte[] head = readHead(from.getInputStream());
        String request = requestLine(head);
        byte[] rewritten = clean ? cleanHeaders(head, host) : rewriteHost(head, host);
        to.getOutputStream().write(rewritten);
        to.getOutputStream().flush();
        stats.up(route, consumer, rewritten.length);
        return request;
    }

    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            out.write(b);
            byte[] h = out.toByteArray();
            int n = h.length;
            if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
            if (n > 64 * 1024) break;
        }
        return out.toByteArray();
    }

    private static byte[] rewriteHost(byte[] raw, String host) {
        String request = new String(raw, StandardCharsets.ISO_8859_1);
        if (!looksHttp(request)) return raw;
        String[] lines = request.split("\r\n", -1);
        boolean seen = false;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].regionMatches(true, 0, "Host:", 0, 5)) {
                lines[i] = "Host: " + host;
                seen = true;
            }
        }
        if (!seen && lines.length > 1) lines[0] = lines[0] + "\r\nHost: " + host;
        return String.join("\r\n", lines).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] cleanHeaders(byte[] raw, String host) {
        String request = new String(raw, StandardCharsets.ISO_8859_1);
        if (!looksHttp(request)) return raw;
        String[] lines = request.split("\r\n", -1);
        String contentLength = null;
        String contentType = null;
        for (String line : lines) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) contentLength = line;
            if (line.regionMatches(true, 0, "Content-Type:", 0, 13)) contentType = line;
        }
        StringBuilder out = new StringBuilder();
        out.append(lines[0]).append("\r\n");
        out.append("Host: ").append(host).append("\r\n");
        if (contentLength != null) out.append(contentLength).append("\r\n");
        if (contentType != null) out.append(contentType).append("\r\n");
        out.append("\r\n");
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static boolean looksHttp(String value) {
        return value.startsWith("GET ") || value.startsWith("POST ") || value.startsWith("HEAD ")
            || value.startsWith("PUT ") || value.startsWith("DELETE ") || value.startsWith("PATCH ")
            || value.startsWith("OPTIONS ");
    }

    private static String requestLine(byte[] raw) {
        if (raw.length == 0) return "empty request";
        String request = new String(raw, StandardCharsets.ISO_8859_1);
        int end = request.indexOf("\r\n");
        return end >= 0 ? request.substring(0, end) : request;
    }
}
