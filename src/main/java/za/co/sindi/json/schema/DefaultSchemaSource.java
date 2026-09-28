package za.co.sindi.json.schema;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Default {@link SchemaSource} backed by {@link HttpClient} and the file system. */
final class DefaultSchemaSource implements SchemaSource {

    static final DefaultSchemaSource INSTANCE = new DefaultSchemaSource();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private DefaultSchemaSource() {
    }

    @Override
    public String fetch(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IOException("Schema URI must be absolute: " + uri);
        }
        return switch (scheme) {
            case "http", "https" -> fetchHttp(uri);
            case "file" -> Files.readString(Path.of(uri), StandardCharsets.UTF_8);
            default -> throw new IOException("Unsupported schema URI scheme '" + scheme + "' for " + uri);
        };
    }

    private String fetchHttp(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/schema+json, application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " while fetching " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }
}
