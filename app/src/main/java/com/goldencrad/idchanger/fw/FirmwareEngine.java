package com.goldencrad.idchanger.fw;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Byte-exact port of the Golden Crad ID Changer (Windows) firmware engine:
 * signature scan, SPHE CRC-32/MPEG-2 repair, hashing and hex helpers.
 */
public final class FirmwareEngine {

    public static final int SIG_BIN_0S = 0;
    public static final int SIG_SPHE = 1;

    private static final int[] MPEG2 = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int c = i << 24;
            for (int b = 0; b < 8; b++)
                c = ((c & 0x80000000) != 0) ? ((c << 1) ^ 0x04C11DB7) : (c << 1);
            MPEG2[i] = c;
        }
    }

    private FirmwareEngine() {}

    /** CRC-32/MPEG-2: poly 0x04C11DB7, init 0xFFFFFFFF, no reflection, no final XOR. */
    public static int crc32Mpeg2(byte[] d, int off, int len) {
        int c = 0xFFFFFFFF;
        for (int i = 0; i < len; i++)
            c = (c << 8) ^ MPEG2[((c >>> 24) ^ (d[off + i] & 0xFF)) & 0xFF];
        return c;
    }

    /** Standard CRC-32 (same as java.util.zip). */
    public static long crc32(byte[] d, int off, int len) {
        CRC32 c = new CRC32();
        c.update(d, off, len);
        return c.getValue();
    }

    public static class Sig {
        public int kind;          // SIG_BIN_0S or SIG_SPHE
        public long sigOffset;    // offset of the signature itself
        public long idOffset;     // offset where the ID starts
        public long idLen;        // chosen ID length in bytes
    }

    private static boolean match(byte[] d, long off, String s) {
        for (int i = 0; i < s.length(); i++)
            if (d[(int) off + i] != (byte) s.charAt(i)) return false;
        return true;
    }

    /**
     * Same algorithm as the Windows firmware_find_signature:
     * first "BIN_0S" occurrence with id at +9 that fits wins; otherwise the
     * first "SPHE" with id at +0x17 that fits; otherwise not found.
     */
    public static Sig findSignature(byte[] d, long idLen) {
        if (d == null || idLen <= 0) return null;
        long size = d.length;
        if (size > 5) {
            for (long i = 0; i <= size - 6; i++) {
                if (d[(int) i] == 'B' && match(d, i, "BIN_0S")) {
                    long idOff = i + 9;
                    if (size - idOff >= idLen) {
                        Sig s = new Sig();
                        s.kind = SIG_BIN_0S; s.sigOffset = i; s.idOffset = idOff; s.idLen = idLen;
                        return s;
                    }
                    break; // BIN_0S hit that does not fit -> fall through to SPHE
                }
            }
        }
        if (size > 3) {
            for (long i = 0; i <= size - 4; i++) {
                if (d[(int) i] == 'S' && match(d, i, "SPHE")) {
                    long idOff = i + 0x17;
                    if (size - idOff >= idLen) {
                        Sig s = new Sig();
                        s.kind = SIG_SPHE; s.sigOffset = i; s.idOffset = idOff; s.idLen = idLen;
                        return s;
                    }
                    return null; // first SPHE that does not fit -> fail
                }
            }
        }
        return null;
    }

    /** Info about a complete 256-byte SPHE block. */
    public static class Sphe {
        public boolean found;
        public long offset;   // offset of "SPHE"
    }

    /** Find first "SPHE" with at least 256 bytes remaining (Windows repair rule). */
    public static Sphe findSpheBlock(byte[] d) {
        Sphe r = new Sphe();
        if (d == null || d.length <= 3) return r;
        for (long i = 0; i <= d.length - 4; i++) {
            if (d[(int) i] == 'S' && match(d, i, "SPHE")) {
                if (d.length - i >= 256) {
                    r.found = true;
                    r.offset = i;
                }
                return r;
            }
        }
        return r;
    }

    /** Stored SPHE CRC: little-endian u32 at block offset +0xFC. */
    public static int storedSpheCrc(byte[] d, long spheOff) {
        int o = (int) spheOff + 0xFC;
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8)
                | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24);
    }

    /** Calculated SPHE CRC: CRC-32/MPEG-2 over the first 252 bytes of the block. */
    public static int calcSpheCrc(byte[] d, long spheOff) {
        return crc32Mpeg2(d, (int) spheOff, 0xFC);
    }

    public static void writeSpheCrc(byte[] d, long spheOff, int crc) {
        int o = (int) spheOff + 0xFC;
        d[o] = (byte) (crc & 0xFF);
        d[o + 1] = (byte) ((crc >>> 8) & 0xFF);
        d[o + 2] = (byte) ((crc >>> 16) & 0xFF);
        d[o + 3] = (byte) ((crc >>> 24) & 0xFF);
    }

    /**
     * Recompute and write the SPHE CRC (Windows firmware_repair_sphe_crc).
     * Returns null when no complete SPHE block exists; otherwise {offset, oldCrc, newCrc}.
     */
    public static long[] repairSpheCrc(byte[] d) {
        Sphe s = findSpheBlock(d);
        if (!s.found) return null;
        int oldCrc = storedSpheCrc(d, s.offset);
        int newCrc = calcSpheCrc(d, s.offset);
        writeSpheCrc(d, s.offset, newCrc);
        return new long[]{s.offset, oldCrc & 0xFFFFFFFFL, newCrc & 0xFFFFFFFFL};
    }

    public static String md5(byte[] d) { return digest("MD5", d); }
    public static String sha1(byte[] d) { return digest("SHA-1", d); }
    public static String sha256(byte[] d) { return digest("SHA-256", d); }

    private static String digest(String algo, byte[] d) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            return bytesToHex(md.digest(d), 0, md.getDigestLength(), "");
        } catch (Exception e) {
            return "?";
        }
    }

    public static String bytesToHex(byte[] d, int off, int len, String sep) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(sep);
            sb.append(String.format(Locale.US, "%02X", d[off + i] & 0xFF));
        }
        return sb.toString();
    }

    /** Parse hex bytes, spaces allowed. Throws IllegalArgumentException on bad input. */
    public static byte[] parseHex(String s) {
        if (s == null) throw new IllegalArgumentException("empty");
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r') continue;
            if (Character.digit(c, 16) < 0) throw new IllegalArgumentException("bad char: " + c);
            clean.append(c);
        }
        if (clean.length() == 0 || (clean.length() % 2) != 0)
            throw new IllegalArgumentException("bad length");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static long parseHexLong(String s) {
        if (s == null) throw new IllegalArgumentException("empty");
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.isEmpty()) throw new IllegalArgumentException("empty");
        return Long.parseUnsignedLong(s, 16);
    }

    /** Hash summary like the Windows tool shows after loading a file. */
    public static String hashSummary(byte[] d) {
        StringBuilder sb = new StringBuilder();
        sb.append("MD5: ").append(md5(d)).append('\n');
        sb.append("SHA-1: ").append(sha1(d)).append('\n');
        sb.append("SHA-256: ").append(sha256(d)).append('\n');
        sb.append(String.format(Locale.US, "CRC-32: 0x%08X", crc32(d, 0, d.length))).append('\n');
        int n = Math.min(16, d.length);
        sb.append("أول ").append(n).append(" بايت: ")
          .append(bytesToHex(d, 0, n, " "));
        return sb.toString();
    }
}
