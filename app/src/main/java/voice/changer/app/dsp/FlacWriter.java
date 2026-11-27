package voice.changer.app.dsp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Кодер FLAC (16 бит) на чистой Java: фиксированные предикторы 0–4 + код Райса, запасной вариант — verbatim.
 * Если левый и правый каналы совпадают, пишет моно (в два раза меньше).
 */
public final class FlacWriter {
    private FlacWriter() {}

    private static final int BS = 4096;

    /** Бит-писатель с накоплением в массив байт. */
    private static final class Bits {
        byte[] buf = new byte[1 << 14];
        int len;       // полных байт
        long acc;      // накопленные биты
        int nacc;      // сколько бит в acc

        void put(long v, int n) {
            while (n > 0) {
                int take = Math.min(n, 32);
                long part = (v >>> (n - take)) & ((1L << take) - 1);
                acc = (acc << take) | part;
                nacc += take;
                n -= take;
                while (nacc >= 8) {
                    ensure();
                    buf[len++] = (byte) (acc >>> (nacc - 8));
                    nacc -= 8;
                }
                acc &= (1L << nacc) - 1;
            }
        }
        void zeros(int n) { while (n > 0) { int t = Math.min(n, 32); put(0, t); n -= t; } }
        void align() { if (nacc > 0) put(0, 8 - nacc); }
        private void ensure() { if (len == buf.length) buf = Arrays.copyOf(buf, buf.length * 2); }
    }

    private static int crc8(byte[] b, int len) {
        int c = 0;
        for (int i = 0; i < len; i++) {
            c ^= b[i] & 0xFF;
            for (int k = 0; k < 8; k++) c = (c & 0x80) != 0 ? ((c << 1) ^ 0x07) & 0xFF : (c << 1) & 0xFF;
        }
        return c;
    }

    private static int crc16(byte[] b, int len) {
        int c = 0;
        for (int i = 0; i < len; i++) {
            c ^= (b[i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x8005) & 0xFFFF : (c << 1) & 0xFFFF;
        }
        return c;
    }

    private static void utf8Number(Bits b, long n) {
        if (n < 0x80) { b.put(n, 8); return; }
        int extra = n < 0x800 ? 1 : n < 0x10000 ? 2 : n < 0x200000 ? 3 : n < 0x4000000 ? 4 : 5;
        b.put((0xFF00 >> (extra + 1)) & 0xFF | (n >>> (6 * extra)), 8);
        for (int i = extra - 1; i >= 0; i--) b.put(0x80 | ((n >>> (6 * i)) & 0x3F), 8);
    }

    /** st — interleaved стерео short, frames — число кадров. */
    public static void write(short[] st, int frames, int rate, OutputStream out) throws IOException {
        boolean mono = true;
        for (int i = 0; i < frames; i++) if (st[2 * i] != st[2 * i + 1]) { mono = false; break; }
        int ch = mono ? 1 : 2;

        Bits h = new Bits();
        h.put(0x664C6143L, 32);                 // "fLaC"
        h.put(1, 1); h.put(0, 7); h.put(34, 24); // последний блок метаданных: STREAMINFO
        h.put(BS, 16); h.put(BS, 16);
        h.put(0, 24); h.put(0, 24);              // размеры кадров неизвестны
        h.put(rate, 20); h.put(ch - 1, 3); h.put(15, 5);
        h.put(frames, 36);
        h.zeros(128);                            // MD5 не считаем
        out.write(h.buf, 0, h.len);

        int[][] x = new int[ch][BS];
        long frameNo = 0;
        for (int pos = 0; pos < frames; pos += BS, frameNo++) {
            int n = Math.min(BS, frames - pos);
            for (int c = 0; c < ch; c++)
                for (int i = 0; i < n; i++) x[c][i] = st[2 * (pos + i) + c];

            Bits f = new Bits();
            f.put(0xFFF8, 16);
            int bsCode = n == BS ? 12 : (n - 1 < 256 ? 6 : 7);
            int rateCode = rate == 44100 ? 9 : 0;
            f.put(bsCode, 4); f.put(rateCode, 4);
            f.put(ch == 1 ? 0 : 1, 4);           // моно / два независимых канала
            f.put(4, 3); f.put(0, 1);            // 16 бит
            utf8Number(f, frameNo);
            if (bsCode == 6) f.put(n - 1, 8); else if (bsCode == 7) f.put(n - 1, 16);
            f.put(crc8(f.buf, f.len), 8);

            for (int c = 0; c < ch; c++) subframe(f, x[c], n);
            f.align();
            int crc = crc16(f.buf, f.len);
            f.put(crc, 16);
            out.write(f.buf, 0, f.len);
        }
    }

    private static void subframe(Bits f, int[] x, int n) {
        int bestOrder = -1, bestBits = 16 * n;       // verbatim как запасной вариант
        int bestK = 0;
        int[] res = new int[n];
        int maxOrder = Math.min(4, n - 1);
        for (int order = 0; order <= maxOrder; order++) {
            residual(x, n, order, res);
            long[] kb = bestRice(res, order, n);
            long bits = 8 + order * 16L + 2 + 4 + 4 + kb[1];
            if (bits < bestBits) { bestBits = (int) bits; bestOrder = order; bestK = (int) kb[0]; }
        }
        f.put(0, 1);
        if (bestOrder < 0) {
            f.put(1, 6); f.put(0, 1);
            for (int i = 0; i < n; i++) f.put(x[i] & 0xFFFF, 16);
            return;
        }
        f.put(8 + bestOrder, 6); f.put(0, 1);
        for (int i = 0; i < bestOrder; i++) f.put(x[i] & 0xFFFF, 16);
        residual(x, n, bestOrder, res);
        f.put(0, 2);          // метод Райса с 4-битным параметром
        f.put(0, 4);          // один раздел
        f.put(bestK, 4);
        for (int i = bestOrder; i < n; i++) {
            int e = res[i];
            long z = ((long) e << 1) ^ (e >> 31);
            z &= 0xFFFFFFFFL;
            long q = z >>> bestK;
            f.zeros((int) q);
            f.put(1, 1);
            if (bestK > 0) f.put(z & ((1L << bestK) - 1), bestK);
        }
    }

    private static void residual(int[] x, int n, int order, int[] r) {
        for (int i = order; i < n; i++) {
            switch (order) {
                case 0: r[i] = x[i]; break;
                case 1: r[i] = x[i] - x[i - 1]; break;
                case 2: r[i] = x[i] - 2 * x[i - 1] + x[i - 2]; break;
                case 3: r[i] = x[i] - 3 * x[i - 1] + 3 * x[i - 2] - x[i - 3]; break;
                default: r[i] = x[i] - 4 * x[i - 1] + 6 * x[i - 2] - 4 * x[i - 3] + x[i - 4];
            }
        }
    }

    /** Лучший параметр Райса (0..14) и число бит на остаток. */
    private static long[] bestRice(int[] r, int from, int n) {
        long bestBits = Long.MAX_VALUE; int bestK = 0;
        for (int k = 0; k <= 14; k++) {
            long bits = 0;
            for (int i = from; i < n; i++) {
                long z = (((long) r[i] << 1) ^ (r[i] >> 31)) & 0xFFFFFFFFL;
                bits += (z >>> k) + 1 + k;
                if (bits >= bestBits) break;
            }
            if (bits < bestBits) { bestBits = bits; bestK = k; }
        }
        return new long[]{bestK, bestBits};
    }
}
