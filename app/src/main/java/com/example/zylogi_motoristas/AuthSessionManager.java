package com.example.zylogi_motoristas;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.io.IOException;
import java.security.GeneralSecurityException;

// O nome da classe foi alterado para ser mais específico
public class AuthSessionManager {

    private static final String FILE_NAME = "encrypted_session_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_USER_CPF = "user_cpf";

    private SharedPreferences sharedPreferences;

    public AuthSessionManager(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            sharedPreferences = EncryptedSharedPreferences.create(
                    FILE_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Could not create EncryptedSharedPreferences", e);
        }
    }

    public void saveAuthToken(String token) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.apply();
    }

    public String getAuthToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null);
    }

    public void clearSession() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    public void enableBiometric(String userCpf) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_BIOMETRIC_ENABLED, true);
        editor.putString(KEY_USER_CPF, userCpf);
        editor.apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    public String getSavedUserCpf() {
        return sharedPreferences.getString(KEY_USER_CPF, null);
    }

    public void disableBiometric() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_BIOMETRIC_ENABLED, false);
        editor.remove(KEY_USER_CPF);
        editor.apply();
    }
}