package telex.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public class MultiPartBodyPublisher {

    private final List<PartsSpec> partsSpecList = new ArrayList<>();
    private final String boundary = UUID.randomUUID().toString();

    public void addPart(String name, String value) {
        var part = new PartsSpec();
        part.type = PartsSpec.Type.STRING;
        part.name = name;
        part.value = value;
        this.partsSpecList.add(part);
    }

    public void addPart(String name, Supplier<InputStream> stream, String filename, String contentType) {
        var part = new PartsSpec();
        part.type = PartsSpec.Type.STREAM;
        part.name = name;
        part.stream = stream;
        part.filename = filename;
        part.contentType = contentType;
        this.partsSpecList.add(part);
    }

    public HttpRequest.BodyPublisher build() {
        if (this.partsSpecList.size() == 0) {
            throw new IllegalStateException("Must have at least one part to build multipart message.");
        }
        this.addFinalBoundaryPart();
        return HttpRequest.BodyPublishers.ofByteArrays(PartBytesIterator::new);
    }

    private void addFinalBoundaryPart() {
        var newPart = new PartsSpec();
        newPart.type = PartsSpec.Type.FINAL_BOUNDARY;
        newPart.value = "--" + boundary + "--";
        this.partsSpecList.add(newPart);
    }

    class PartBytesIterator implements Iterator<byte[]> {

        private final byte[] streamBuffer = new byte[8192];

        private final Iterator<PartsSpec> partsIterator = MultiPartBodyPublisher.this.partsSpecList.listIterator();

        private PartsSpec currentPart;
        private InputStream currentStream;

        @Override
        public boolean hasNext() {
            return this.partsIterator.hasNext() && this.currentStream != null;
        }

        @Override
        public byte[] next() {
            if (this.currentStream == null) {
                this.currentPart = this.partsIterator.next();
            }
            try {
                return this.computeNext();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private byte[] computeNext() throws IOException {
            var utf8 = StandardCharsets.UTF_8;
            var boundary = MultiPartBodyPublisher.this.boundary;
            Objects.requireNonNull(this.currentPart.type);
            switch (this.currentPart.type) {
                case STRING:
                    String part =
                            "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=" + this.currentPart.name + "\r\n" +
                            "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                            this.currentPart.value + "\r\n";
                    return part.getBytes(utf8);
                case FINAL_BOUNDARY:
                    return this.currentPart.value.getBytes(utf8);
                case STREAM:
                    if (this.currentStream == null) {
                        this.currentStream = this.currentPart.stream.get();
                        String partHeader =
                                "--" + boundary + "\r\n" +
                                "Content-Disposition: form-data; name=" + this.currentPart.name + "; filename=" + this.currentPart.filename + "\r\n" +
                                "Content-Type: " + this.currentPart.contentType + "\r\n\r\n";
                        return partHeader.getBytes(utf8);
                    } else {
                        int read = this.currentStream.read(this.streamBuffer);
                        if (read > 0) {
                            var actualBytes = new byte[read];
                            System.arraycopy(this.streamBuffer, 0, actualBytes, 0, read);
                            return actualBytes;
                        } else {
                            this.currentStream.close();
                            this.currentStream = null;
                            return "\r\n".getBytes(utf8);
                        }
                    }
            }
            throw new IllegalStateException("Should not reach here");
        }
    }

    static class PartsSpec {

        public enum Type {
            STRING, STREAM, FINAL_BOUNDARY
        }

        Type type;
        String name;
        String value;
        Supplier<InputStream> stream;
        String filename;
        String contentType;
    }
}
