package reverse.server.proxy;

import reverse.server.config.RouteConfig;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;

public final class Tls {
    private Tls() {
    }

    public static SSLSocket open(Socket tunnel, RouteConfig route) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(tunnel, route.tlsName(), route.getTargetPort(), true);
        SSLParameters parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(parameters);
        ssl.startHandshake();
        return ssl;
    }
}
