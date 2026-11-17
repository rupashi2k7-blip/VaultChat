package com.vaultchat.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM primitives, PBKDF2 and HKDF. All keys are 32 bytes. */
public final class Crypto {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private Crypto() {}

    public static byte[] rnd(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static byte[] pbkdf2(char[] pw, byte[] salt, int iters, int len) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw, salt, iters, len * 8);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] out = f.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** returns iv || ciphertext || tag */
    public static byte[] encrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = rnd(IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] blob) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN));
            return c.doFinal(blob, IV_LEN, blob.length - IV_LEN);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Writes iv first, then the AES-GCM encrypted stream. */
    public static void encryptStream(byte[] key, InputStream in, OutputStream out) throws Exception {
        byte[] iv = rnd(IV_LEN);
        out.write(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        CipherOutputStream cos = new CipherOutputStream(out, c);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            cos.write(buf, 0, n);
        }
        cos.close();
    }

    public static void decryptStream(byte[] key, InputStream in, OutputStream out) throws Exception {
        byte[] iv = new byte[IV_LEN];
        DataInputStream din = new DataInputStream(in);
        din.readFully(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        CipherInputStream cis = new CipherInputStream(din, c);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = cis.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    public static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                md.update(p);
            }
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] hkdf(byte[] ikm, byte[] salt, String info, int len) {
        try {
            byte[] useSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(useSalt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] infoB = info.getBytes("UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] t = new byte[0];
            int i = 1;
            while (out.size() < len) {
                mac.update(t);
                mac.update(infoB);
                mac.update((byte) i);
                t = mac.doFinal();
                out.write(t, 0, t.length);
                i++;
            }
            byte[] full = out.toByteArray();
            byte[] res = new byte[len];
            System.arraycopy(full, 0, res, 0, len);
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }

    public static String b64(byte[] b) {
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }

    public static byte[] unb64(String s) {
        if (s == null) return new byte[0];
        return android.util.Base64.decode(s, android.util.Base64.NO_WRAP);
    }
}
