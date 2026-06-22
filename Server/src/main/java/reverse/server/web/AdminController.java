package reverse.server.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reverse.server.config.*;
import reverse.server.proxy.ProxyRuntime;
import reverse.server.stats.ConsumerStats;
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
        List<RouteConfig> routes = store.routes();
        List<String> ips = Net.localIps();
        String defaultIp = ips.get(0);
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Reverse Server</title>
              <style>
                :root{--bg:#f4f6f8;--panel:#fff;--line:#dfe5ec;--text:#14171f;--muted:#667085;--blue:#1d4ed8;--green:#15803d;--red:#b42318;--amber:#a16207}
                body{margin:0;background:var(--bg);color:var(--text);font:14px system-ui,-apple-system,Segoe UI,sans-serif}
                main{max-width:1320px;margin:0 auto;padding:22px 22px 96px}
                h1{font-size:22px;margin:0} h2{font-size:15px;margin:18px 0 10px}
                button{border:1px solid #bcc6d3;background:#fff;border-radius:8px;padding:8px 12px;cursor:pointer;white-space:nowrap}
                button.primary{background:var(--blue);border-color:var(--blue);color:#fff}.danger{color:var(--red)}
                input,select{width:100%%;box-sizing:border-box;border:1px solid #cfd6df;border-radius:7px;padding:8px;background:#fff;color:var(--text)}
                .bar{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:14px}.actions{display:flex;gap:8px;align-items:center}
                .muted{color:var(--muted)}.small{font-size:12px}.error{background:#fee2e2;color:var(--red);border:1px solid #fecaca;border-radius:8px;padding:10px;margin-bottom:12px}
                .kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:14px}.kpi{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:12px}.label{color:var(--muted);font-size:12px;text-transform:uppercase}.value{font-size:22px;margin-top:5px}
                .map{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:14px}.flow{display:grid;grid-template-columns:1.2fr 34px 1fr 34px 1.2fr 34px 1fr 34px 1.2fr;gap:8px;align-items:center;margin-bottom:8px}.arrow{text-align:center;color:var(--muted)}
                .node{border:1px solid var(--line);border-radius:8px;background:#fff;padding:10px;min-height:58px}.node.server{border-color:#93c5fd}.node.client{border-color:#86efac}.node.tunnel{border-color:#facc15}.node.consumer{border-color:#c4b5fd}.node.target{border-color:#fdba74}
                .node-title{display:flex;justify-content:space-between;gap:8px;font-weight:650}.node-sub{margin-top:5px;color:var(--muted);font-size:12px;word-break:break-all}.pill{display:inline-block;border-radius:999px;padding:3px 8px;font-size:12px;background:#eef2ff;color:#3730a3}.ok{background:#dcfce7;color:#166534}.bad{background:#fee2e2;color:#991b1b}.warn{background:#fef3c7;color:#92400e}
                .board{display:grid;grid-template-columns:1fr 1.25fr 1fr;gap:12px;margin-top:14px}.column{min-width:0}.cards{display:grid;gap:10px}.empty{border:1px dashed #c7d0dd;border-radius:8px;padding:14px;color:var(--muted);background:#fff}
                .stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:10px}.stat{background:#f8fafc;border:1px solid #e6ebf1;border-radius:7px;padding:8px}.stat b{display:block;font-size:15px}
                details{margin-top:10px}summary{cursor:pointer;color:var(--muted)}.edit{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}.edit .wide{grid-column:1/-1}.edit-actions{display:flex;gap:8px;margin-top:8px}
                dialog{border:0;border-radius:12px;padding:0;box-shadow:0 24px 80px #0004;max-width:min(760px,calc(100vw - 28px));width:720px}dialog::backdrop{background:#11182780}.modal{padding:18px}.modal-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
                .client-tools{display:grid;grid-template-columns:1fr auto auto;gap:8px;align-items:center;margin-bottom:10px}pre{margin:0;background:#111827;color:#e5e7eb;border-radius:8px;padding:12px;max-height:56vh;overflow:auto;white-space:pre-wrap}
                .fabs{position:fixed;right:22px;bottom:22px;display:flex;flex-direction:column;gap:12px}.fab{width:56px;height:56px;border-radius:999px;background:var(--blue);color:#fff;border:0;font-size:26px;box-shadow:0 10px 26px #0003}.fab.secondary{background:#111827;font-size:17px}
                @media(max-width:980px){.kpis,.board{grid-template-columns:1fr}.flow{grid-template-columns:1fr}.arrow{display:none}.edit{grid-template-columns:1fr}.client-tools{grid-template-columns:1fr}}
              </style>
            </head>
            <body>
            <main>
              <div class="bar">
                <h1>Reverse Server</h1>
                <div class="actions"><form method="post" action="/routes/reload"><button>Reload</button></form><a class="small muted" href="/api/state">/api/state</a></div>
              </div>
              %s
              <section class="kpis">%s</section>
              <section class="map"><h2>Live Map</h2>%s</section>
              <section class="board">
                <div class="column"><h2>Consumers</h2><div class="cards">%s</div></div>
                <div class="column"><h2>Tunnels</h2><div class="cards">%s</div></div>
                <div class="column"><h2>Client / Targets</h2><div class="cards">%s%s</div></div>
              </section>
            </main>
            <div class="fabs">
              <button class="fab" type="button" onclick="openRouteModal()">+</button>
              <button class="fab secondary" type="button" onclick="openClientModal()">&lt;/&gt;</button>
            </div>
            <dialog id="route-modal"><div class="modal"><div class="modal-head"><h2>Add tunnel</h2><button onclick="closeRouteModal()">Close</button></div>%s</div></dialog>
            <dialog id="client-modal"><div class="modal"><div class="modal-head"><h2>Configured Client.java</h2><button onclick="closeClientModal()">Close</button></div><div class="client-tools"><select id="client-ip" onchange="updateClientPreview()">%s</select><button id="copy-client-command" onclick="copyClientCommand()">Copy command</button><button id="copy-client-source" onclick="copyClientSource()">Copy Client.java</button></div><pre id="client-source"></pre></div></dialog>
            <script>
              function selectedClientIp(){var s=document.getElementById("client-ip");return s?s.value:"%s"}
              function clientSourceUrl(){return "/client/Client.java?host="+encodeURIComponent(selectedClientIp())}
              function clientCommand(){return "javac Client.java\\njava Client"}
              async function copyText(text,id){try{await navigator.clipboard.writeText(text);var b=document.getElementById(id);if(b){var o=b.textContent;b.textContent="Copied";setTimeout(function(){b.textContent=o},1200)}}catch(e){window.prompt("Copy",text)}}
              async function updateClientPreview(){try{localStorage.setItem("clientIp",selectedClientIp())}catch(e){}var r=await fetch(clientSourceUrl(),{cache:"no-store"});document.getElementById("client-source").textContent=await r.text()}
              async function copyClientSource(){var r=await fetch(clientSourceUrl(),{cache:"no-store"});copyText(await r.text(),"copy-client-source")}
              function copyClientCommand(){copyText(clientCommand(),"copy-client-command")}
              function openRouteModal(){document.getElementById("route-modal").showModal()}
              function closeRouteModal(){document.getElementById("route-modal").close()}
              async function openClientModal(){var s=document.getElementById("client-ip");try{var v=localStorage.getItem("clientIp");if(v&&s){for(var i=0;i<s.options.length;i++)if(s.options[i].value===v)s.value=v}}catch(e){}document.getElementById("client-modal").showModal();await updateClientPreview()}
              function closeClientModal(){document.getElementById("client-modal").close()}
              setInterval(function(){if(!document.querySelector("dialog[open]")) location.reload()},4000)
            </script>
            </body>
            </html>
            """.formatted(
            error == null ? "" : "<div class=\"error\">" + html(error) + "</div>",
            kpis(routes, now),
            flowRows(routes, now),
            consumerCards(routes, now),
            tunnelCards(routes, now),
            clientCard(now),
            targetCards(routes),
            addForm(),
            ipOptions(ips),
            html(defaultIp)
        );
    }

    @GetMapping(value = "/client/Client.java", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> clientJava(@RequestParam(value = "host", required = false) String host) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Client.java\"");
        return new ResponseEntity<>(clientSource(clientHost(host), settings.controlPort()), headers, HttpStatus.OK);
    }

    @GetMapping("/api/state")
    public Map<String, Object> state() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> routeStates = new ArrayList<>();
        for (RouteConfig route : store.routes()) {
            RouteStats s = stats.route(route.getId());
            List<Map<String, Object>> consumers = new ArrayList<>();
            for (ConsumerStats c : s.consumers()) {
                consumers.add(new LinkedHashMap<>(Map.of(
                    "remote", c.remote(),
                    "requests", c.requests(),
                    "active", c.active(),
                    "errors", c.errors(),
                    "bytesUp", c.bytesUp(),
                    "bytesDown", c.bytesDown(),
                    "lastSeenMsAgo", msAgo(now, c.lastSeenAt())
                )));
            }
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
                Map.entry("lastRequestMsAgo", msAgo(now, s.lastRequestAt())),
                Map.entry("consumers", consumers)
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

    private String kpis(List<RouteConfig> routes, long now) {
        return kpi("Client", stats.clientConnected() ? "online" : "offline", stats.clientConnected() ? stats.clientRemote() : "waiting")
            + kpi("Streams", stats.activeStreams() + " active", stats.streams() + " total / " + stats.errors() + " errors")
            + kpi("Traffic", bytes(stats.bytesUp()) + " up", bytes(stats.bytesDown()) + " down")
            + kpi("Topology", routes.size() + " tunnels", consumerCount(routes) + " consumers / pong " + age(now, stats.lastPongAt()));
    }

    private String kpi(String label, String value, String sub) {
        return "<div class=\"kpi\"><div class=\"label\">" + html(label) + "</div><div class=\"value\">" + html(value) + "</div><div class=\"muted small\">" + html(sub) + "</div></div>";
    }

    private String flowRows(List<RouteConfig> routes, long now) {
        if (routes.isEmpty()) return "<div class=\"empty\">No tunnels configured.</div>";
        StringBuilder out = new StringBuilder();
        for (RouteConfig route : routes) {
            RouteStats s = stats.route(route.getId());
            List<ConsumerStats> consumers = s.consumers();
            if (consumers.isEmpty()) out.append(flow(route, null, now));
            else for (ConsumerStats consumer : consumers) out.append(flow(route, consumer, now));
        }
        return out.toString();
    }

    private String flow(RouteConfig route, ConsumerStats consumer, long now) {
        RouteStats s = stats.route(route.getId());
        String consumerName = consumer == null ? "waiting consumer" : consumer.remote();
        String consumerSub = consumer == null ? "no requests yet" : consumer.requests() + " req / " + consumer.active() + " active / " + age(now, consumer.lastSeenAt());
        return """
            <div class="flow">
              %s<div class="arrow">-></div>%s<div class="arrow">-></div>%s<div class="arrow">-></div>%s<div class="arrow">-></div>%s
            </div>
            """.formatted(
            node("consumer", consumerName, consumerSub, "consumer"),
            node("server", "Server", "0.0.0.0:" + settings.controlPort(), "server"),
            node("tunnel", route.getId(), route.getMode().label() + " / " + bytes(s.bytesUp()) + " up / " + bytes(s.bytesDown()) + " down", "tunnel"),
            node("client", stats.clientConnected() ? "Client" : "Client offline", stats.clientConnected() ? stats.clientRemote() : "waiting for reverse connection", "client " + (stats.clientConnected() ? "ok" : "bad")),
            node("target", route.target(), "via client socket", "target")
        );
    }

    private String node(String kind, String title, String sub, String cls) {
        return "<div class=\"node " + cls + "\"><div class=\"node-title\"><span>" + html(title) + "</span><span class=\"pill\">" + html(kind) + "</span></div><div class=\"node-sub\">" + html(sub) + "</div></div>";
    }

    private String consumerCards(List<RouteConfig> routes, long now) {
        StringBuilder out = new StringBuilder();
        for (RouteConfig route : routes) {
            for (ConsumerStats c : stats.route(route.getId()).consumers()) {
                out.append("""
                    <article class="node consumer">
                      <div class="node-title"><span>%s</span><span class="pill">%s</span></div>
                      <div class="node-sub">%d req / %d active / %d errors / last %s</div>
                      <div class="stats"><div class="stat"><b>%s</b><span class="small muted">up</span></div><div class="stat"><b>%s</b><span class="small muted">down</span></div></div>
                    </article>
                    """.formatted(html(c.remote()), html(route.getId()), c.requests(), c.active(), c.errors(), age(now, c.lastSeenAt()), bytes(c.bytesUp()), bytes(c.bytesDown())));
            }
        }
        return out.isEmpty() ? "<div class=\"empty\">Consumers will appear here when Codex connects to a tunnel.</div>" : out.toString();
    }

    private String tunnelCards(List<RouteConfig> routes, long now) {
        if (routes.isEmpty()) return "<div class=\"empty\">Use the + button to add a tunnel.</div>";
        StringBuilder out = new StringBuilder();
        for (RouteConfig route : routes) {
            RouteStats s = stats.route(route.getId());
            out.append("""
                <article class="node tunnel">
                  <div class="node-title"><span>%s</span><span class="pill %s">%s</span></div>
                  <div class="node-sub">listen %s:%d -> %s</div>
                  <div class="stats">
                    <div class="stat"><b>%d</b><span class="small muted">requests</span></div>
                    <div class="stat"><b>%d</b><span class="small muted">active</span></div>
                    <div class="stat"><b>%d</b><span class="small muted">errors</span></div>
                    <div class="stat"><b>%s</b><span class="small muted">last</span></div>
                  </div>
                  <details><summary>Settings</summary>%s</details>
                </article>
                """.formatted(
                html(route.getId()),
                route.isEnabled() ? "ok" : "bad",
                route.isEnabled() ? html(s.listenerState()) : "disabled",
                html(route.getBind()),
                route.getPublicPort(),
                html(route.target()),
                s.requests(),
                s.active(),
                s.errors(),
                age(now, s.lastRequestAt()),
                editForm(route)
            ));
        }
        return out.toString();
    }

    private String clientCard(long now) {
        String state = stats.clientConnected() ? "online" : "offline";
        String cls = stats.clientConnected() ? "ok" : "bad";
        return """
            <article class="node client">
              <div class="node-title"><span>Client</span><span class="pill %s">%s</span></div>
              <div class="node-sub">%s</div>
              <div class="stats"><div class="stat"><b>%s</b><span class="small muted">last pong</span></div><div class="stat"><b>%d</b><span class="small muted">connects</span></div><div class="stat"><b>%d</b><span class="small muted">data sockets</span></div></div>
            </article>
            """.formatted(cls, state, html(stats.clientRemote()), age(now, stats.lastPongAt()), stats.clientConnects(), stats.dataSockets());
    }

    private String targetCards(List<RouteConfig> routes) {
        if (routes.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (RouteConfig route : routes) {
            out.append("""
                <article class="node target">
                  <div class="node-title"><span>%s</span><span class="pill">%s</span></div>
                  <div class="node-sub">target %s:%d / TLS %s</div>
                </article>
                """.formatted(html(route.getId()), html(route.getMode().label()), html(route.getTargetHost()), route.getTargetPort(), html(route.tlsName())));
        }
        return out.toString();
    }

    private String editForm(RouteConfig route) {
        String id = html(route.getId());
        return """
            <form class="edit" method="post" action="/routes">
              <input name="id" value="%s" readonly>
              <input name="bind" value="%s">
              <input name="publicPort" type="number" min="1" max="65535" value="%d">
              <input name="targetHost" value="%s">
              <input name="targetPort" type="number" min="1" max="65535" value="%d">
              %s
              <input class="wide" name="tlsHost" value="%s" placeholder="TLS host">
              <label class="small"><input name="enabled" type="checkbox" %s> enabled</label>
              <div class="edit-actions wide"><button class="primary">Save</button><button class="danger" form="delete-%s">Delete</button></div>
            </form>
            <form id="delete-%s" method="post" action="/routes/%s/delete"></form>
            """.formatted(id, html(route.getBind()), route.getPublicPort(), html(route.getTargetHost()), route.getTargetPort(), modeSelect(route.getMode()), html(route.getTlsHost()), route.isEnabled() ? "checked" : "", id, id, id);
    }

    private String addForm() {
        return """
            <form class="edit" method="post" action="/routes">
              <input name="id" placeholder="id" value="mcp">
              <input name="bind" value="0.0.0.0">
              <input name="publicPort" type="number" min="1" max="65535" value="7777">
              <input name="targetHost" value="127.0.0.1">
              <input name="targetPort" type="number" min="1" max="65535" value="64343">
              %s
              <input class="wide" name="tlsHost" placeholder="real.domain">
              <label class="small"><input name="enabled" type="checkbox" checked> enabled</label>
              <div class="edit-actions wide"><button class="primary">Add tunnel</button></div>
            </form>
            """.formatted(modeSelect(ProxyMode.HTTP));
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

    private int consumerCount(List<RouteConfig> routes) {
        int count = 0;
        for (RouteConfig route : routes) count += stats.route(route.getId()).consumers().size();
        return count;
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

    private String clientHost(String host) {
        List<String> ips = Net.localIps();
        if (host != null && ips.contains(host)) return host;
        return ips.get(0);
    }

    private String ipOptions(List<String> ips) {
        StringBuilder out = new StringBuilder();
        for (String ip : ips) out.append("<option value=\"").append(html(ip)).append("\">").append(html(ip)).append("</option>");
        return out.toString();
    }

    private String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
