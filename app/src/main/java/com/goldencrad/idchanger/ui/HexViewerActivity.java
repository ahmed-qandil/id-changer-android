package com.goldencrad.idchanger.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.goldencrad.idchanger.R;
import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareEngine;
import com.goldencrad.idchanger.fw.FirmwareStore;

/** Accessible hex viewer: line-by-line list rows TalkBack can read. */
public class HexViewerActivity extends BaseActivity {

    private static final int[] WINDOWS = {1024, 4096, 16384, 65536};

    private Spinner spSource, spWindow;
    private EditText edOffset;
    private ListView list;
    private TextView txtStatus;
    private long curOffset = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_hex);

        spSource = findViewById(R.id.spSource);
        spWindow = findViewById(R.id.spWindow);
        edOffset = findViewById(R.id.edOffset);
        list = findViewById(R.id.list);
        txtStatus = findViewById(R.id.status);

        fillSpinner(spSource, FirmwareStore.slotLabels());
        fillSpinner(spWindow, new String[]{"1 كيلو", "4 كيلو", "16 كيلو", "64 كيلو"});
        spWindow.setSelection(1);

        int srcIdx = getIntent().getIntExtra("source", -1);
        if (srcIdx >= 0 && srcIdx < FirmwareStore.SLOT_KEYS.length) spSource.setSelection(srcIdx);
        String off = getIntent().getStringExtra("offset");
        if (off != null) edOffset.setText(off);

        findViewById(R.id.btnGo).setOnClickListener(v -> go());
        findViewById(R.id.btnPrev).setOnClickListener(v -> step(-1));
        findViewById(R.id.btnNext).setOnClickListener(v -> step(1));

        refresh();
    }

    private FirmwareDoc current() {
        int i = spSource.getSelectedItemPosition();
        return FirmwareStore.get(FirmwareStore.SLOT_KEYS[Math.max(0, i)]);
    }

    private int window() { return WINDOWS[Math.max(0, spWindow.getSelectedItemPosition())]; }

    private void go() {
        try {
            curOffset = FirmwareEngine.parseHexLong(edOffset.getText().toString());
        } catch (Exception e) {
            say(txtStatus, "اكتب أوفسيت هيكس صحيح موجب أو صفر"); return;
        }
        refresh();
    }

    private void step(int dir) {
        curOffset = Math.max(0, curOffset + (long) dir * window());
        refresh();
    }

    private void refresh() {
        FirmwareDoc d = current();
        if (d == null) {
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                    new String[]{"مفيش ملف محمّل في المصدر ده. حمّله من محرر 1506 أو أدوات فانيلا."}));
            say(txtStatus, "مفيش ملف محمّل");
            return;
        }
        if (curOffset >= d.size()) curOffset = Math.max(0, d.size() - 1);
        edOffset.setText(Long.toHexString(curOffset).toUpperCase());
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                hexRows(d.data, curOffset, window())));
        long end = Math.min(d.size(), curOffset + window());
        say(txtStatus, String.format("الملف: %s - حجم %d بايت - بداية النافذة 0x%X - عدد البايتات في النافذة %d",
                d.name, d.size(), curOffset, end - curOffset));
    }
}
