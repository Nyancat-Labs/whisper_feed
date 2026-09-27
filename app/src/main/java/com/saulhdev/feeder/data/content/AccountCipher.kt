/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.data.content

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the account's values before they are written down, and opens them
 * again.
 *
 * Each value is sealed with the name it is stored under, so a sealed token
 * copied into the place of the user name will not open: the name is part of
 * what the seal checks.
 */
interface AccountCipher {
    fun seal(plain: String, name: String): String
    fun open(sealed: String, name: String): String

    /** Throws the key away. The next seal makes a new one. */
    fun forget()
}

/**
 * AES-GCM with a key from [key]. The IV is the cipher's own, fresh for every
 * seal, and travels in front of the sealed bytes.
 */
internal class GcmCipher(
    private val key: () -> SecretKey,
    private val dropKey: () -> Unit,
) : AccountCipher {

    @Volatile
    private var cached: SecretKey? = null

    private fun currentKey(): SecretKey = cached ?: key().also { cached = it }

    override fun seal(plain: String, name: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, currentKey())
        cipher.updateAAD(name.toByteArray())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length ${iv.size}" }
        return Base64.encodeToString(iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun open(sealed: String, name: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Too short to have been sealed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, currentKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.updateAAD(name.toByteArray())
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    override fun forget() {
        cached = null
        dropKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/** The account's own key, used for nothing else, never leaving the Keystore. */
private const val ACCOUNT_KEY = "whisper_account_key"

private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

/** The cipher the app uses: [GcmCipher] with a 256-bit key in the Android Keystore. */
internal fun keystoreCipher(): AccountCipher = GcmCipher(
    key = {
        androidKeyStore().getKey(ACCOUNT_KEY, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        ACCOUNT_KEY,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
    },
    dropKey = { androidKeyStore().deleteEntry(ACCOUNT_KEY) },
)

/**
 * Where the account lived until September 2026: an EncryptedSharedPreferences
 * file, from a library Google has deprecated. Read once, to move the account
 * out, and then deleted with its key. The library stays in the build for as
 * long as a phone might still be carrying the old file.
 */
interface LegacyAccountStore {
    fun open(): SharedPreferences
    fun forgetKey()
}

@Suppress("DEPRECATION") // The deprecated library, read once; see above.
internal class EncryptedLegacyStore(context: Context, private val file: String) : LegacyAccountStore {
    private val appContext = context.applicationContext

    override fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        file,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** The library's master key. Nothing else in the app used it. */
    override fun forgetKey() {
        androidKeyStore().deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    }
}
