package reverse.server.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reverse.server.config.*;
import reverse.server.proxy.ProxyRuntime;
import reverse.server.stats.RouteStats;
import reverse.server.stats.StatsService;
import reverse.server.util.Net;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static reverse.server.util.Text.*;

@RestController
public class AdminController {
    private final RuntimeSettings settings;
    private final RouteStore store;
    private final ProxyRuntime runtime;
    private final StatsService stats;

    public AdminController(RuntimeSettings settings, RouteStore store, ProxyRuntime runtime, StatsService stats) {
        this.settings = settings;
        this.store = store;
        this.runtime = runtime;
        this.stats = stats;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String index(@RequestParam(value = "error", required = false) String error) {
        long now = System.currentTimeMillis();
        String clientIp = serverIp();
        StringBuilder routes = new StringBuilder();
        for (RouteConfig route : store.routes()) routes.append(routeForm(route, now));
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta http-equiv="refresh" content="3">
              <title>Reverse Server</title>
              <style>
                body{margin:0;background:#f6f7f9;color:#171a1f;font:14px system-ui,-apple-system,Segoe UI,sans-serif}
                main{max-width:1180px;margin:0 auto;padding:24px}
                h1{font-size:22px;margin:0 0 16px}
                h2{font-size:15px;margin:22px 0 10px}
                .grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:16px}
                .card,.route,.add{background:white;border:1px solid #dde2e8;border-radius:8px}
                .card{padding:12px}.label{font-size:12px;color:#5d6675;text-transform:uppercase}.value{font-size:20px;margin-top:5px;word-break:break-word}
                .ok{color:#176b37;background:#ddf7e7}.bad{color:#9a1b1b;background:#fee2e2}.pill{display:inline-block;border-radius:999px;padding:3px 9px;font-size:12px}
                .route,.add{display:grid;grid-template-columns:1.2fr 1fr .8fr 1.4fr .8fr 1fr 1.2fr .7fr auto auto;gap:8px;align-items:end;padding:10px;margin-bottom:8px}
                .head{display:grid;grid-template-columns:1.2fr 1fr .8fr 1.4fr .8fr 1fr 1.2fr .7fr auto auto;gap:8px;color:#5d6675;font-size:12px;text-transform:uppercase;padding:0 10px 6px}
                input,select{width:100%%;box-sizing:border-box;border:1px solid #cfd6df;border-radius:6px;padding:8px;background:white;color:#171a1f}
                input[type=checkbox]{width:auto}.check{display:flex;gap:6px;align-items:center;height:36px}
                button{border:1px solid #bbc4d0;background:#ffffff;border-radius:6px;padding:8px 12px;cursor:pointer;white-space:nowrap}
                button.primary{background:#174ea6;color:white;border-color:#174ea6}.danger{color:#9a1b1b}.muted{color:#5d6675}.error{background:#fee2e2;color:#9a1b1b;border:1px solid #fecaca;border-radius:8px;padding:10px;margin-bottom:12px}
                .bar{display:flex;justify-content:space-between;gap:12px;align-items:center}.actions{display:flex;gap:8px;align-items:center}
                .stat{font-size:12px;color:#5d6675;margin-top:4px}.small{font-size:12px}
                .clientbox{display:flex;gap:10px;align-items:center;background:white;border:1px solid #dde2e8;border-radius:8px;padding:10px;margin-bottom:16px}
                code{display:block;white-space:pre-wrap;word-break:break-all;color:#111827;flex:1}
                @media(max-width:980px){.grid{grid-template-columns:1fr 1fr}.head{display:none}.route,.add{grid-template-columns:1fr 1fr}.wide{grid-column:1/-1}}
              </style>
            </head>
            <body>
            <main>
              <div class="bar">
                <h1>Reverse Server</h1>
                <div class="actions">
                  <form method="post" action="/routes/reload"><button>Reload</button></form>
                  <button id="copy-client-source" type="button" onclick="copyClientSource()">Copy Client.java</button>
                  <a class="small muted" href="/api/state">/api/state</a>
                </div>
              </div>
              %s
              <section class="grid">
                <div class="card"><div class="label">Client</div><div class="value"><span class="pill %s">%s</span></div><div class="muted">%s</div></div>
                <div class="card"><div class="label">Last pong</div><div class="value">%s</div><div class="muted">%d pings / %d pongs</div></div>
                <div class="card"><div class="label">Streams</div><div class="value">%d active / %d total</div><div class="muted">%d errors</div></div>
                <div class="card"><div class="label">Traffic</div><div class="value">%s / %s</div><div class="muted">up / down</div></div>
              </section>
              <section class="clientbox"><code id="client-command">Client.java: %s:%d</code><button id="copy-client-command" type="button" onclick="copyClientCommand()">Copy command</button><button type="button" onclick="copyClientSource()">Copy Client.java</button><a class="small muted" href="/client/Client.java">open source</a></section>
              <h2>Routes</h2>
              <div class="head"><div>ID</div><div>Bind</div><div>Public</div><div>Target host</div><div>Target</div><div>Mode</div><div>TLS host</div><div>Enabled</div><div></div><div></div></div>
              %s
              <h2>Add route</h2>
              %s
              <p class="muted small">Config: %s | Control: 0.0.0.0:%d | Admin: %s:%d</p>
            </main>
            <script>
              function clientCommand() {
                return "javac Client.java\\njava Client";
              }
              async function copyText(text, buttonId) {
                try {
                  await navigator.clipboard.writeText(text);
                  var button = document.getElementById(buttonId);
                  if (button) {
                    var old = button.textContent;
                    button.textContent = "Copied";
                    setTimeout(function(){ button.textContent = old; }, 1200);
                  }
                } catch (e) {
                  window.prompt("Copy", text);
                }
              }
              async function copyClientCommand() {
                var text = clientCommand();
                await copyText(text, "copy-client-command");
              }
              async function copyClientSource() {
                var response = await fetch("/client/Client.java", {cache: "no-store"});
                var text = await response.text();
                await copyText(text, "copy-client-source");
              }
              document.getElementById("client-command").textContent = "Configured Client.java -> %s:%d\\n" + clientCommand();
            </script>
            </body>
            </html>
            """.formatted(
            error == null ? "" : "<div class=\"error\">" + html(error) + "</div>",
            stats.clientConnected() ? "ok" : "bad",
            stats.clientConnected() ? "connected" : "offline",
            html(stats.clientRemote()),
            age(now, stats.lastPongAt()),
            stats.pings(),
            stats.pongs(),
            stats.activeStreams(),
            stats.streams(),
            stats.errors(),
            bytes(stats.bytesUp()),
            bytes(stats.bytesDown()),
            html(clientIp),
            settings.controlPort(),
            routes,
            addForm(),
            html(store.file().toString()),
            settings.controlPort(),
            html(settings.adminBind()),
            settings.adminPort(),
            html(clientIp),
            settings.controlPort()
        );
    }

    @GetMapping(value = "/client/Client.java", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> clientJava() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Client.java\"");
        return new ResponseEntity<>(clientSource(serverIp(), settings.controlPort()), headers, HttpStatus.OK);
    }

    private String clientSource(String serverHost, int serverPort) {
        return """
            import java.io.*;
            import java.net.*;
            import java.nio.charset.StandardCharsets;
            import java.util.*;
            import java.util.concurrent.*;

            public class Client {
                static final int BUF = 64 * 1024;
                static final ExecutorService IO = Executors.newCachedThreadPool();
                static final List<Socket> ACTIVE = Collections.synchronizedList(new ArrayList<>());

                public static void main(String[] args) {
                    String serverHost = pick(args, 0, "SERVER_HOST", "%s");
                    int serverPort = Integer.parseInt(pick(args, 1, "SERVER_PORT", "%d"));

                    while (true) {
                        System.out.println("Connecting...");
                        try (Socket control = new Socket(serverHost, serverPort)) {
                            control.setTcpNoDelay(true);
                            System.out.println("Connected!");
                            commands(serverHost, serverPort, control);
                        } catch (Exception ignored) {
                        }
                        closeAll();
                        sleep(1000);
                    }
                }

                static void commands(String serverHost, int serverPort, Socket control) throws IOException {
                    BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8));
                    for (String line; (line = in.readLine()) != null; ) {
                        if ("P".equals(line)) {
                            out.write("P\\n");
                            out.flush();
                        } else if (line.startsWith("S ")) {
                            socket(serverHost, serverPort, line);
                        }
                    }
                }

                static void socket(String serverHost, int serverPort, String command) {
                    Socket tunnel = null;
                    Socket target = null;
                    try {
                        String[] p = command.split(" ", 3);
                        if (p.length != 3) throw new IOException("bad socket command");

                        target = new Socket(p[1], Integer.parseInt(p[2]));
                        target.setTcpNoDelay(true);
                        ACTIVE.add(target);

                        tunnel = new Socket(serverHost, serverPort);
                        tunnel.setTcpNoDelay(true);
                        ACTIVE.add(tunnel);

                        Socket a = tunnel, b = target;
                        IO.execute(() -> {
                            try {
                                pipeBoth(a, b);
                            } catch (InterruptedException ignored) {
                            } finally {
                                ACTIVE.remove(a);
                                ACTIVE.remove(b);
                            }
                        });
                    } catch (Exception ignored) {
                        close(tunnel);
                        close(target);
                    }
                }

                static void pipeBoth(Socket a, Socket b) throws InterruptedException {
                    CountDownLatch done = new CountDownLatch(1);
                    IO.execute(() -> pipe(a, b, done));
                    IO.execute(() -> pipe(b, a, done));
                    done.await();
                    close(a);
                    close(b);
                }

                static void pipe(Socket from, Socket to, CountDownLatch done) {
                    try {
                        byte[] buf = new byte[BUF];
                        InputStream in = from.getInputStream();
                        OutputStream out = to.getOutputStream();
                        for (int n; (n = in.read(buf)) >= 0; ) {
                            out.write(buf, 0, n);
                            out.flush();
                        }
                    } catch (IOException ignored) {
                    } finally {
                        close(from);
                        close(to);
                        done.countDown();
                    }
                }

                static String pick(String[] args, int i, String env, String def) {
                    return args.length > i ? args[i] : System.getenv().getOrDefault(env, def);
                }

                static void sleep(long ms) {
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException ignored) {
                    }
                }

                static void closeAll() {
                    synchronized (ACTIVE) {
                        for (Socket s : ACTIVE) close(s);
                        ACTIVE.clear();
                    }
                }

                static void close(Closeable c) {
                    try {
                        if (c != null) c.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            """.formatted(javaString(serverHost), serverPort);
    }

    private String serverIp() {
        return Net.localIps().get(0);
    }

    private String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @GetMapping("/api/state")
    public Map<String, Object> state() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> routeStates = new ArrayList<>();
        for (RouteConfig route : store.routes()) {
            RouteStats s = stats.route(route.getId());
            routeStates.add(new LinkedHashMap<>(Map.ofEntries(
                Map.entry("id", route.getId()),
                Map.entry("enabled", route.isEnabled()),
                Map.entry("listenerState", s.listenerState()),
                Map.entry("bind", route.getBind()),
                Map.entry("publicPort", route.getPublicPort()),
                Map.entry("targetHost", route.getTargetHost()),
                Map.entry("targetPort", route.getTargetPort()),
                Map.entry("mode", route.getMode().label()),
                Map.entry("tlsHost", route.tlsName()),
                Map.entry("requests", s.requests()),
                Map.entry("active", s.active()),
                Map.entry("errors", s.errors()),
                Map.entry("bytesUp", s.bytesUp()),
                Map.entry("bytesDown", s.bytesDown()),
                Map.entry("lastRequestMsAgo", msAgo(now, s.lastRequestAt()))
            )));
        }
        return new LinkedHashMap<>(Map.of(
            "client", new LinkedHashMap<>(Map.of(
                "connected", stats.clientConnected(),
                "remote", stats.clientRemote(),
                "lastPongMsAgo", msAgo(now, stats.lastPongAt()),
                "connects", stats.clientConnects(),
                "pings", stats.pings(),
                "pongs", stats.pongs()
            )),
            "streams", new LinkedHashMap<>(Map.of(
                "total", stats.streams(),
                "active", stats.activeStreams(),
                "finished", stats.finishedStreams(),
                "errors", stats.errors(),
                "bytesUp", stats.bytesUp(),
                "bytesDown", stats.bytesDown(),
                "dataSockets", stats.dataSockets(),
                "droppedDataSockets", stats.droppedDataSockets()
            )),
            "routes", routeStates,
            "configFile", store.file().toString()
        ));
    }

    @PostMapping("/routes")
    public ResponseEntity<Void> save(@RequestParam Map<String, String> form) throws IOException {
        try {
            store.save(route(form));
            runtime.reloadRoutes();
            return redirect("/");
        } catch (Exception e) {
            return redirect("/?error=" + encode(e.getMessage()));
        }
    }

    @PostMapping("/routes/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        store.delete(id);
        runtime.reloadRoutes();
        return redirect("/");
    }

    @PostMapping("/routes/reload")
    public ResponseEntity<Void> reload() {
        runtime.reloadRoutes();
        return redirect("/");
    }

    private RouteConfig route(Map<String, String> form) {
        RouteConfig route = new RouteConfig();
        route.setId(form.getOrDefault("id", ""));
        route.setBind(form.getOrDefault("bind", "0.0.0.0"));
        route.setPublicPort(Integer.parseInt(form.getOrDefault("publicPort", "0")));
        route.setTargetHost(form.getOrDefault("targetHost", "127.0.0.1"));
        route.setTargetPort(Integer.parseInt(form.getOrDefault("targetPort", "0")));
        route.setMode(ProxyMode.parse(form.getOrDefault("mode", "tcp")));
        route.setTlsHost(form.getOrDefault("tlsHost", ""));
        route.setEnabled("on".equals(form.get("enabled")) || "true".equalsIgnoreCase(form.get("enabled")));
        return route.normalized();
    }

    private String routeForm(RouteConfig route, long now) {
        RouteStats s = stats.route(route.getId());
        String id = html(route.getId());
        return """
            <form class="route" method="post" action="/routes">
              <input name="id" value="%s" readonly>
              <input name="bind" value="%s">
              <input name="publicPort" type="number" min="1" max="65535" value="%d">
              <input class="wide" name="targetHost" value="%s">
              <input name="targetPort" type="number" min="1" max="65535" value="%d">
              %s
              <input class="wide" name="tlsHost" value="%s">
              <label class="check"><input name="enabled" type="checkbox" %s> on</label>
              <button class="primary">Save</button>
              <button class="danger" form="delete-%s">Delete</button>
              <div class="stat wide">%s | req %d | active %d | err %d | %s / %s | last %s</div>
            </form>
            <form id="delete-%s" method="post" action="/routes/%s/delete"></form>
            """.formatted(
            id,
            html(route.getBind()),
            route.getPublicPort(),
            html(route.getTargetHost()),
            route.getTargetPort(),
            modeSelect(route.getMode()),
            html(route.getTlsHost()),
            route.isEnabled() ? "checked" : "",
            id,
            html(s.listenerState()),
            s.requests(),
            s.active(),
            s.errors(),
            bytes(s.bytesUp()),
            bytes(s.bytesDown()),
            age(now, s.lastRequestAt()),
            id,
            id
        );
    }

    private String addForm() {
        return """
            <form class="add" method="post" action="/routes">
              <input name="id" placeholder="id" value="mcp2">
              <input name="bind" value="0.0.0.0">
              <input name="publicPort" type="number" min="1" max="65535" value="7778">
              <input class="wide" name="targetHost" value="127.0.0.1">
              <input name="targetPort" type="number" min="1" max="65535" value="64343">
              %s
              <input class="wide" name="tlsHost" placeholder="real.domain">
              <label class="check"><input name="enabled" type="checkbox" checked> on</label>
              <button class="primary">Add</button>
              <span></span>
            </form>
            """.formatted(modeSelect(ProxyMode.HTTP));
    }

    private String modeSelect(ProxyMode selected) {
        StringBuilder out = new StringBuilder("<select name=\"mode\">");
        for (ProxyMode mode : ProxyMode.values()) {
            out.append("<option value=\"").append(mode.label()).append("\"");
            if (mode == selected) out.append(" selected");
            out.append(">").append(mode.label()).append("</option>");
        }
        out.append("</select>");
        return out.toString();
    }

    private ResponseEntity<Void> redirect(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    private String encode(String value) {
        return value == null ? "" : value.replace(" ", "%20").replace("\"", "%22").replace("<", "%3C").replace(">", "%3E");
    }
}
