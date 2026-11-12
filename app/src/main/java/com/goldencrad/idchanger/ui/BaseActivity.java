package com.goldencrad.idchanger.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareEngine;
import com.goldencrad.idchanger.fw.FirmwareStore;
import com.goldencrad.idchanger.fw.SafeSaver;

import java.util.ArrayList;
import java.util.List;

/** Shared plumbing: SAF pickers, firmware loading, the save pipeline, TalkBack status. */
public abstract class BaseActivity extends Activity {

    // request codes
    protected static final int REQ_OPEN_BASE = 100;   // + slot index
    protected static final int REQ_SAVE = 200;
    protected static final int REQ_TREE = 201;
    protected static final int REQ_OPEN_JSON = 202;
    protected static final int REQ_SAVE_JSON = 203;

    private String pendingSlot;
    // pending save (picker fallback when no tree folder is set)
    private byte[] pendingData;
    private String pendingName;
    private boolean pendingVerify;
    private TextView pendingStatus;
    private String pendingCrcNote = "";

    /* ---------- status ---------- */

    protected void say(TextView status, String msg) {
        status.setText(msg);
        status.announceForAccessibility(msg);
    }

    protected void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /* ---------- picking firmware ---------- */

    protected void pickFirmware(String slot) {
        pendingSlot = slot;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        startActivityForResult(i, REQ_OPEN_BASE);
    }

    /* ---------- save flow ---------- */

    /**
     * Save data with the Windows pipeline. crcNote is appended to the status
     * (e.g. " SPHE CRC اتصلح من 0x.. لـ 0x..").
     */
    protected void saveOutput(String outName, byte[] data, boolean backup, boolean verify,
                              TextView status, String crcNote) {
        ContentResolver cr = getContentResolver();
        if (FirmwareStore.treeUri != null) {
            try {
                SafeSaver.Result r = SafeSaver.saveToTree(cr, FirmwareStore.treeUri, outName, data, backup, verify);
                String msg = "اتحفظ: " + r.savedName
                        + (r.backupName != null ? " - النسخة الاحتياطية: " + r.backupName : "")
                        + (r.verified ? " - تم التحقق بايت-بايت" : "") + crcNote;
                say(status, msg);
                onSaved(r.savedName);
            } catch (Exception e) {
                say(status, "فشل الحفظ: " + e.getMessage());
            }
        } else {
            pendingData = data;
            pendingName = outName;
            pendingVerify = verify;
            pendingStatus = status;
            pendingCrcNote = crcNote;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, outName);
            startActivityForResult(i, REQ_SAVE);
        }
    }

    protected void onSaved(String savedName) {}

    /* ---------- tree folder ---------- */

    protected void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(i, REQ_TREE);
    }

    /* ---------- results ---------- */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        ContentResolver cr = getContentResolver();
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {}

        if (requestCode == REQ_TREE) {
            FirmwareStore.treeUri = uri;
            FirmwareStore.treeName = SafeSaver.displayName(cr, uri);
            onTreeSet();
            return;
        }
        if (requestCode == REQ_OPEN_BASE) {
            try {
                byte[] bytes = SafeSaver.readAll(cr, uri);
                if (bytes.length == 0) { toast("ملفات السوفت الفاضية مش مدعومة"); return; }
                String name = SafeSaver.displayName(cr, uri);
                FirmwareStore.put(pendingSlot, new FirmwareDoc(name, uri, bytes));
                onFirmwareLoaded(pendingSlot);
            } catch (Exception e) {
                toast("مش قادر أقرا الملف: " + e.getMessage());
            }
            return;
        }
        if (requestCode == REQ_SAVE) {
            try {
                SafeSaver.Result r = SafeSaver.saveToUri(cr, uri, pendingData, pendingVerify);
                say(pendingStatus, "اتحفظ: " + r.savedName
                        + (r.verified ? " - تم التحقق بايت-بايت" : "") + pendingCrcNote);
                onSaved(r.savedName);
            } catch (Exception e) {
                say(pendingStatus, "فشل الحفظ: " + e.getMessage());
            }
            return;
        }
        onCustomResult(requestCode, uri);
    }

    protected void onCustomResult(int requestCode, Uri uri) {}
    protected void onFirmwareLoaded(String slot) {}
    protected void onTreeSet() {}

    /* ---------- ui helpers ---------- */

    protected void fillSpinner(Spinner sp, String[] items) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
    }

    /** Build an accessible hex-dump ListView content for [offset, offset+window). */
    protected List<String> hexRows(byte[] d, long offset, int window) {
        List<String> rows = new ArrayList<>();
        long end = Math.min(d.length, offset + window);
        for (long p = offset; p < end; p += 16) {
            int n = (int) Math.min(16, end - p);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("0x%08X: ", p));
            for (int i = 0; i < n; i++) sb.append(String.format("%02X ", d[(int) p + i] & 0xFF));
            sb.append(' ');
            for (int i = 0; i < n; i++) {
                int b = d[(int) p + i] & 0xFF;
                sb.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
            }
            rows.add(sb.toString());
        }
        return rows;
    }
}
