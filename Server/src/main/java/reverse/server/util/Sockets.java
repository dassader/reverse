package reverse.server.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

public final class Sockets {
    private Sockets() {
    }

    public static String addr(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown" : address.toString().replaceFirst("^/", "");
    }

    public static void close(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (IOException ignored) {
        }
    }

    public static void close(Socket socket) {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    public static String shortError(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
