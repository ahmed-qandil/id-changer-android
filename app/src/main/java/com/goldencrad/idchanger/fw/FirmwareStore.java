package com.goldencrad.idchanger.fw;

import android.net.Uri;

import java.util.LinkedHashMap;
import java.util.Map;

/** App-wide state: the 4 named firmware slots, the shared patch queue, the save folder. */
public final class FirmwareStore {
    public static final String S1506_ORIG = "1506 Original";
    public static final String S1506_TGT = "1506 Target";
    public static final String VAN_ORIG = "Vanilla Original";
    public static final String VAN_TGT = "Vanilla Target";

    public static final String[] SLOT_KEYS = {S1506_ORIG, S1506_TGT, VAN_ORIG, VAN_TGT};

    private static final Map<String, FirmwareDoc> slots = new LinkedHashMap<>();

    public static final PatchQueue patchQueue = new PatchQueue();

    public static Uri treeUri;      // user-chosen save folder (SAF tree), nullable
    public static String treeName;  // display name of that folder

    private FirmwareStore() {}

    public static synchronized FirmwareDoc get(String slot) { return slots.get(slot); }

    public static synchronized void put(String slot, FirmwareDoc doc) { slots.put(slot, doc); }

    public static String arabicName(String slot) {
        switch (slot) {
            case S1506_ORIG: return "1506 الأصلي";
            case S1506_TGT: return "1506 التارجت";
            case VAN_ORIG: return "فانيلا الأصلي";
            case VAN_TGT: return "فانيلا التارجت";
            default: return slot;
        }
    }

    /** Arabic spinner labels for all slots, with (محمّل) marker. */
    public static String[] slotLabels() {
        String[] out = new String[SLOT_KEYS.length];
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            out[i] = arabicName(SLOT_KEYS[i]) + (get(SLOT_KEYS[i]) != null ? " (محمّل)" : " (فاضي)");
        }
        return out;
    }
}
