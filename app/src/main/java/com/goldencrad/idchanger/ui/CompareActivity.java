package com.goldencrad.idchanger.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.goldencrad.idchanger.R;
import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Byte-by-byte firmware comparison with XOR, like the Windows Compare tab. */
public class CompareActivity extends BaseActivity {

    private static final int MAX_SHOWN = 1000;

    private Spinner spPair;
    private TextView txtSummary, txtStatus;
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_compare);

        spPair = findViewById(R.id.spPair);
        txtSummary = findViewById(R.id.txtSummary);
        txtStatus = findViewById(R.id.status);
        list = findViewById(R.id.list);

        fillSpinner(spPair, new String[]{"1506: الأصلي × التارجت", "فانيلا: الأصلي × التارجت"});
        findViewById(R.id.btnCompare).setOnClickListener(v -> compare());
    }

    private void compare() {
        boolean is1506 = spPair.getSelectedItemPosition() == 0;
        FirmwareDoc a = FirmwareStore.get(is1506 ? FirmwareStore.S1506_ORIG : FirmwareStore.VAN_ORIG);
        FirmwareDoc c = FirmwareStore.get(is1506 ? FirmwareStore.S1506_TGT : FirmwareStore.VAN_TGT);
        if (a == null || c == null) { say(txtStatus, "حمّل الأصلي والتارجت الأول"); return; }

        long min = Math.min(a.size(), c.size());
        long diffs = 0;
        List<String> rows = new ArrayList<>();
        for (long i = 0; i < min; i++) {
            int x = a.data[(int) i] & 0xFF, y = c.data[(int) i] & 0xFF;
            if (x != y) {
                diffs++;
                if (rows.size() < MAX_SHOWN)
                    rows.add(String.format(Locale.US, "0x%08X: أصلي %02X - تارجت %02X - XOR %02X", i, x, y, x ^ y));
            }
        }
        long sizeDiff = Math.abs(a.size() - c.size());
        double pct = min == 0 ? 0 : (100.0 * diffs / min);
        String summary = String.format(Locale.US,
                "%d بايت مختلف من %d بايت متقارن (%.2f%%).", diffs, min, pct)
                + (sizeDiff > 0 ? " حجم الملفين مختلف بـ " + sizeDiff + " بايت." : "")
                + (diffs > MAX_SHOWN ? " بنعرض أول " + MAX_SHOWN + " فرق." : "");
        txtSummary.setText(summary);
        if (rows.isEmpty()) rows.add("مفيش فروق في الجزء المتقارن");
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        say(txtStatus, "المقارنة خلصت: " + diffs + " فرق");
    }
}
