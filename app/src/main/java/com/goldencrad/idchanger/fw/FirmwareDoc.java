package com.goldencrad.idchanger.fw;

import android.net.Uri;

/** A firmware file loaded fully into memory: name, source uri and raw bytes. */
public class FirmwareDoc {
    public String name;
    public Uri uri;
    public byte[] data;

    public FirmwareDoc(String name, Uri uri, byte[] data) {
        this.name = name;
        this.uri = uri;
        this.data = data;
    }

    public long size() {
        return data == null ? 0 : data.length;
    }
}
