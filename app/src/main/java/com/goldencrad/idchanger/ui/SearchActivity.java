package com.goldencrad.idchanger.ui;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Firmware search: hex bytes or ASCII text, results jump into the hex viewer. */
public class SearchActivity extends BaseActivity {

    private static final int MAX_RESULTS = 4096;

    private Spinner spSource, spMode;
    private CheckBox chkCase;
    private EditText edQuery;
    private ListView list;
    private TextView txtStatus;
    private final List<Long> resultOffsets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_search);

        spSource = findViewById(R.id.spSource);
        spMode = findViewById(R.id.spMode);
        chkCase = findViewById(R.id.chkCase);
        edQuery = findViewById(R.id.edQuery);
        list = findViewById(R.id.list);
        txtStatus = findViewById(R.id.status);

        fillSpinner(spSource, FirmwareStore.slotLabels());
        fillSpinner(spMode, new String[]{"هيكس بايتس", "نص ASCII"});

        findViewById(R.id.btnSearch).setOnClickListener(v -> runSearch());

        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = new Intent(this, HexViewerActivity.class);
            i.putExtra("source", spSource.getSelectedItemPosition());
            i.putExtra("offset", Long.toHexString(resultOffsets.get(pos)).toUpperCase());
            startActivity(i);
        });
    }

    private void runSearch() {
        int srcIdx = Math.max(0, spSource.getSelectedItemPosition());
        FirmwareDoc d = FirmwareStore.get(FirmwareStore.SLOT_KEYS[srcIdx]);
        if (d == null) { say(txtStatus, "حمّل المصدر المحدد قبل البحث"); return; }
        String q = edQuery.getText().toString();
        byte[] needle;
        boolean hexMode = spMode.getSelectedItemPosition() == 0;
        try {
            if (hexMode) {
                needle = FirmwareEngine.parseHex(q);
            } else {
                for (int i = 0; i < q.length(); i++)
                    if (q.charAt(i) > 0x7F) throw new IllegalArgumentException("وضع النص ASCII يقبل حروف ASCII بس");
                String s = chkCase.isChecked() ? q : q.toLowerCase(Locale.US);
                needle = s.getBytes("US-ASCII");
            }
        } catch (Exception e) {
            say(txtStatus, hexMode ? "استعلام هيكس غير صالح" : e.getMessage());
            return;
        }
        if (needle.length == 0) { say(txtStatus, "اكتب نص أو بايتات للبحث"); return; }

        byte[] hay = d.data;
        boolean ci = !hexMode && !chkCase.isChecked();
        resultOffsets.clear();
        List<String> rows = new ArrayList<>();
        boolean limited = false;
        for (int i = 0; i + needle.length <= hay.length; i++) {
            boolean m = true;
            for (int j = 0; j < needle.length; j++) {
                int a = hay[i + j] & 0xFF, bch = needle[j] & 0xFF;
                if (ci) {
                    if (a >= 'A' && a <= 'Z') a += 32;
                    if (bch >= 'A' && bch <= 'Z') bch += 32;
                }
                if (a != bch) { m = false; break; }
            }
            if (m) {
                if (resultOffsets.size() >= MAX_RESULTS) { limited = true; break; }
                resultOffsets.add((long) i);
                int n = Math.min(needle.length, 16);
                rows.add(String.format(Locale.US, "0x%08X: %s", i,
                        FirmwareEngine.bytesToHex(hay, i, n, " ")));
            }
        }
        if (rows.isEmpty()) rows.add("0 نتيجة");
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        say(txtStatus, "البحث خلص: " + resultOffsets.size() + " نتيجة"
                + (limited ? " (اتوصل لحد النتائج الأقصى)" : ""));
    }
}
