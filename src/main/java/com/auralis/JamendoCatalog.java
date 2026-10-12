package com.auralis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class JamendoCatalog {

    private static final String API_URL = "https://api.jamendo.com/v3.0/tracks/";

    private static final int PAGE_SIZE = 200;

    private final String clientId;
    private final HttpClient httpClient;

    // Creates the Jamendo API client and checks that the API key exists.
    // Initializes the Jamendo API client using the environment or local .env file.
    public JamendoCatalog() {
        String configuredClientId = System.getenv("JAMENDO_CLIENT_ID");

        if (configuredClientId == null || configuredClientId.isBlank()) {
            configuredClientId = Dotenv.configure()
                    .ignoreIfMissing()
                    .load()
                    .get("JAMENDO_CLIENT_ID");
        }

        if (configuredClientId == null || configuredClientId.isBlank()) {
            throw new IllegalStateException(
                    "JAMENDO_CLIENT_ID is missing. Add it to your .env file.");
        }

        this.clientId = configuredClientId;

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // Fetches tracks from Jamendo asynchronously without blocking the JavaFX
    // interface.
    public CompletableFuture<List<Song>> fetchTracks(int offset, String searchQuery) {
        StringBuilder url = new StringBuilder(API_URL)
                .append("?client_id=").append(encode(clientId))
                .append("&format=json")
                .append("&limit=").append(PAGE_SIZE)
                .append("&offset=").append(Math.max(0, offset))
                .append("&imagesize=500")
                .append("&audioformat=mp32");

        if (searchQuery != null && !searchQuery.isBlank()) {
            url.append("&search=").append(encode(searchQuery.trim()));
        } else {
            // Popular tracks first, so the landing page shows well-known songs.
            url.append("&order=popularity_total");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "Jamendo returned HTTP " + response.statusCode());
                    }

                    List<Song> tracks = parseTracks(response.body());
                    System.out.println(
                            "Jamendo returned " + tracks.size()
                                    + " usable tracks at offset " + offset);

                    return tracks;
                });
    }

    // Converts the Jamendo JSON response into Song objects.
    private List<Song> parseTracks(String json) {

        JsonObject response;

        try {
            response = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new RuntimeException(
                    "Jamendo returned invalid JSON: " + shorten(json),
                    exception);
        }

        JsonObject headers = response.has("headers")
                && response.get("headers").isJsonObject()
                        ? response.getAsJsonObject("headers")
                        : null;

        if (headers == null
                || !"success".equalsIgnoreCase(
                        readString(headers, "status"))) {

            String message = headers == null
                    ? "The API response did not contain valid headers."
                    : readString(headers, "error_message");

            if (message.isBlank()) {
                message = "Unknown Jamendo API error.";
            }

            throw new RuntimeException("Jamendo API error: " + message);
        }

        JsonArray results = response.has("results")
                && response.get("results").isJsonArray()
                        ? response.getAsJsonArray("results")
                        : new JsonArray();

        // Keeps track IDs unique while preserving their original order.
        Map<String, Song> uniqueTracks = new LinkedHashMap<>();

        for (JsonElement element : results) {

            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject track = element.getAsJsonObject();

            String id = readString(track, "id");
            String title = readString(track, "name");
            String artist = readString(track, "artist_name");
            String cover = readString(track, "image");
            String audio = readString(track, "audio");
            int duration = readInt(track, "duration");

            if (cover.isBlank()) {
                cover = readString(track, "album_image");
            }

            // Only accepts tracks with an ID, title, and audio stream.
            if (id.isBlank() || title.isBlank() || audio.isBlank()) {
                continue;
            }

            Song song = new Song(
                    id,
                    title,
                    artist.isBlank() ? "Unknown artist" : artist,
                    cover,
                    audio,
                    duration);

            uniqueTracks.putIfAbsent(id, song);
        }

        return new ArrayList<>(uniqueTracks.values());
    }

    // Reads a text field safely when it is missing or null.
    private String readString(JsonObject object, String key) {

        if (object == null || !object.has(key)) {
            return "";
        }

        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull()) {
            return "";
        }

        try {
            return element.getAsString().trim();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    // Reads a duration safely and returns zero if it is invalid.
    private int readInt(JsonObject object, String key) {

        try {
            return Integer.parseInt(readString(object, key));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    // Encodes URL parameters so spaces and special characters are valid.
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Limits long error responses so useful details fit in the terminal.
    private String shorten(String value) {

        if (value == null || value.isBlank()) {
            return "No response details provided.";
        }

        String cleaned = value.replaceAll("\\s+", " ").trim();

        return cleaned.length() > 300
                ? cleaned.substring(0, 300) + "..."
                : cleaned;
    }
}