package br.com.fabricads.poc.spawn;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class RestServer {

    private final HttpServer httpServer;

    private RestServer(String host, int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
    }

    public static RestServer create(String host, int port) throws IOException {
        return new RestServer(host, port);
    }

    public RestServer withRoute(String path, HttpHandler httpHandler) {
        httpServer.createContext(path, httpHandler);
        return this;
    }

    public void start() {
        httpServer.start();
    }
}
