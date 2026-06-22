# Reverse MCP Proxy

Reverse proxy for exposing a local MCP endpoint through one outbound client connection.

- `Client/Client.java` is intentionally tiny. It only connects to the server and obeys `P` / `S host port`.
- `Server/` is a Spring Boot project with web UI, route statistics, route editing, and persistent config.

No auth is enabled yet. Use only for private-network testing.

## Server

```bash
cd Server
./gradlew build
CONTROL_PORT=7443 ADMIN_PORT=8080 CONFIG_FILE=server-config.json \
  java -jar build/libs/reverse-server-1.0.0.jar
```

Defaults:

```text
CONTROL_PORT=7443
ADMIN_BIND=0.0.0.0
ADMIN_PORT=8080
CONFIG_FILE=server-config.json
```

Open the web UI:

```text
http://server-ip:8080/
```

The UI has a `Copy Client` button. It copies a ready command using the host from the opened page and the configured control port:

```bash
javac Client/Client.java
java -cp Client Client server-ip 7443
```

The server creates `server-config.json` on first start with a default IntelliJ MCP route:

```text
0.0.0.0:7777 -> http 127.0.0.1:64343
```

## Client

```bash
javac Client/Client.java
java -cp Client Client server-ip 7443
```

Client output stays minimal:

```text
Connecting...
Connected!
```

## Modes

- `tcp`: raw TCP passthrough.
- `http`: HTTP passthrough with `Host` rewrite to `targetHost:targetPort`.
- `https`: Codex uses HTTP; Server opens TLS over the reverse tunnel to `targetHost:targetPort` with SNI/Host `tlsHost`.
- `https-clean`: like `https`, but strips request headers down to `Host` plus body-required `Content-Length` and `Content-Type`.

Traffic path:

```text
Codex -> Server route port -> Client data socket -> target host:port
```

Protocol between Server and Client:

```text
P
S host port
```

The first client socket is the command channel. Every `S host port` command makes the client open one new data socket back to the same server port and connect it to the target.
