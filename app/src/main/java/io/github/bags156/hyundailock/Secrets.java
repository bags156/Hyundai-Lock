package io.github.bags156.hyundailock;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts the Bluelink password and PIN with a key that lives in the Android keystore and never
 * leaves it. Uninstalling the app destroys the key.
 */
final class Secrets {

    private Secrets() {
    }

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "bluelink-credentials";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    static String encrypt(String plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        ByteBuffer out = ByteBuffer.allocate(1 + iv.length + sealed.length);
        out.put((byte) iv.length).put(iv).put(sealed);
        return Base64.encodeToString(out.array(), Base64.NO_WRAP);
    }

    static String decrypt(String encoded) throws GeneralSecurityException {
        byte[] all;
        try {
            all = Base64.decode(encoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Saved credential is damaged", e);
        }
        if (all.length < 1 + IV_BYTES) {
            throw new GeneralSecurityException("Saved credential is damaged");
        }
        int ivLength = all[0];
        if (ivLength <= 0 || all.length <= 1 + ivLength) {
            throw new GeneralSecurityException("Saved credential is damaged");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 1, ivLength));
        byte[] plain = cipher.doFinal(all, 1 + ivLength, all.length - 1 - ivLength);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** Deletes the key, making anything encrypted with it unreadable. */
    static void destroy() {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (store.containsAlias(ALIAS)) {
                store.deleteEntry(ALIAS);
            }
        } catch (GeneralSecurityException | IOException e) {
            // Nothing more to do; the encrypted values are cleared separately.
        }
    }

    private static synchronized SecretKey key() throws GeneralSecurityException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        try {
            store.load(null);
        } catch (IOException | CertificateException e) {
            throw new GeneralSecurityException("Android keystore unavailable", e);
        }
        KeyStore.Entry entry = store.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
