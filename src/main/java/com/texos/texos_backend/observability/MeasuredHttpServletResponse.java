package com.texos.texos_backend.observability;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class MeasuredHttpServletResponse extends HttpServletResponseWrapper {

    private CountingServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean outputStreamObtained;
    private boolean writerObtained;
    private long bytesWritten;
    private long outputWriteNanos;

    MeasuredHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writerObtained) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        outputStreamObtained = true;
        return countingOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStreamObtained) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (writer == null) {
            writerObtained = true;
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.ISO_8859_1
                    : Charset.forName(encoding);
            writer = new PrintWriter(new OutputStreamWriter(
                    countingOutputStream(), charset));
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        if (outputStream != null) {
            outputStream.flush();
        }
        super.flushBuffer();
    }

    long getBytesWritten() {
        return bytesWritten;
    }

    long getOutputWriteNanos() {
        return outputWriteNanos;
    }

    void finishMeasurement() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    private CountingServletOutputStream countingOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new CountingServletOutputStream(super.getOutputStream());
        }
        return outputStream;
    }

    private final class CountingServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;

        private CountingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int value) throws IOException {
            measureWrite(() -> delegate.write(value));
            bytesWritten++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            measureWrite(() -> delegate.write(bytes, offset, length));
            bytesWritten += length;
        }

        @Override
        public void flush() throws IOException {
            measureWrite(delegate::flush);
        }

        @Override
        public void close() throws IOException {
            measureWrite(delegate::close);
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        private void measureWrite(IoOperation operation) throws IOException {
            long startedAtNanos = System.nanoTime();
            try {
                operation.run();
            } finally {
                outputWriteNanos += System.nanoTime() - startedAtNanos;
            }
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws IOException;
    }
}
