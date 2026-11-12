package com.goldencrad.idchanger.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.goldencrad.idchanger.R;
import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareEngine;
import com.goldencrad.idchanger.fw.FirmwareStore;
import com.goldencrad.idchanger.fw.Patch;
import com.goldencrad.idchanger.fw.PatchQueue;
import com.goldencrad.idchanger.fw.SafeSaver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Batch patches: queue + JSON profile import/export + apply with the safe-save pipeline. */
public class BatchActivity extends BaseActivity {

    private ListView list;
    private TextView txtStatus;
    private EditText edOffset, edLength, edValue, edSource;
    private Spinner spTarget;
    private CheckBox chkBackup, chkVerify, chkRepair;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_batch);

        list = findViewById(R.id.list);
        txtStatus = findViewById(R.id.status);
        edOffset = findViewById(R.id.edOffset);
        edLength = findViewById(R.id.edLength);
        edValue = findViewById(R.id.edValue);
        edSource = findViewById(R.id.edSource);
        spTarget = findViewById(R.id.spTarget);
        chkBackup = findViewById(R.id.chkBackup);
        chkVerify = findViewById(R.id.chkVerify);
        chkRepair = findViewById(R.id.chkRepair);

        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        fillSpinner(spTarget, new String[]{"1506 التارجت", "فانيلا التارجت"});

        findViewById(R.id.btnAdd).setOnClickListener(v -> addPatch());
        findViewById(R.id.btnRemove).setOnClickListener(v -> removeSelected());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            FirmwareStore.patchQueue.clear();
            refresh();
            say(txtStatus, "قائمة الباتشات اتمسحت");
        });
        findViewById(R.id.btnLoadJson).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, REQ_OPEN_JSON);
        });
        findViewById(R.id.btnSaveJson).setOnClickListener(v -> {
            if (FirmwareStore.patchQueue.size() == 0) { say(txtStatus, "قائمة الباتشات فاضية"); return; }
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "patch_profile.json");
            startActivityForResult(i, REQ_SAVE_JSON);
        });
        findViewById(R.id.btnApply).setOnClickListener(v -> apply());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<String> rows = new ArrayList<>();
        for (Patch p : FirmwareStore.patchQueue.patches) rows.add(p.describe());
        if (rows.isEmpty()) rows.add("القائمة فاضية. ضيف باتش أو حمّل بروفايل JSON.");
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, rows));
    }

    private void addPatch() {
        try {
            long off = FirmwareEngine.parseHexLong(edOffset.getText().toString());
            long len = Long.parseLong(edLength.getText().toString().trim());
            byte[] val = FirmwareEngine.parseHex(edValue.getText().toString());
            String src = edSource.getText().toString().trim();
            FirmwareStore.patchQueue.add(new Patch(off, len, val, src));
            refresh();
            say(txtStatus, String.format(Locale.US, "اتضاف باتش عند 0x%X (%d بايت). المجموع %d باتش.",
                    off, len, FirmwareStore.patchQueue.size()));
        } catch (Exception e) {
            say(txtStatus, "مش قادر أضيف: " + e.getMessage());
        }
    }

    private void removeSelected() {
        android.util.SparseBooleanArray checked = list.getCheckedItemPositions();
        List<Integer> toRemove = new ArrayList<>();
        for (int i = 0; i < list.getCount(); i++)
            if (checked.get(i) && i < FirmwareStore.patchQueue.size()) toRemove.add(i);
        if (toRemove.isEmpty()) { say(txtStatus, "حدد باتش أو أكتر للحذف"); return; }
        for (int i = toRemove.size() - 1; i >= 0; i--)
            FirmwareStore.patchQueue.remove(toRemove.get(i));
        refresh();
        say(txtStatus, "اتحذف " + toRemove.size() + " باتش؛ الباقي " + FirmwareStore.patchQueue.size());
    }

    private void apply() {
        String slot = spTarget.getSelectedItemPosition() == 0 ? FirmwareStore.S1506_TGT : FirmwareStore.VAN_TGT;
        FirmwareDoc tgt = FirmwareStore.get(slot);
        if (tgt == null) { say(txtStatus, "حمّل التارجت المحدد الأول"); return; }
        if (FirmwareStore.patchQueue.size() == 0) { say(txtStatus, "قائمة الباتشات فاضية"); return; }
        byte[] out = tgt.data.clone();
        long[] res = FirmwareStore.patchQueue.apply(out);
        if (res[0] == 0) { say(txtStatus, "مفيش باتش جوه حدود التارجت"); return; }
        String note = String.format(Locale.US, " - اتطبق %d من %d باتش", res[0], res[0] + res[1]);
        if (res[1] > 0) note += String.format(Locale.US, " - اتخطى %d باتش بره الحدود", res[1]);
        if (chkRepair.isChecked()) {
            long[] r = FirmwareEngine.repairSpheCrc(out);
            if (r == null) {
                say(txtStatus, "تصليح CRC متطلب، بس مفيش بلوك SPHE كامل. الملف مش اتحفظ.");
                return;
            }
            note += String.format(Locale.US, " - SPHE CRC اتصلح من 0x%08X لـ 0x%08X", r[1], r[2]);
        }
        saveOutput("Patched_" + tgt.name, out, chkBackup.isChecked(), chkVerify.isChecked(), txtStatus, note);
    }

    @Override
    protected void onCustomResult(int requestCode, Uri uri) {
        if (requestCode == REQ_OPEN_JSON) {
            try {
                byte[] raw = SafeSaver.readAll(getContentResolver(), uri);
                PatchQueue q = PatchQueue.fromJson(new String(raw, "UTF-8"));
                FirmwareStore.patchQueue.clear();
                for (Patch p : q.patches) FirmwareStore.patchQueue.patches.add(p);
                refresh();
                say(txtStatus, "اتحمّل " + q.size() + " باتش من " + SafeSaver.displayName(getContentResolver(), uri));
            } catch (Exception e) {
                say(txtStatus, "فشل تحميل البروفايل: " + e.getMessage());
            }
        } else if (requestCode == REQ_SAVE_JSON) {
            try {
                byte[] raw = FirmwareStore.patchQueue.toJson().getBytes("UTF-8");
                SafeSaver.saveToUri(getContentResolver(), uri, raw, false);
                say(txtStatus, "اتحفظ " + FirmwareStore.patchQueue.size() + " باتش في "
                        + SafeSaver.displayName(getContentResolver(), uri));
            } catch (Exception e) {
                say(txtStatus, "فشل حفظ البروفايل: " + e.getMessage());
            }
        }
    }
}
