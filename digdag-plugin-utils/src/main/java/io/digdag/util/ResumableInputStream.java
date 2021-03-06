package io.digdag.util;

import java.io.IOException;
import java.io.InputStream;

public class ResumableInputStream
        extends InputStream
{
    public interface Reopener
    {
        public InputStream reopen(long offset, Exception closedCause) throws IOException;
    }

    private final Reopener reopener;
    protected InputStream in;
    private long offset;

    public ResumableInputStream(InputStream initialInputStream, Reopener reopener)
    {
        this.reopener = reopener;
        this.in = initialInputStream;
        this.offset = 0L;
    }

    public ResumableInputStream(Reopener reopener) throws IOException
    {
        this(reopener.reopen(0, null), reopener);
    }

    private synchronized void reopen(Exception closedCause) throws IOException
    {
        if (in != null) {
            in.close();
            in = null;
        }
        in = reopener.reopen(offset, closedCause);
    }

    @Override
    public int read() throws IOException
    {
        while (true) {
            try {
                int v = in.read();
                if (v >= 0) {
                    offset += 1;
                }
                return v;
            }
            catch (IOException | RuntimeException ex) {
                reopen(ex);
            }
        }
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        while (true) {
            try {
                int r = in.read(b);
                if (r > 0) {
                    offset += r;
                }
                return r;
            }
            catch (IOException | RuntimeException ex) {
                reopen(ex);
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        while (true) {
            try {
                int r = in.read(b, off, len);
                if (r > 0) {
                    offset += r;
                }
                return r;
            }
            catch (IOException | RuntimeException ex) {
                reopen(ex);
            }
        }
    }

    @Override
    public long skip(long n) throws IOException
    {
        while (true) {
            try {
                long r = in.skip(n);
                if (r > 0) {
                    offset += r;
                }
                return r;
            }
            catch (IOException | RuntimeException ex) {
                reopen(ex);
            }
        }
    }

    @Override
    public int available() throws IOException
    {
        return in.available();
    }

    @Override
    public void close() throws IOException
    {
        in.close();
    }

    // mark is not supported. If necessary, wrap using a BufferedInputStream.
}
