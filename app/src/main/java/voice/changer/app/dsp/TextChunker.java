package voice.changer.app.dsp;

import java.util.ArrayList;
import java.util.List;

/** Делит длинный текст на куски ≤ max символов по границам предложений (у TTS-движков есть лимит длины). */
public final class TextChunker {
    private TextChunker() {}

    public static List<String> split(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0, n = text.length();
        while (i < n) {
            int j = i;
            while (j < n) {
                char c = text.charAt(j++);
                if (c == '.' || c == '!' || c == '?' || c == '…' || c == '\n') {
                    while (j < n && (text.charAt(j) == '.' || text.charAt(j) == '!' || text.charAt(j) == '?')) j++;
                    break;
                }
            }
            String sent = text.substring(i, j);
            i = j;
            while (sent.length() > max) {          // слишком длинное предложение — режем по пробелу
                int cut = sent.lastIndexOf(' ', max);
                if (cut < max / 2) cut = max;
                flush(out, cur, sent.substring(0, cut), max);
                sent = sent.substring(cut);
            }
            flush(out, cur, sent, max);
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur, String piece, int max) {
        if (cur.length() + piece.length() > max) {
            String t = cur.toString().trim();
            if (!t.isEmpty()) out.add(t);
            cur.setLength(0);
        }
        cur.append(piece);
    }
}
