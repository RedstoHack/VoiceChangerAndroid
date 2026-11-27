package voice.changer.app.dsp;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Чтение WAV (PCM 8/16/24/32, float32) без MediaCodec. Нужен и для результата TTS-движков. */
public final class WavSource implements PcmStream {
    private final RandomAccessFile raf;
    private final int rate, inCh, outCh, bits, tag;
    private long remaining;
    private final byte[] raw = new byte[1 << 16];

    private WavSource(RandomAccessFile raf, int rate, int inCh, int outCh, int bits, int tag, long bytes) {
        this.raf = raf; this.rate = rate; this.inCh = inCh; this.outCh = outCh;
        this.bits = bits; this.tag = tag; this.remaining = bytes;
    }

    /** Возвращает null, если файл — не поддерживаемый WAV. */
    public static WavSource open(File f, int outCh) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] h = new byte[12];
            if (raf.read(h) < 12 || h[0] != 'R' || h[1] != 'I' || h[2] != 'F' || h[3] != 'F' || h[8] != 'W') {
                raf.close(); return null;
            }
            int tag = 0, ch = 0, rate = 0, bits = 0;
            byte[] ch8 = new byte[8];
            while (raf.read(ch8) == 8) {
                ByteBuffer b = ByteBuffer.wrap(ch8).order(ByteOrder.LITTLE_ENDIAN);
                String id = new String(ch8, 0, 4, "US-ASCII");
                long size = b.getInt(4) & 0xFFFFFFFFL;
                if (id.equals("fmt ")) {
                    byte[] fm = new byte[(int) Math.min(size, 40)];
                    raf.readFully(fm);
                    ByteBuffer fb = ByteBuffer.wrap(fm).order(ByteOrder.LITTLE_ENDIAN);
                    tag = fb.getShort(0) & 0xFFFF; ch = fb.getShort(2); rate = fb.getInt(4); bits = fb.getShort(14);
                    if (tag == 0xFFFE && fm.length >= 26) tag = fb.getShort(24) & 0xFFFF;
                    raf.seek(raf.getFilePointer() + (size - fm.length) + (size & 1));
                } else if (id.equals("data")) {
                    long left = raf.length() - raf.getFilePointer();
                    if (size == 0 || size > left) size = left;
                    boolean ok = (tag == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32)) || (tag == 3 && bits == 32);
                    if (!ok || ch < 1 || rate < 1000) { raf.close(); return null; }
                    return new WavSource(raf, rate, ch, outCh, bits, tag, size);
                } else {
                    raf.seek(raf.getFilePointer() + size + (size & 1));
                }
            }
        } catch (IOException | RuntimeException e) {
            raf.close(); throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        raf.close();
        return null;
    }

    @Override public int rate() { return rate; }

    @Override public int read(float[] out, int off, int frames) throws IOException {
        int bps = bits / 8, frameBytes = bps * inCh;
        if (remaining < frameBytes) return -1;
        int want = (int) Math.min((long) frames, Math.min(remaining / frameBytes, raw.length / frameBytes));
        int got = raf.read(raw, 0, want * frameBytes) / frameBytes;
        if (got <= 0) { remaining = 0; return -1; }
        remaining -= (long) got * frameBytes;
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < got; i++) {
            float l = 0, r = 0, sum = 0;
            for (int c = 0; c < inCh; c++) {
                int p = (i * inCh + c) * bps;
                float v;
                if (tag == 3) v = b.getFloat(p);
                else if (bits == 16) v = b.getShort(p) / 32768f;
                else if (bits == 8) v = ((raw[p] & 0xFF) - 128) / 128f;
                else if (bits == 24) v = (((raw[p + 2] << 16) | ((raw[p + 1] & 0xFF) << 8) | (raw[p] & 0xFF))) / 8388608f;
                else v = b.getInt(p) / 2147483648f;
                if (c == 0) l = v;
                if (c == 1) r = v;
                sum += v;
            }
            if (outCh == 1) out[off + i] = sum / inCh;
            else { out[(off + i) * 2] = l; out[(off + i) * 2 + 1] = inCh == 1 ? l : r; }
        }
        return got;
    }

    @Override public void close() throws IOException { raf.close(); }
}
