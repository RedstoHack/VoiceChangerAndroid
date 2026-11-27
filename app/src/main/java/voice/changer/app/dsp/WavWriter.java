package voice.changer.app.dsp;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Потоковая запись WAV (PCM 16 бит). */
public final class WavWriter implements Closeable {
    private final RandomAccessFile raf;
    private final int channels, rate;
    private long dataBytes;
    private ByteBuffer bb = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);

    public WavWriter(File f, int channels, int rate) throws IOException {
        this.raf = new RandomAccessFile(f, "rw");
        this.raf.setLength(0);
        this.channels = channels; this.rate = rate;
        raf.write(new byte[44]);
    }

    public void write(short[] pcm, int count) throws IOException {
        int i = 0;
        while (i < count) {
            bb.clear();
            int c = Math.min(count - i, bb.capacity() / 2);
            for (int k = 0; k < c; k++) bb.putShort(pcm[i + k]);
            raf.write(bb.array(), 0, c * 2);
            dataBytes += c * 2L;
            i += c;
        }
    }

    @Override public void close() throws IOException {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt((int) (36 + dataBytes)).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        h.put("data".getBytes()).putInt((int) dataBytes);
        raf.seek(0);
        raf.write(h.array());
        raf.close();
    }
}
