package telex.support;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MultiPartBodyPublisher {

    private final static Charset utf8 = StandardCharsets.UTF_8;

    private final String boundary = UUID.randomUUID().toString();

    private final List<Object> parts = new ArrayList<>();

    public void addPart(@NotNull String name, @NotNull String value) {
        String part = "--" + this.boundary + "\r\n" +
                      "Content-Disposition: form-data; name=" + name + "\r\n" +
                      "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                      value + "\r\n";
        this.parts.add(part.getBytes(utf8));
    }

    public void addPart(@NotNull String name, @NotNull FilePartSpec filePart) {
        String partHeader = "--" + this.boundary + "\r\n" +
                            "Content-Disposition: form-data; name=" + name + "; filename=" + filePart.getFilename() + "\r\n" +
                            "Content-Type: " + filePart.getContentType() + "\r\n\r\n";
        this.parts.add(partHeader.getBytes(utf8));
        this.parts.add(new ByteArrayIterator(filePart.getStream()));
    }

    public HttpRequest.BodyPublisher build() {
        if (this.parts.size() == 0) {
            throw new IllegalStateException("Must have at least one part to build multipart message.");
        }
        this.parts.add(("--" + boundary + "--").getBytes(utf8));
        return HttpRequest.BodyPublishers.ofByteArrays(this::getPartByteIterator);
    }

    private Iterator<byte[]> getPartByteIterator() {
        var partIterator = this.parts.iterator();
        return new Iterator<>() {

            private Iterator<byte[]> byteArrayIterator = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                return this.byteArrayIterator.hasNext() || partIterator.hasNext();
            }

            @Override
            public byte[] next() {
                if (this.byteArrayIterator.hasNext()) {
                    return this.byteArrayIterator.next();
                }
                var next = partIterator.next();
                if (next instanceof byte[]) {
                    return (byte[]) next;
                } else if (next instanceof ByteArrayIterator) {
                    this.byteArrayIterator = (ByteArrayIterator) next;
                    return this.next();
                } else {
                    throw new IllegalStateException("Should not reach here");
                }
            }
        };
    }

    public static class FilePartSpec {

        String filename;

        String contentType;

        InputStream stream;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public InputStream getStream() {
            return stream;
        }

        public void setStream(InputStream stream) {
            this.stream = stream;
        }
    }

}
