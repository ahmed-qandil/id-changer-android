package com.goldencrad.idchanger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.goldencrad.idchanger.fw.FirmwareDoc;
import com.goldencrad.idchanger.fw.FirmwareStore;
import com.goldencrad.idchanger.ui.BaseActivity;
import com.goldencrad.idchanger.ui.BatchActivity;
import com.goldencrad.idchanger.ui.CompareActivity;
import com.goldencrad.idchanger.ui.Editor1506Activity;
import com.goldencrad.idchanger.ui.HexViewerActivity;
import com.goldencrad.idchanger.ui.SearchActivity;
import com.goldencrad.idchanger.ui.VanillaActivity;

public class MainActivity extends BaseActivity {

    private TextView txtState;

    private static final String[] MENU = {
            "محرر أيدي 1506 - تعديل الأيدي بأوفسيتات ثابتة",
            "أدوات فانيلا - كشف ونسخ الأيدي في أي سوفت",
            "عارض الهيكس - شوف بايتات السوفت",
            "بحث - دور على بايتات أو نص في السوفت",
            "مقارنة سوفتين - الفروق بايت-بايت",
            "قائمة الباتشات - باتشات جماعية ببروفايل JSON",
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        ListView list = findViewById(R.id.menu);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, MENU);
        list.setAdapter(ad);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Class<?> c;
            switch (pos) {
                case 0: c = Editor1506Activity.class; break;
                case 1: c = VanillaActivity.class; break;
                case 2: c = HexViewerActivity.class; break;
                case 3: c = SearchActivity.class; break;
                case 4: c = CompareActivity.class; break;
                default: c = BatchActivity.class; break;
            }
            startActivity(new Intent(this, c));
        });

        Button btnTree = findViewById(R.id.btnTree);
        btnTree.setOnClickListener(v -> pickTree());
        txtState = findViewById(R.id.txtState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    @Override
    protected void onTreeSet() {
        refreshState();
        say(txtState, "مجلد الحفظ اتظبط: " + FirmwareStore.treeName);
    }

    private void refreshState() {
        StringBuilder sb = new StringBuilder();
        sb.append("مجلد الحفظ: ").append(FirmwareStore.treeUri == null ? "مش محدد (هتختار مكان عند كل حفظ)" : FirmwareStore.treeName).append('\n');
        for (String k : FirmwareStore.SLOT_KEYS) {
            FirmwareDoc d = FirmwareStore.get(k);
            sb.append(FirmwareStore.arabicName(k)).append(": ");
            sb.append(d == null ? "فاضي" : d.name + " (" + d.size() + " بايت)");
            sb.append('\n');
        }
        sb.append("الباتشات في القائمة: ").append(FirmwareStore.patchQueue.size());
        txtState.setText(sb.toString());
    }
}
