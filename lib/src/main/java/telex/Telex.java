package telex;

import org.jetbrains.annotations.NotNull;
import telex.support.MultiPartBodyPublisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Simple Telegram API wrapper
 *
 * @see <a href="https://core.telegram.org/bots/api">Telegram Bot API</a>
 */
public class Telex {

    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/%s";
    private static final String TELEGRAM_FILE_API = "https://api.telegram.org/file/bot%s/%s";

    private final String token;
    private final HttpClient httpClient;

    /**
     * Use default HttpClient
     *
     * @param token bot token
     */
    public Telex(@NotNull String token) {
        this(token, HttpClient.newHttpClient());
    }

    /**
     * Use a custom HTTP client
     *
     * @param token      bot token
     * @param httpClient http client
     */
    public Telex(@NotNull String token, @NotNull HttpClient httpClient) {
        Objects.requireNonNull(token, "token must be not null");
        Objects.requireNonNull(httpClient, "httpClient must be not null");
        this.token = token;
        this.httpClient = httpClient;
    }

    public @NotNull CompletableFuture<String> callAsync(@NotNull String method, @NotNull Map<String, ?> payload) {
        var endpoint = this.getEndpoint(method);
        var request = createRequest(endpoint, payload);
        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public @NotNull String call(@NotNull String method, @NotNull Map<String, ?> payload) {
        var endpoint = this.getEndpoint(method);
        var request = createRequest(endpoint, payload);
        try {
            return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof IOException) {
                throw new UncheckedIOException((IOException) ex);
            }
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param endpoint Telegram endpoint
     * @param payload  Request payload
     * @return Request
     */
    public static HttpRequest createRequest(@NotNull String endpoint, @NotNull Map<String, ?> payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "multipart/form-data")
                .POST(toBodyPublisher(payload))
                .build();
    }

    /**
     * Build BodyPublisher from payload
     *
     * @param payload payload
     * @return BodyPublisher
     */
    public static HttpRequest.BodyPublisher toBodyPublisher(@NotNull Map<String, ?> payload) {
        Objects.requireNonNull(payload, "payload must be not null");
        var publisher = new MultiPartBodyPublisher();
        payload.forEach((name, value) -> {
            if (value instanceof Path) {
                publisher.addPart(name, MultiPartBodyPublisher.FilePartSpec.from((Path) value));
            } else if (value instanceof File) {
                publisher.addPart(name, MultiPartBodyPublisher.FilePartSpec.from((File) value));
            } else if (value instanceof Supplier<?>) {
                publisher.addPart(name, toFilePartSpec(name, (Supplier<?>) value));
            } else if (value instanceof MultiPartBodyPublisher.FilePartSpec) {
                publisher.addPart(name, (MultiPartBodyPublisher.FilePartSpec) value);
            } else {
                publisher.addPart(name, String.valueOf(value));
            }
        });
        return publisher.build();
    }

    /**
     * Convert input stream supplier to file part
     *
     * @param name     form item name
     * @param supplier input stream supplier
     * @return file part
     */
    private static MultiPartBodyPublisher.FilePartSpec toFilePartSpec(String name, Supplier<?> supplier) {
        return new MultiPartBodyPublisher.FilePartSpec() {

            @Override
            public String getFilename() {
                return name;
            }

            @Override
            public InputStream getInputStream() {
                Object o = supplier.get();
                if (!(o instanceof InputStream)) {
                    throw new IllegalStateException("Supplier must supplies inputStream");
                }
                return (InputStream) o;
            }
        };
    }

    /**
     * @param method Telegram method
     * @return API endpoint
     */
    public @NotNull String getEndpoint(@NotNull String method) {
        Objects.requireNonNull(method, "method must be not null");
        return String.format(TELEGRAM_API, this.token, method);
    }

    /**
     * @param filePath Telegram method
     * @return Telegram file url
     */
    public @NotNull String getFileUrl(@NotNull String filePath) {
        Objects.requireNonNull(filePath, "filePath must be not null");
        return String.format(TELEGRAM_FILE_API, this.token, filePath);
    }
}
