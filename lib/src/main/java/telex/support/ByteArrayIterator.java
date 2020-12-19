package telex.support;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ByteArrayIterator implements Iterator<byte[]> {

    private final InputStream inputStream;

    private final byte[] buffer = new byte[8192];

    private boolean drained = false;

    public ByteArrayIterator(@NotNull InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream must be not null");
        this.inputStream = inputStream;
    }

    @Override
    public boolean hasNext() {
        return !this.drained;
    }

    @Override
    public byte[] next() {
        if (this.drained) {
            throw new NoSuchElementException("stream is drained");
        }
        int read;
        try {
            read = this.inputStream.read(this.buffer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (read > 0) {
            var actualBytes = new byte[read];
            System.arraycopy(this.buffer, 0, actualBytes, 0, read);
            return actualBytes;
        } else {
            this.drained = true;
            try {
                this.inputStream.close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return new byte[0];
        }
    }
}
