package org.jianzhao.telex;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
        payload.forEach((key, value) -> {
            if (value instanceof Path) {
                publisher.addPart(key, (Path) value);
            } else if (value instanceof Supplier) {
                publisher.addPart(key, assureInputStreamSupplier((Supplier<?>) value),
                        UUID.randomUUID().toString(), "application/octet-stream");
            } else if (value instanceof String) {
                publisher.addPart(key, (String) value);
            } else {
                publisher.addPart(key, String.valueOf(value));
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
     * Assure input stream supplier
     *
     * @param supplier unknown supplier
     * @return input stream supplier
     */
    private static Supplier<InputStream> assureInputStreamSupplier(Supplier<?> supplier) {
        return () -> {
            Object o = supplier.get();
            if (!(o instanceof InputStream)) {
                throw new IllegalStateException("Supplier must supplies inputStream");
            }
            return (InputStream) o;
        };
    }

    static class MultiPartBodyPublisher {

        private final List<PartsSpecification> partsSpecificationList = new ArrayList<>();
        private final String boundary = UUID.randomUUID().toString();

        public HttpRequest.BodyPublisher build() {
            if (partsSpecificationList.size() == 0) {
                throw new IllegalStateException("Must have at least one part to build multipart message.");
            }
            addFinalBoundaryPart();
            return HttpRequest.BodyPublishers.ofByteArrays(PartsIterator::new);
        }

        public String getBoundary() {
            return boundary;
        }

        public MultiPartBodyPublisher addPart(String name, String value) {
            PartsSpecification newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.STRING;
            newPart.name = name;
            newPart.value = value;
            partsSpecificationList.add(newPart);
            return this;
        }

        public MultiPartBodyPublisher addPart(String name, Path value) {
            var newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.FILE;
            newPart.name = name;
            newPart.path = value;
            partsSpecificationList.add(newPart);
            return this;
        }

        public MultiPartBodyPublisher addPart(String name, Supplier<InputStream> value, String filename, String contentType) {
            var newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.STREAM;
            newPart.name = name;
            newPart.stream = value;
            newPart.filename = filename;
            newPart.contentType = contentType;
            partsSpecificationList.add(newPart);
            return this;
        }

        private void addFinalBoundaryPart() {
            var newPart = new PartsSpecification();
            newPart.type = PartsSpecification.TYPE.FINAL_BOUNDARY;
            newPart.value = "--" + boundary + "--";
            partsSpecificationList.add(newPart);
        }

        static class PartsSpecification {

            public enum TYPE {
                STRING, FILE, STREAM, FINAL_BOUNDARY
            }

            PartsSpecification.TYPE type;
            String name;
            String value;
            Path path;
            Supplier<InputStream> stream;
            String filename;
            String contentType;

        }

        class PartsIterator implements Iterator<byte[]> {

            private final Iterator<PartsSpecification> iter;
            private InputStream currentFileInput;

            private boolean done;
            private byte[] next;

            PartsIterator() {
                iter = partsSpecificationList.iterator();
            }

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                try {
                    next = computeNext();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                if (next == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) throw new NoSuchElementException();
                byte[] res = next;
                next = null;
                return res;
            }

            private byte[] computeNext() throws IOException {
                if (currentFileInput == null) {
                    if (!iter.hasNext()) return null;
                    PartsSpecification nextPart = iter.next();
                    if (PartsSpecification.TYPE.STRING.equals(nextPart.type)) {
                        String part =
                                "--" + boundary + "\r\n" +
                                "Content-Disposition: form-data; name=" + nextPart.name + "\r\n" +
                                "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                                nextPart.value + "\r\n";
                        return part.getBytes(StandardCharsets.UTF_8);
                    }
                    if (PartsSpecification.TYPE.FINAL_BOUNDARY.equals(nextPart.type)) {
                        return nextPart.value.getBytes(StandardCharsets.UTF_8);
                    }
                    String filename;
                    String contentType;
                    if (PartsSpecification.TYPE.FILE.equals(nextPart.type)) {
                        Path path = nextPart.path;
                        filename = path.getFileName().toString();
                        contentType = Files.probeContentType(path);
                        if (contentType == null) contentType = "application/octet-stream";
                        currentFileInput = Files.newInputStream(path);
                    } else {
                        filename = nextPart.filename;
                        contentType = nextPart.contentType;
                        if (contentType == null) contentType = "application/octet-stream";
                        currentFileInput = nextPart.stream.get();
                    }
                    String partHeader =
                            "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=" + nextPart.name + "; filename=" + filename + "\r\n" +
                            "Content-Type: " + contentType + "\r\n\r\n";
                    return partHeader.getBytes(StandardCharsets.UTF_8);
                } else {
                    byte[] buf = new byte[8192];
                    int r = currentFileInput.read(buf);
                    if (r > 0) {
                        byte[] actualBytes = new byte[r];
                        System.arraycopy(buf, 0, actualBytes, 0, r);
                        return actualBytes;
                    } else {
                        currentFileInput.close();
                        currentFileInput = null;
                        return "\r\n".getBytes(StandardCharsets.UTF_8);
                    }
                }
            }
        }
    }
}
