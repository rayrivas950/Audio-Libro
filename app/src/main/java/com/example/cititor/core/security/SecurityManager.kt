package com.example.cititor.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class SecurityManager(context: Context) {

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDatabasePassphrase(): ByteArray {
        var passphrase = sharedPreferences.getString("db_pass", null)?.toByteArray(Charsets.UTF_8)
        if (passphrase == null) {
            passphrase = generateAndSavePassphrase()
        }
        return passphrase
    }

    private fun generateAndSavePassphrase(): ByteArray {
        val newPassphrase = (1..32).map { ('a'..'z').random() }.joinToString("").toByteArray(Charsets.UTF_8)
        sharedPreferences.edit().putString("db_pass", newPassphrase.toString(Charsets.UTF_8)).apply()
        return newPassphrase
    }
}