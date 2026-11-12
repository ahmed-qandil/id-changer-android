package com.goldencrad.idchanger.fw;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Safe-save pipeline, mirroring the Windows tool:
 * if the output already exists it is renamed to name.bak_yyyyMMdd_HHmmss_SSS,
 * the new file is written, then re-read and verified byte-for-byte.
 */
public final class SafeSaver {

    public static class Result {
        public String savedName;
        public String backupName;   // null when no backup was made
        public boolean verified;
    }

    private SafeSaver() {}

    public static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
    }

    public static byte[] readAll(ContentResolver cr, Uri uri) throws Exception {
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new Exception("مش قادر أفتح الملف");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    public static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "firmware.bin" : last;
    }

    /**
     * Save into the user-chosen SAF tree folder with the exact output name.
     * Existing file with the same name becomes the timestamped backup.
     */
    public static Result saveToTree(ContentResolver cr, Uri treeUri, String outName,
                                    byte[] data, boolean backup, boolean verify) throws Exception {
        Result r = new Result();
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        Uri parentDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);

        Uri existing = null;
        try (Cursor c = cr.query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (outName.equals(c.getString(1))) {
                    existing = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
                    break;
                }
            }
        }

        Uri dest;
        if (existing != null && backup) {
            String bakName = outName + ".bak_" + timestamp();
            DocumentsContract.renameDocument(cr, existing, bakName);
            r.backupName = bakName;
            dest = null;
        } else {
            dest = existing;
        }
        if (dest == null) {
            dest = DocumentsContract.createDocument(cr, parentDoc, "application/octet-stream", outName);
            if (dest == null) throw new Exception("مش قادر أنشئ ملف الخرج");
        }

        writeAll(cr, dest, data);
        r.savedName = outName;

        if (verify) {
            byte[] back = readAll(cr, dest);
            if (!Arrays.equals(back, data))
                throw new Exception("التحقق فشل: الملف المحفوظ مش مطابق بايت-بايت");
            r.verified = true;
        }
        return r;
    }

    /** Save to a single SAF document uri (CREATE_DOCUMENT picker flow). */
    public static Result saveToUri(ContentResolver cr, Uri dest, byte[] data, boolean verify) throws Exception {
        Result r = new Result();
        r.savedName = displayName(cr, dest);
        writeAll(cr, dest, data);
        if (verify) {
            byte[] back = readAll(cr, dest);
            if (!Arrays.equals(back, data))
                throw new Exception("التحقق فشل: الملف المحفوظ مش مطابق بايت-بايت");
            r.verified = true;
        }
        return r;
    }

    private static void writeAll(ContentResolver cr, Uri uri, byte[] data) throws Exception {
        OutputStream out = cr.openOutputStream(uri, "rwt");
        if (out == null) throw new Exception("مش قادر أكتب الملف");
        out.write(data);
        out.flush();
        out.close();
    }
}
