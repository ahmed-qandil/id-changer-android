package com.goldencrad.idchanger.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.goldencrad.idchanger.R;
import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareEngine;
import com.goldencrad.idchanger.fw.FirmwareStore;
import com.goldencrad.idchanger.fw.Patch;

import java.util.Locale;

/** 1506 fixed-offset ID editor: the 6 presets + custom offset/length. */
public class Editor1506Activity extends BaseActivity {

    // preset name -> {offset, length}  (same presets as the Windows tool)
    private static final String[] PRESETS = {
            "كستمر أيدي (0x01A000)", "بورد أيدي (0x01A010)", "ماك (0x01B000)",
            "سيريال (0x01C000)", "ريجون (0x01D000)", "هيدر (0)", "مخصص"
    };
    private static final long[] P_OFF = {0x01A000, 0x01A010, 0x01B000, 0x01C000, 0x01D000, 0, -1};
    private static final long[] P_LEN = {16, 16, 6, 16, 16, 16, -1};

    private TextView txtOrig, txtTgt, txtStatus;
    private EditText edOffset, edLength, edValue;
    private CheckBox chkRepair, chkBackup, chkVerify;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_editor1506);

        txtOrig = findViewById(R.id.txtOrig);
        txtTgt = findViewById(R.id.txtTgt);
        txtStatus = findViewById(R.id.status);
        edOffset = findViewById(R.id.edOffset);
        edLength = findViewById(R.id.edLength);
        edValue = findViewById(R.id.edValue);
        chkRepair = findViewById(R.id.chkRepair);
        chkBackup = findViewById(R.id.chkBackup);
        chkVerify = findViewById(R.id.chkVerify);

        findViewById(R.id.btnLoadOrig).setOnClickListener(v -> pickFirmware(FirmwareStore.S1506_ORIG));
        findViewById(R.id.btnLoadTgt).setOnClickListener(v -> pickFirmware(FirmwareStore.S1506_TGT));

        Spinner spPreset = findViewById(R.id.spPreset);
        fillSpinner(spPreset, PRESETS);
        spPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (P_OFF[pos] >= 0) {
                    edOffset.setText(String.format(Locale.US, "%X", P_OFF[pos]));
                    edLength.setText(String.valueOf(P_LEN[pos]));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        findViewById(R.id.btnReadOrig).setOnClickListener(v -> readId(FirmwareStore.S1506_ORIG));
        findViewById(R.id.btnReadTgt).setOnClickListener(v -> readId(FirmwareStore.S1506_TGT));
        findViewById(R.id.btnCopySave).setOnClickListener(v -> copyAndSave());
        findViewById(R.id.btnAddPatch).setOnClickListener(v -> addPatch());

        refreshInfo();
    }

    @Override
    protected void onFirmwareLoaded(String slot) {
        refreshInfo();
        FirmwareDoc d = FirmwareStore.get(slot);
        say(txtStatus, "اتحمّل " + FirmwareStore.arabicName(slot) + ": " + d.name + " (" + d.size() + " بايت)");
    }

    private void refreshInfo() {
        txtOrig.setText(info(FirmwareStore.S1506_ORIG));
        txtTgt.setText(info(FirmwareStore.S1506_TGT));
    }

    private String info(String slot) {
        FirmwareDoc d = FirmwareStore.get(slot);
        if (d == null) return FirmwareStore.arabicName(slot) + ": فاضي. استخدم زرار التحميل.";
        return FirmwareStore.arabicName(slot) + ": " + d.name + " - " + d.size() + " بايت\n"
                + FirmwareEngine.hashSummary(d.data);
    }

    private long offset() { return FirmwareEngine.parseHexLong(edOffset.getText().toString()); }
    private long length() { return Long.parseLong(edLength.getText().toString().trim()); }

    private boolean checkRange(FirmwareDoc d, long off, long len) {
        if (off < 0 || len <= 0 || off + len > d.size()) {
            say(txtStatus, "الأوفسيت والطول المحددين بره السوفت (" + d.size() + " بايت)");
            return false;
        }
        return true;
    }

    private void readId(String slot) {
        FirmwareDoc d = FirmwareStore.get(slot);
        if (d == null) { say(txtStatus, "حمّل ملف " + FirmwareStore.arabicName(slot) + " الأول"); return; }
        try {
            long off = offset(), len = length();
            if (!checkRange(d, off, len)) return;
            String hex = FirmwareEngine.bytesToHex(d.data, (int) off, (int) len, " ");
            edValue.setText(hex);
            say(txtStatus, "اتقري " + len + " بايت من " + FirmwareStore.arabicName(slot)
                    + " عند 0x" + Long.toHexString(off).toUpperCase(Locale.US) + ": " + hex);
        } catch (Exception e) {
            say(txtStatus, "راجع الأوفسيت (هيكس) والطول (رقم موجب)");
        }
    }

    private void copyAndSave() {
        FirmwareDoc orig = FirmwareStore.get(FirmwareStore.S1506_ORIG);
        FirmwareDoc tgt = FirmwareStore.get(FirmwareStore.S1506_TGT);
        if (orig == null || tgt == null) {
            say(txtStatus, "حمّل الأصلي والتارجت الأول"); return;
        }
        try {
            long off = offset(), len = length();
            if (!checkRange(orig, off, len) || !checkRange(tgt, off, len)) return;
            byte[] out = tgt.data.clone();
            System.arraycopy(orig.data, (int) off, out, (int) off, (int) len);
            String note = "";
            if (chkRepair.isChecked()) {
                long[] r = FirmwareEngine.repairSpheCrc(out);
                if (r == null) {
                    say(txtStatus, "مفيش بلوك SPHE كامل في التارجت. الملف مش اتحفظ؛ شيل تصليح CRC لو مش مطلوب.");
                    return;
                }
                note = String.format(Locale.US, " - SPHE CRC اتصلح من 0x%08X لـ 0x%08X", r[1], r[2]);
            }
            say(txtStatus, "اتنسخ " + len + " بايت لأوفسيت 0x" + Long.toHexString(off).toUpperCase(Locale.US) + " في التارجت.");
            saveOutput("Modified_" + tgt.name, out, chkBackup.isChecked(), chkVerify.isChecked(), txtStatus, note);
        } catch (Exception e) {
            say(txtStatus, "راجع الأوفسيت والطول");
        }
    }

    private void addPatch() {
        try {
            long off = offset(), len = length();
            byte[] val = FirmwareEngine.parseHex(edValue.getText().toString());
            FirmwareStore.patchQueue.add(new Patch(off, len, val, "1506 أيدي"));
            say(txtStatus, "اتضاف باتش عند 0x" + Long.toHexString(off).toUpperCase(Locale.US)
                    + " (" + len + " بايت). عدد الباتشات: " + FirmwareStore.patchQueue.size());
        } catch (Exception e) {
            say(txtStatus, "مش قادر أضيف الباتش: " + e.getMessage());
        }
    }
}
