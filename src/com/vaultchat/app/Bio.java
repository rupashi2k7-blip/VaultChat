package com.vaultchat.app;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Fingerprint unlock. The master key is wrapped by a hardware-backed Keystore key
 * that can only be used after a successful fingerprint authentication.
 */
public final class Bio {

    private static final String ALIAS = "vaultchat_bio_v1";
    private static final String PREFS = "vc";

    private Bio() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(false)
                .build());
        return kg.generateKey();
    }

    public static boolean available(Context c) {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            FingerprintManager fm = (FingerprintManager) c.getSystemService(Context.FINGERPRINT_SERVICE);
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean enrolled(Context c) {
        return c.getSharedPreferences(PREFS, 0).contains("bio_blob");
    }

    public static void enroll(Context c, byte[] mk) {
        try {
            if (!available(c)) return;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] blob = cipher.doFinal(mk);
            c.getSharedPreferences(PREFS, 0).edit()
                    .putString("bio_iv", Crypto.b64(cipher.getIV()))
                    .putString("bio_blob", Crypto.b64(blob))
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void disable(Context c) {
        c.getSharedPreferences(PREFS, 0).edit().remove("bio_blob").remove("bio_iv").apply();
    }

    public static Cipher decryptCipher(Context c) throws Exception {
        byte[] iv = Crypto.unb64(c.getSharedPreferences(PREFS, 0).getString("bio_iv", ""));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return cipher;
    }

    public static byte[] unwrap(Context c, Cipher cipher) throws Exception {
        byte[] blob = Crypto.unb64(c.getSharedPreferences(PREFS, 0).getString("bio_blob", ""));
        return cipher.doFinal(blob);
    }
}
