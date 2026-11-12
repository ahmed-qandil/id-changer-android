package com.goldencrad.idchanger.fw;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch patch queue with JSON profiles in the exact Windows schema:
 * an array of {"offset": n, "length": n, "value": "hex", "source": "..."}.
 */
public class PatchQueue {
    public final List<Patch> patches = new ArrayList<>();

    public void add(Patch p) {
        if (p.value == null || p.value.length == 0)
            throw new IllegalArgumentException("الباتش لازم يحتوي بايت واحد على الأقل");
        if (p.value.length != p.length)
            throw new IllegalArgumentException("طول القيمة " + p.value.length + " بايت مش مطابق للطول المطلوب " + p.length);
        patches.add(p);
    }

    public void remove(int idx) { patches.remove(idx); }
    public void clear() { patches.clear(); }
    public int size() { return patches.size(); }

    /** Apply into a copy of target; returns {applied, skippedOutOfRange}. */
    public long[] apply(byte[] target) {
        long applied = 0, skipped = 0;
        for (Patch p : patches) {
            if (p.offset >= 0 && p.offset + p.length <= target.length) {
                System.arraycopy(p.value, 0, target, (int) p.offset, (int) p.length);
                applied++;
            } else {
                skipped++;
            }
        }
        return new long[]{applied, skipped};
    }

    public String toJson() {
        JSONArray arr = new JSONArray();
        try {
            for (Patch p : patches) {
                JSONObject o = new JSONObject();
                o.put("offset", p.offset);
                o.put("length", p.length);
                o.put("value", FirmwareEngine.bytesToHex(p.value, 0, p.value.length, ""));
                if (!p.source.isEmpty()) o.put("source", p.source);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        try {
            return arr.toString(2);
        } catch (Exception e) {
            return arr.toString();
        }
    }

    /** Parse a Windows-format JSON profile. Throws with an Arabic message on bad input. */
    public static PatchQueue fromJson(String text) {
        PatchQueue q = new PatchQueue();
        try {
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.has("offset") || !o.has("length") || !o.has("value"))
                    throw new IllegalArgumentException("باتش رقم " + (i + 1) + ": ناقص offset أو length أو value");
                long offset = o.getLong("offset");
                long length = o.getLong("length");
                byte[] value = FirmwareEngine.parseHex(o.getString("value"));
                String source = o.optString("source", "");
                q.add(new Patch(offset, length, value, source));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("ملف البروفايل مش صالح: " + e.getMessage());
        }
        return q;
    }
}
