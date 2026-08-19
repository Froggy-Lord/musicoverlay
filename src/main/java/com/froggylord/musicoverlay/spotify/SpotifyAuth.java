package com.froggylord.musicoverlay.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spotify OAuth using Authorization Code with PKCE, so only the user's Client ID
 * is needed (no client secret). Opens the consent page in the browser, catches
 * the redirect on a short-lived loopback HTTP server, exchanges the code for
 * tokens, and refreshes them as they expire.
 */
public final class SpotifyAuth {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = String.join(" ",
            "user-read-playback-state", "user-modify-playback-state", "user-read-currently-playing",
            "playlist-read-private", "playlist-read-collaborative",
            "playlist-modify-public", "playlist-modify-private",
            "user-library-read", "user-library-modify");

    private final HttpClient http = HttpClient.newHttpClient();
    private final SecureRandom random = new SecureRandom();
    private volatile HttpServer server;
    private String codeVerifier;
    private String expectedState;

    public boolean isConnected() {
        return SpotifyConfigManager.get().hasTokens();
    }

    /** Kick off the consent flow. {@code status} receives short human-readable updates. */
    public synchronized void beginAuth(Consumer<String> status) {
        SpotifyConfig cfg = SpotifyConfigManager.get();
        if (!cfg.hasClientId()) {
            status.accept("Set your Spotify Client ID first.");
            return;
        }
        try {
            stopServer();
            codeVerifier = randomString(64);
            expectedState = randomString(16);
            String challenge = base64Url(sha256(codeVerifier));

            startCallbackServer(cfg, status);

            String url = AUTH_URL + "?client_id=" + enc(cfg.clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(cfg.redirectUri())
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + enc(challenge)
                    + "&state=" + enc(expectedState)
                    + "&scope=" + enc(SCOPES);
            openBrowser(url);
            status.accept("Approve access in your browser…");
        } catch (Exception e) {
            LOG.error("[MusicOverlay] Spotify auth failed to start", e);
            status.accept("Couldn't start auth: " + e.getMessage());
        }
    }

    public void disconnect() {
        SpotifyConfig cfg = SpotifyConfigManager.get();
        cfg.accessToken = "";
        cfg.refreshToken = "";
        cfg.expiresAtEpoch = 0;
        SpotifyConfigManager.save();
    }

    /**
     * Returns a valid access token, refreshing if it's within a minute of expiry.
     * Blocking; call off the render thread. Returns null if not connected.
     */
    public synchronized String freshAccessToken() {
        SpotifyConfig cfg = SpotifyConfigManager.get();
        if (!cfg.hasTokens()) return null;
        if (System.currentTimeMillis() < cfg.expiresAtEpoch - 60_000 && !cfg.accessToken.isBlank()) {
            return cfg.accessToken;
        }
        return refresh(cfg);
    }

    // ---- internals ----

    private void startCallbackServer(SpotifyConfig cfg, Consumer<String> status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", cfg.redirectPort), 0);
        server.createContext("/callback", exchange -> {
            Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
            String body;
            boolean ok = false;
            if (q.containsKey("error")) {
                body = "Spotify access was denied. You can close this tab.";
                status.accept("Access denied.");
            } else if (!expectedState.equals(q.get("state"))) {
                body = "State mismatch. Please close this tab and try again.";
                status.accept("State mismatch, try again.");
            } else if (q.containsKey("code")) {
                ok = exchangeCode(cfg, q.get("code"));
                body = ok ? "Connected. You can close this tab and return to Minecraft."
                          : "Token exchange failed. Check the log and try again.";
                status.accept(ok ? "Connected to Spotify." : "Token exchange failed.");
            } else {
                body = "Missing authorization code.";
                status.accept("No code returned.");
            }
            byte[] out = ("<html><body style='font-family:sans-serif;background:#1A1A2E;color:#fff;"
                    + "display:flex;align-items:center;justify-content:center;height:100vh'>"
                    + "<h2>" + body + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
            stopServer();
        });
        server.setExecutor(null);
        server.start();
    }

    private boolean exchangeCode(SpotifyConfig cfg, String code) {
        try {
            String form = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(cfg.redirectUri())
                    + "&client_id=" + enc(cfg.clientId)
                    + "&code_verifier=" + enc(codeVerifier);
            JsonObject json = postForm(form);
            if (json == null || !json.has("access_token")) return false;
            storeTokens(cfg, json);
            return true;
        } catch (Exception e) {
            LOG.error("[MusicOverlay] token exchange failed", e);
            return false;
        }
    }

    private String refresh(SpotifyConfig cfg) {
        try {
            String form = "grant_type=refresh_token"
                    + "&refresh_token=" + enc(cfg.refreshToken)
                    + "&client_id=" + enc(cfg.clientId);
            JsonObject json = postForm(form);
            if (json == null || !json.has("access_token")) {
                LOG.warn("[MusicOverlay] Spotify token refresh returned no token");
                return null;
            }
            storeTokens(cfg, json);
            return cfg.accessToken;
        } catch (Exception e) {
            LOG.error("[MusicOverlay] token refresh failed", e);
            return null;
        }
    }

    private void storeTokens(SpotifyConfig cfg, JsonObject json) {
        cfg.accessToken = json.get("access_token").getAsString();
        if (json.has("refresh_token")) cfg.refreshToken = json.get("refresh_token").getAsString();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
        cfg.expiresAtEpoch = System.currentTimeMillis() + expiresIn * 1000;
        SpotifyConfigManager.save();
    }

    private JsonObject postForm(String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            LOG.warn("[MusicOverlay] token endpoint {}: {}", res.statusCode(), res.body());
            return null;
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private void stopServer() {
        if (server != null) {
            try { server.stop(0); } catch (Exception ignored) {}
            server = null;
        }
    }

    private static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            String[] cmd;
            if (os.contains("win")) {
                cmd = new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
            } else if (os.contains("mac") || os.contains("darwin")) {
                cmd = new String[]{"open", url};
            } else {
                cmd = new String[]{"xdg-open", url};
            }
            new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            LOG.warn("[MusicOverlay] couldn't open browser; visit this URL manually: {}", url);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        if (raw == null) return m;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                m.put(java.net.URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                      java.net.URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private String randomString(int len) {
        byte[] b = new byte[len];
        random.nextBytes(b);
        return base64Url(b).substring(0, len);
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
