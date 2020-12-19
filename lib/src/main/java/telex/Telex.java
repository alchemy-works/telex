package telex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import telex.support.MultiPartBodyPublisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Simple Telegram API wrapper
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

    public @NotNull CompletableFuture<String> sendAsync(@NotNull String method, @NotNull Map<String, ?> payload) {
        Objects.requireNonNull(payload, "payload must be not null");
        var endpoint = this.getEndpoint(method);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .build();
        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public @NotNull String send(@NotNull String method, @NotNull Map<String, ?> payload) {
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
        Objects.requireNonNull(payload, "payload must be not null");
        var publisher = new MultiPartBodyPublisher();
        payload.forEach((name, value) -> {
            var addResourceToPublisher = (Consumer<Resource>) resource -> {
                var filePart = new MultiPartBodyPublisher.FilePartSpec();
                filePart.setFilename(resource.getFilename());
                filePart.setContentType(resource.getContentType());
                filePart.setStream(resource.getInputStream());
                publisher.addPart(name,filePart );
            };
            if (value instanceof Path) {
                addResourceToPublisher.accept(Resource.of((Path) value));
            } else if (value instanceof File) {
                addResourceToPublisher.accept(Resource.of((File) value));
            } else if (value instanceof Resource) {
                addResourceToPublisher.accept((Resource) value);
            } else {
                publisher.addPart(name, String.valueOf(value));
            }
        });
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "multipart/form-data")
                .POST(publisher.build())
                .build();
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

    /**
     * File resource
     */
    public interface Resource {

        InputStream getInputStream();

        @Nullable
        default String getFilename() {
            return null;
        }

        @Nullable
        default String getContentType() {
            return "application/octet-stream";
        }

        static Resource of(Path path) {
            Objects.requireNonNull(path, "path must be not null");
            return new Resource() {
                @Override
                public InputStream getInputStream() {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }

                @Override
                public @Nullable String getFilename() {
                    return path.getFileName().toString();
                }

                @Override
                public @Nullable String getContentType() {
                    try {
                        return Files.probeContentType(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            };
        }

        static Resource of(@NotNull File file) {
            Objects.requireNonNull(file, "file must be not null");
            return of(file.toPath());
        }

        static Resource of(@NotNull InputStream inputStream) {
            Objects.requireNonNull(inputStream, "inputStream must be not null");
            return new Resource() {
                @Override
                public InputStream getInputStream() {
                    return inputStream;
                }

                @Override
                public String getFilename() {
                    return "";
                }
            };
        }
    }
}
