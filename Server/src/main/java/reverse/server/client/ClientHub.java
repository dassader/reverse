package reverse.server.client;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import reverse.server.config.RouteConfig;
import reverse.server.config.RuntimeSettings;
import reverse.server.stats.StatsService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static reverse.server.util.Sockets.addr;
import static reverse.server.util.Sockets.close;

@Component
public class ClientHub {
    private final int port;
    private final ExecutorService io;
    private final StatsService stats;
    private final Object openLock = new Object();
    private final BlockingQueue<Socket> data = new LinkedBlockingQueue<>();
    private final List<Socket> active = new ArrayList<>();
    private volatile ServerSocket server;
    private volatile Control control;
    private volatile boolean awaitingData;
    private volatile boolean running;

    public ClientHub(RuntimeSettings settings, ExecutorService io, StatsService stats) {
        this.port = settings.controlPort();
        this.io = io;
        this.stats = stats;
    }

    public void start() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", port));
        server = socket;
        running = true;
        io.execute(() -> accept(socket));
    }

    public Socket openTunnel(RouteConfig route) throws Exception {
        synchronized (openLock) {
            Control current = control;
            if (current == null) throw new IOException("client is not connected");
            awaitingData = true;
            try {
                current.send("S " + route.getTargetHost() + " " + route.getTargetPort());
                Socket tunnel = data.poll(30, TimeUnit.SECONDS);
                if (tunnel == null) throw new IOException("client did not open data socket");
                synchronized (active) {
                    active.add(tunnel);
                }
                return tunnel;
            } catch (IOException e) {
                lost(current);
                throw e;
            } finally {
                awaitingData = false;
            }
        }
    }

    public void release(Socket tunnel) {
        synchronized (active) {
            active.remove(tunnel);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        close(server);
        Control current = control;
        if (current != null) close(current.socket);
        closeAll();
        Socket socket;
        while ((socket = data.poll()) != null) close(socket);
    }

    private void accept(ServerSocket socket) {
        while (running) {
            try {
                Socket incoming = socket.accept();
                incoming.setTcpNoDelay(true);
                io.execute(() -> incoming(incoming));
            } catch (IOException e) {
                if (running) System.out.println("[S] client accept error: " + e.getMessage());
            }
        }
    }

    private void incoming(Socket socket) {
        try {
            if (awaitingData) {
                stats.dataSocket();
                data.offer(socket);
                return;
            }
            setControl(socket);
        } catch (Exception e) {
            stats.droppedDataSocket();
            close(socket);
        }
    }

    private void setControl(Socket socket) throws IOException {
        Control old = control;
        if (old != null) close(old.socket);
        Control current = new Control(socket);
        control = current;
        stats.clientConnected(socket);
        System.out.println("[S] client connected " + addr(socket));
        io.execute(() -> read(current));
        io.execute(() -> ping(current));
    }

    private void read(Control current) {
        try {
            String line;
            while ((line = current.in.readLine()) != null) {
                if ("P".equals(line)) {
                    current.lastPong = System.currentTimeMillis();
                    stats.pong(current.lastPong);
                }
            }
        } catch (IOException ignored) {
        } finally {
            lost(current);
        }
    }

    private void ping(Control current) {
        while (running && control == current) {
            sleep(5000);
            try {
                current.send("P");
                stats.ping();
                if (System.currentTimeMillis() - current.lastPong > 15000) lost(current);
            } catch (IOException e) {
                lost(current);
            }
        }
    }

    private void lost(Control current) {
        if (control != current) return;
        control = null;
        stats.clientDisconnected();
        close(current.socket);
        closeAll();
        Socket socket;
        while ((socket = data.poll()) != null) close(socket);
        System.out.println("[S] client disconnected");
    }

    private void closeAll() {
        synchronized (active) {
            for (Socket socket : active) close(socket);
            active.clear();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static class Control {
        final Socket socket;
        final BufferedReader in;
        final BufferedWriter out;
        volatile long lastPong = System.currentTimeMillis();

        Control(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        synchronized void send(String line) throws IOException {
            out.write(line);
            out.write('\n');
            out.flush();
        }
    }
}
