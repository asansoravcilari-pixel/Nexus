package com.nexus.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    public static final class Credentials {
        public final String username;
        public final String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public boolean isEmpty() {
            return username.isEmpty() && password.isEmpty();
        }
    }

    private static final String PREFS = "nexus_tabs_credentials";
    private static final String ALIAS = "nexus_tabs_aes_key";
    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return gen.generateKey();
    }

    public void put(String pageId, String username, String password) {
        try {
            JSONObject json = new JSONObject();
            json.put("u", username == null ? "" : username);
            json.put("p", password == null ? "" : password);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
            String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(pageId, value).apply();
        } catch (Exception ignored) {
        }
    }

    public Credentials get(String pageId) {
        try {
            String value = prefs.getString(pageId, "");
            if (value == null || value.isEmpty()) return new Credentials("", "");
            String[] parts = value.split("\\.", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            String raw = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);
            return new Credentials(json.optString("u", ""), json.optString("p", ""));
        } catch (Exception ignored) {
            return new Credentials("", "");
        }
    }

    public void remove(String pageId) {
        prefs.edit().remove(pageId).apply();
    }
}
