package voice.changer.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Files {
    private Files() {}

    static File outDir(Context c) {
        File d = new File(c.getCacheDir(), "out");
        d.mkdirs();
        return d;
    }

    static File tmpDir(Context c) {
        File d = new File(c.getCacheDir(), "tmp");
        d.mkdirs();
        return d;
    }

    static File tmp(Context c, String name) { return new File(tmpDir(c), name); }

    /** Копии файлов фона лежат в закрытой папке приложения. */
    static File bgLayerFile(Context c, String id) {
        File d = new File(c.getFilesDir(), "bg");
        d.mkdirs();
        return new File(d, id + ".dat");
    }

    static File newOut(Context c, String ext) {
        String base = "VoiceChanger_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(outDir(c), base + "." + ext);
        for (int i = 2; f.exists(); i++) f = new File(outDir(c), base + "_" + i + "." + ext);
        return f;
    }

    /** Удаляет временные файлы старше суток. */
    static void cleanup(Context c) {
        long limit = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File d : new File[]{outDir(c), tmpDir(c)}) {
            File[] l = d.listFiles();
            if (l != null) for (File f : l) if (f.lastModified() < limit) f.delete();
        }
    }

    static String displayName(Context c, Uri u) {
        try (Cursor cur = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) { }
        String p = u.getLastPathSegment();
        return p == null ? "audio" : p;
    }

    /** Копирует содержимое Uri в файл, возвращает имя. */
    static String copyUri(Context c, Uri u, File dst) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(u);
             OutputStream out = new java.io.FileOutputStream(dst)) {
            if (in == null) throw new IOException("cannot open " + u);
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        return displayName(c, u);
    }

    static void copyToUri(Context c, File src, Uri dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = c.getContentResolver().openOutputStream(dst)) {
            if (out == null) throw new IOException("cannot write " + dst);
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
    }

    static String mime(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".wav")) return "audio/x-wav";
        if (n.endsWith(".flac")) return "audio/flac";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg") || n.endsWith(".opus")) return "audio/ogg";
        return "application/octet-stream";
    }

    static Uri shareUri(Context c, File f) {
        return new Uri.Builder().scheme("content").authority(c.getPackageName() + ".share")
                .appendPath(f.getName()).build();
    }

    static void share(Context c, File f) {
        Uri uri = shareUri(c, f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime(f));
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri(null, uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent ch = Intent.createChooser(i, c.getString(R.string.share_title));
        if (!(c instanceof Activity)) ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(ch);
    }

    static String fmtTime(float sec) {
        int s = Math.round(sec);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
