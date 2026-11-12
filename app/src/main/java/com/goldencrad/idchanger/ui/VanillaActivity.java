package com.goldencrad.idchanger.ui;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.goldencrad.idchanger.R;
import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareEngine;
import com.goldencrad.idchanger.fw.FirmwareStore;

import java.util.Locale;

/** Vanilla tools: signature-based ID detection/copy on ANY firmware (BIN_0S / SPHE). */
public class VanillaActivity extends BaseActivity {

    private TextView txtOrig, txtTgt, txtScan, txtStatus;
    private EditText edIdLen;
    private CheckBox chkRepair, chkBackup, chkVerify;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vanilla);

        txtOrig = findViewById(R.id.txtOrig);
        txtTgt = findViewById(R.id.txtTgt);
        txtScan = findViewById(R.id.txtScan);
        txtStatus = findViewById(R.id.status);
        edIdLen = findViewById(R.id.edIdLen);
        chkRepair = findViewById(R.id.chkRepair);
        chkBackup = findViewById(R.id.chkBackup);
        chkVerify = findViewById(R.id.chkVerify);

        findViewById(R.id.btnLoadOrig).setOnClickListener(v -> pickFirmware(FirmwareStore.VAN_ORIG));
        findViewById(R.id.btnLoadTgt).setOnClickListener(v -> pickFirmware(FirmwareStore.VAN_TGT));
        findViewById(R.id.btnScan).setOnClickListener(v -> scan());
        findViewById(R.id.btnCopyId).setOnClickListener(v -> copyId());
        findViewById(R.id.btnCopyBlock).setOnClickListener(v -> copyBlock());
        findViewById(R.id.btnRepairOnly).setOnClickListener(v -> repairOnly());

        refreshInfo();
    }

    @Override
    protected void onFirmwareLoaded(String slot) {
        refreshInfo();
        FirmwareDoc d = FirmwareStore.get(slot);
        say(txtStatus, "اتحمّل " + FirmwareStore.arabicName(slot) + ": " + d.name + " (" + d.size() + " بايت)");
    }

    private void refreshInfo() {
        txtOrig.setText(info(FirmwareStore.VAN_ORIG));
        txtTgt.setText(info(FirmwareStore.VAN_TGT));
    }

    private String info(String slot) {
        FirmwareDoc d = FirmwareStore.get(slot);
        if (d == null) return FirmwareStore.arabicName(slot) + ": فاضي. استخدم زرار التحميل.";
        return FirmwareStore.arabicName(slot) + ": " + d.name + " - " + d.size() + " بايت";
    }

    private long idLen() { return Long.parseLong(edIdLen.getText().toString().trim()); }

    private String describeOne(String slot, byte[] d, long len) {
        StringBuilder sb = new StringBuilder(FirmwareStore.arabicName(slot)).append(":\n");
        FirmwareEngine.Sig sig = FirmwareEngine.findSignature(d, len);
        if (sig == null) {
            sb.append("  الأيدي: مفيش توقيع كامل مدعوم (BIN_0S / SPHE).\n");
        } else {
            String kind = sig.kind == FirmwareEngine.SIG_SPHE ? "SPHE" : "BIN_0S";
            sb.append(String.format(Locale.US,
                    "  الأيدي: %s عند 0x%X - أوفسيت الأيدي 0x%X - القيمة %s\n",
                    kind, sig.sigOffset, sig.idOffset,
                    FirmwareEngine.bytesToHex(d, (int) sig.idOffset, (int) sig.idLen, " ")));
        }
        FirmwareEngine.Sphe sp = FirmwareEngine.findSpheBlock(d);
        if (sp.found) {
            long stored = FirmwareEngine.storedSpheCrc(d, sp.offset) & 0xFFFFFFFFL;
            long calc = FirmwareEngine.calcSpheCrc(d, sp.offset) & 0xFFFFFFFFL;
            sb.append(String.format(Locale.US,
                    "  أوفسيت SPHE: 0x%X\n  SPHE CRC المخزن: 0x%08X\n  SPHE CRC المحسوب: 0x%08X (%s)\n",
                    sp.offset, stored, calc, stored == calc ? "مطابق" : "مش مطابق"));
        } else {
            sb.append("  SPHE CRC: مفيش بلوك SPHE كامل.\n");
        }
        return sb.toString();
    }

    private void scan() {
        FirmwareDoc orig = FirmwareStore.get(FirmwareStore.VAN_ORIG);
        FirmwareDoc tgt = FirmwareStore.get(FirmwareStore.VAN_TGT);
        if (orig == null && tgt == null) { say(txtStatus, "حمّل ملف فانيلا واحد على الأقل قبل الفحص"); return; }
        long len;
        try { len = idLen(); if (len <= 0) throw new Exception(); }
        catch (Exception e) { say(txtStatus, "اكتب طول أيدي موجب بالبايت"); return; }
        StringBuilder sb = new StringBuilder();
        int found = 0;
        if (orig != null) {
            String s = describeOne(FirmwareStore.VAN_ORIG, orig.data, len);
            sb.append(s);
            if (!s.contains("مفيش توقيع")) found++;
        }
        if (tgt != null) {
            String s = describeOne(FirmwareStore.VAN_TGT, tgt.data, len);
            sb.append(s);
            if (!s.contains("مفيش توقيع")) found++;
        }
        txtScan.setText(sb.toString());
        say(txtStatus, "الفحص خلص: " + found + " نتيجة. اختار أيدي مكتشف عشان تشوف التفاصيل في النص.");
    }

    private void copyId() {
        FirmwareDoc orig = FirmwareStore.get(FirmwareStore.VAN_ORIG);
        FirmwareDoc tgt = FirmwareStore.get(FirmwareStore.VAN_TGT);
        if (orig == null || tgt == null) { say(txtStatus, "حمّل فانيلا الأصلي والتارجت الأول"); return; }
        long len;
        try { len = idLen(); if (len <= 0) throw new Exception(); }
        catch (Exception e) { say(txtStatus, "اكتب طول أيدي موجب بالبايت"); return; }
        FirmwareEngine.Sig so = FirmwareEngine.findSignature(orig.data, len);
        FirmwareEngine.Sig st = FirmwareEngine.findSignature(tgt.data, len);
        if (so == null || st == null) {
            say(txtStatus, "لازم يتلاقي توقيع كامل مدعوم وأيدي في الملفين بالطول المحدد"); return;
        }
        byte[] out = tgt.data.clone();
        System.arraycopy(orig.data, (int) so.idOffset, out, (int) st.idOffset, (int) len);
        String note = "";
        if (chkRepair.isChecked()) {
            long[] r = FirmwareEngine.repairSpheCrc(out);
            if (r == null) {
                say(txtStatus, "مفيش بلوك SPHE كامل في فانيلا التارجت؛ مفيش حاجة اتحفظت.");
                return;
            }
            note = String.format(Locale.US, " - SPHE CRC اتصلح من 0x%08X لـ 0x%08X", r[1], r[2]);
        }
        say(txtStatus, String.format(Locale.US, "اتنسخ أيدي %d بايت لأوفسيت 0x%X.", len, st.idOffset));
        saveOutput("Modified_Vanilla_" + tgt.name, out, chkBackup.isChecked(), chkVerify.isChecked(), txtStatus, note);
    }

    private void copyBlock() {
        FirmwareDoc orig = FirmwareStore.get(FirmwareStore.VAN_ORIG);
        FirmwareDoc tgt = FirmwareStore.get(FirmwareStore.VAN_TGT);
        if (orig == null || tgt == null) { say(txtStatus, "حمّل فانيلا الأصلي والتارجت الأول"); return; }
        FirmwareEngine.Sphe bo = FirmwareEngine.findSpheBlock(orig.data);
        FirmwareEngine.Sphe bt = FirmwareEngine.findSpheBlock(tgt.data);
        if (!bo.found || !bt.found) {
            say(txtStatus, "مطلوب بلوك SPHE كامل 256 بايت في ملفي فانيلا"); return;
        }
        byte[] out = tgt.data.clone();
        System.arraycopy(orig.data, (int) bo.offset, out, (int) bt.offset, 256);
        long[] r = FirmwareEngine.repairSpheCrc(out);
        if (r == null) { say(txtStatus, "مش قادر أصلح بلوك SPHE المنسوخ؛ مفيش حاجة اتحفظت"); return; }
        String note = String.format(Locale.US,
                " - اتنسخ بلوك SPHE الـ 256 بايت من 0x%X لـ 0x%X واتصلح CRC من 0x%08X لـ 0x%08X",
                bo.offset, bt.offset, r[1], r[2]);
        saveOutput("Modified_Vanilla_" + tgt.name, out, chkBackup.isChecked(), chkVerify.isChecked(), txtStatus, note);
    }

    private void repairOnly() {
        FirmwareDoc tgt = FirmwareStore.get(FirmwareStore.VAN_TGT);
        if (tgt == null) { say(txtStatus, "حمّل فانيلا التارجت الأول"); return; }
        byte[] out = tgt.data.clone();
        long[] r = FirmwareEngine.repairSpheCrc(out);
        if (r == null) { say(txtStatus, "مفيش بلوك SPHE كامل في فانيلا التارجت؛ مفيش حاجة اتحفظت"); return; }
        String note = String.format(Locale.US, " - اتصلح SPHE CRC عند 0x%X من 0x%08X لـ 0x%08X", r[0], r[1], r[2]);
        saveOutput("Modified_Vanilla_" + tgt.name, out, chkBackup.isChecked(), chkVerify.isChecked(), txtStatus, note);
    }
}
