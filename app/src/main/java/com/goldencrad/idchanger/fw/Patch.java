package com.goldencrad.idchanger.fw;

/** One batch patch: write value bytes at offset, value.length == length. */
public class Patch {
    public long offset;
    public long length;
    public byte[] value;
    public String source;

    public Patch(long offset, long length, byte[] value, String source) {
        this.offset = offset;
        this.length = length;
        this.value = value;
        this.source = source == null ? "" : source;
    }

    public String describe() {
        return String.format("أوفسيت 0x%X - طول %d - قيمة %s%s",
                offset, length,
                FirmwareEngine.bytesToHex(value, 0, value.length, " "),
                source.isEmpty() ? "" : " - " + source);
    }
}
