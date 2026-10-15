package civiclens.automation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Built-in zero-dependency HTTP server for hosting the CivicLens 3D Presentation
 * and system automation assets on http://localhost:8080/.
 */
public class PresentationServer {

    private static final int DEFAULT_PORT = 8080;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("htm", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("doc", "application/msword");
        MIME_TYPES.put("txt", "text/plain; charset=UTF-8");
    }

    private final int port;
    private final Path rootDir;
    private HttpServer server;

    public PresentationServer(int port, Path rootDir) {
        this.port = port;
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    public void start(boolean openBrowser) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // Static file handler for root and presentation
        server.createContext("/", new FileHandler(rootDir));

        // System health and status API endpoint
        server.createContext("/api/status", new StatusHandler());

        server.start();
        System.out.println("==================================================================");
        System.out.println("  CivicLens Localhost Automation Server is Active!                ");
        System.out.println("  URL: http://localhost:" + port + "/civiclens-presentation.html");
        System.out.println("  API: http://localhost:" + port + "/api/status");
        System.out.println("==================================================================");

        if (openBrowser) {
            openInBrowser("http://localhost:" + port + "/civiclens-presentation.html");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[PresentationServer] Server on port " + port + " stopped.");
        }
    }

    public static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                System.out.println("[PresentationServer] Automatically opened " + url + " in default browser.");
            } else {
                // Fallback for Windows command line
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                System.out.println("[PresentationServer] Launched browser via Windows runtime: " + url);
            }
        } catch (Exception ex) {
            System.err.println("[PresentationServer] Notice: Could not open browser automatically (" + ex.getMessage() + "). Please visit " + url);
        }
    }

    private static class FileHandler implements HttpHandler {
        private final Path root;

        public FileHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr == null || pathStr.equals("/") || pathStr.isEmpty()) {
                pathStr = "/civiclens-presentation.html";
            }

            // Sanitize against directory traversal
            Path resolved = root.resolve("." + pathStr).normalize();
            if (!resolved.startsWith(root) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                byte[] notFound = ("404 Not Found: " + pathStr).getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound);
                }
                return;
            }

            String ext = "";
            String fileName = resolved.getFileName().toString();
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < fileName.length() - 1) {
                ext = fileName.substring(dotIdx + 1).toLowerCase();
            }
            String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

            byte[] bytes = Files.readAllBytes(resolved);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\n" +
                    "  \"system\": \"CivicLens\",\n" +
                    "  \"status\": \"OPERATIONAL\",\n" +
                    "  \"presentation\": \"civiclens-presentation.html\",\n" +
                    "  \"port\": 8080,\n" +
                    "  \"timestamp\": \"" + System.currentTimeMillis() + "\"\n" +
                    "}";
            byte[] bytes = json.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean openBrowser = true;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        for (String arg : args) {
            if ("--no-browser".equalsIgnoreCase(arg)) {
                openBrowser = false;
            }
        }

        try {
            PresentationServer server = new PresentationServer(port, Paths.get("."));
            server.start(openBrowser);

            // Keep running until interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            // Sleep thread
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[PresentationServer] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
