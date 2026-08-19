package com.froggylord.musicoverlay.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper over the Spotify Web API. Every call runs off the game thread and
 * attaches a fresh bearer token; playback-control endpoints (skip, queue, play)
 * need Spotify Premium and an active device, which the API enforces.
 */
public final class SpotifyClient {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final String API = "https://api.spotify.com/v1";

    private final HttpClient http = HttpClient.newHttpClient();
    private final SpotifyAuth auth;

    public SpotifyClient(SpotifyAuth auth) {
        this.auth = auth;
    }

    /** The track currently playing, with its real Spotify URI and saved state. */
    public CompletableFuture<PlaybackState> currentPlayback() {
        return CompletableFuture.supplyAsync(() -> {
            HttpResponse<String> res = send("GET", "/me/player/currently-playing", null);
            if (res == null || res.statusCode() == 204 || res.body().isBlank()) return null;
            if (res.statusCode() / 100 != 2) return null;
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject item = json.getAsJsonObject("item");
            if (item == null) return null;
            String uri = str(item, "uri");
            String id = str(item, "id");
            String title = str(item, "name");
            String artist = "";
            JsonArray artists = item.getAsJsonArray("artists");
            if (artists != null && artists.size() > 0) {
                artist = str(artists.get(0).getAsJsonObject(), "name");
            }
            boolean saved = id != null && isSaved(id);
            return new PlaybackState(uri, id, title, artist, saved);
        });
    }

    /** The user's playlists (first 50). */
    public CompletableFuture<List<SpotifyPlaylist>> playlists() {
        return CompletableFuture.supplyAsync(() -> {
            List<SpotifyPlaylist> out = new ArrayList<>();
            HttpResponse<String> res = send("GET", "/me/playlists?limit=50", null);
            if (res == null || res.statusCode() / 100 != 2) return out;
            JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
            if (items == null) return out;
            for (var el : items) {
                JsonObject p = el.getAsJsonObject();
                if (p == null || p.isJsonNull()) continue;
                String owner = "";
                JsonObject o = p.getAsJsonObject("owner");
                if (o != null) owner = str(o, "display_name");
                int count = 0;
                JsonObject tracks = p.getAsJsonObject("tracks");
                if (tracks != null && tracks.has("total")) count = tracks.get("total").getAsInt();
                out.add(new SpotifyPlaylist(str(p, "id"), str(p, "name"), count, owner));
            }
            return out;
        });
    }

    public CompletableFuture<Boolean> addToPlaylist(String playlistId, String trackUri) {
        return CompletableFuture.supplyAsync(() -> ok(send("POST",
                "/playlists/" + playlistId + "/tracks",
                "{\"uris\":[\"" + trackUri + "\"]}")));
    }

    public CompletableFuture<Boolean> addToQueue(String trackUri) {
        return CompletableFuture.supplyAsync(() -> ok(send("POST",
                "/me/player/queue?uri=" + enc(trackUri), "")));
    }

    public CompletableFuture<Boolean> next() {
        return CompletableFuture.supplyAsync(() -> ok(send("POST", "/me/player/next", "")));
    }

    public CompletableFuture<Boolean> previous() {
        return CompletableFuture.supplyAsync(() -> ok(send("POST", "/me/player/previous", "")));
    }

    /** Start playing a playlist (or any context URI) on the active device. */
    public CompletableFuture<Boolean> playContext(String contextUri) {
        return CompletableFuture.supplyAsync(() -> ok(send("PUT", "/me/player/play",
                "{\"context_uri\":\"" + contextUri + "\"}")));
    }

    public CompletableFuture<Boolean> setSaved(String trackId, boolean saved) {
        return CompletableFuture.supplyAsync(() ->
                ok(send(saved ? "PUT" : "DELETE", "/me/tracks?ids=" + enc(trackId), "")));
    }

    // ---- internals ----

    private boolean isSaved(String trackId) {
        HttpResponse<String> res = send("GET", "/me/tracks/contains?ids=" + enc(trackId), null);
        if (res == null || res.statusCode() / 100 != 2) return false;
        try {
            JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
            return arr.size() > 0 && arr.get(0).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /** Sends a request with a fresh token; retries once after a refresh on 401. */
    private HttpResponse<String> send(String method, String path, String body) {
        HttpResponse<String> res = doSend(method, path, body);
        if (res != null && res.statusCode() == 401) {
            res = doSend(method, path, body); // freshAccessToken refreshed for the retry
        }
        return res;
    }

    private HttpResponse<String> doSend(String method, String path, String body) {
        String token = auth.freshAccessToken();
        if (token == null) return null;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(API + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json");
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
            b.method(method, pub);
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 403) {
                LOG.warn("[MusicOverlay] Spotify refused {} {} (Premium required for playback control?)", method, path);
            } else if (res.statusCode() == 404 && path.startsWith("/me/player")) {
                LOG.warn("[MusicOverlay] no active Spotify device - open Spotify on any device first.");
            }
            return res;
        } catch (Exception e) {
            LOG.warn("[MusicOverlay] Spotify request {} {} failed: {}", method, path, e.getMessage());
            return null;
        }
    }

    private static boolean ok(HttpResponse<String> res) {
        return res != null && res.statusCode() / 100 == 2;
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
