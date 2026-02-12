package com.pocketssh.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.pocketssh.app.ssh.SshKeyInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.crypto.util.PrivateKeyFactory

import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.StringReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KeyStorageManager"
private const val KEYSTORE_ALIAS = "ssh_master_key"
private const val KEYS_DIR = "ssh_keys"
private const val METADATA_FILE = "key_metadata.json"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 128

/**
 * Secure SSH key storage backed by AES-256-GCM with an
 * Android Keystore master key.
 *
 * Private keys are encrypted at rest in `context.filesDir/ssh_keys/`.
 * All plaintext key material is zeroed in `finally` blocks after use.
 */
@Singleton
class KeyStorageManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : KeyStorageManager {

    private val mutex = Mutex()

    init {
        ensureMasterKeyExists()
    }

    // -- Key generation --------------------------------------------------------

    override suspend fun generateEd25519Key(name: String): SshKeyInfo =
        withContext(Dispatchers.IO) {
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPair = generator.generateKeyPair()

            val privateKey = keyPair.private as Ed25519PrivateKeyParameters
            val publicKey = keyPair.public as Ed25519PublicKeyParameters

            val pemBytes = toOpenSshPem(privateKey)
            try {
                val publicKeyOpenSsh = encodeEd25519PublicKeyOpenSsh(publicKey, name)
                val fingerprint = computeFingerprint(publicKey)
                val keyId = UUID.randomUUID().toString()

                encryptAndStore(keyId, pemBytes)
                savePublicKey(keyId, publicKeyOpenSsh)

                val info = SshKeyInfo(
                    id = keyId,
                    name = name,
                    algorithm = "ed25519",
                    fingerprintSha256 = fingerprint,
                    createdAt = System.currentTimeMillis(),
                )
                mutex.withLock { appendMetadata(info) }
                Log.i(TAG, "Generated Ed25519 key: $keyId ($name)")
                info
            } finally {
                pemBytes.fill(0)
            }
        }

    override suspend fun generateRsaKey(name: String): SshKeyInfo =
        withContext(Dispatchers.IO) {
            val generator = RSAKeyPairGenerator()
            generator.init(
                RSAKeyGenerationParameters(
                    BigInteger.valueOf(65537),
                    SecureRandom(),
                    4096,
                    80,
                )
            )
            val keyPair = generator.generateKeyPair()

            val pemBytes = toOpenSshPem(keyPair.private as AsymmetricKeyParameter)
            try {
                val rsaPub = keyPair.public as RSAKeyParameters
                val publicKeyOpenSsh = encodeRsaPublicKeyOpenSsh(rsaPub, name)
                val fingerprint = computeRsaFingerprint(rsaPub)
                val keyId = UUID.randomUUID().toString()

                encryptAndStore(keyId, pemBytes)
                savePublicKey(keyId, publicKeyOpenSsh)

                val info = SshKeyInfo(
                    id = keyId,
                    name = name,
                    algorithm = "rsa-4096",
                    fingerprintSha256 = fingerprint,
                    createdAt = System.currentTimeMillis(),
                )
                mutex.withLock { appendMetadata(info) }
                Log.i(TAG, "Generated RSA-4096 key: $keyId ($name)")
                info
            } finally {
                pemBytes.fill(0)
            }
        }

    // -- Key import ------------------------------------------------------------

    override suspend fun importKey(
        name: String,
        privateKeyBytes: ByteArray,
        passphrase: CharArray?,
    ): SshKeyInfo = withContext(Dispatchers.IO) {
        var pemBytes: ByteArray? = null
        try {
            val pemString = String(privateKeyBytes, Charsets.UTF_8)
            val parsed = parsePemPrivateKey(pemString, passphrase)

            // For OpenSSH format keys, store the original PEM directly —
            // avoids a round-trip through BouncyCastle that can alter the binary encoding.
            // For other formats (PKCS#1, PKCS#8), convert to OpenSSH PEM.
            pemBytes = if (pemString.trimStart().startsWith("-----BEGIN OPENSSH PRIVATE KEY-----")) {
                pemString.trim().toByteArray(Charsets.UTF_8)
            } else {
                toOpenSshPem(parsed.bcPrivateKey)
            }

            val publicKeyOpenSsh = parsed.publicKeyOpenSsh(name)
            val fingerprint = parsed.fingerprint()
            val keyId = UUID.randomUUID().toString()

            encryptAndStore(keyId, pemBytes)
            savePublicKey(keyId, publicKeyOpenSsh)

            val info = SshKeyInfo(
                id = keyId,
                name = name,
                algorithm = parsed.algorithm,
                fingerprintSha256 = fingerprint,
                createdAt = System.currentTimeMillis(),
            )
            mutex.withLock { appendMetadata(info) }
            Log.i(TAG, "Imported key: $keyId ($name, ${parsed.algorithm})")
            info
        } finally {
            privateKeyBytes.fill(0)
            pemBytes?.fill(0)
            passphrase?.fill('\u0000')
        }
    }

    // -- Key retrieval ---------------------------------------------------------

    override suspend fun getDecryptedPrivateKey(keyId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val encFile = encryptedKeyFile(keyId)
            if (!encFile.exists()) return@withContext null
            val encrypted = encFile.readBytes()
            decrypt(encrypted, keyId.toByteArray(Charsets.UTF_8))
        }

    override suspend fun getPublicKeyString(keyId: String): String? =
        withContext(Dispatchers.IO) {
            val pubFile = publicKeyFile(keyId)
            if (!pubFile.exists()) return@withContext null
            pubFile.readText(Charsets.UTF_8)
        }

    // -- Key listing -----------------------------------------------------------

    override suspend fun listKeys(): List<SshKeyInfo> =
        withContext(Dispatchers.IO) {
            mutex.withLock { loadAllMetadata() }
        }

    // -- Key deletion ----------------------------------------------------------

    override suspend fun deleteKey(keyId: String): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                secureDelete(encryptedKeyFile(keyId))
                publicKeyFile(keyId).delete()
                removeMetadataLocked(keyId)
                Log.i(TAG, "Deleted key: $keyId")
            }
        }

    // -- Key rename ------------------------------------------------------------

    override suspend fun renameKey(keyId: String, newName: String): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val allMeta = loadAllMetadata().toMutableList()
                val idx = allMeta.indexOfFirst { it.id == keyId }
                if (idx < 0) return@withContext
                allMeta[idx] = allMeta[idx].copy(name = newName)
                writeAllMetadata(allMeta)
                Log.i(TAG, "Renamed key $keyId to '$newName'")
            }
        }

    // ==========================================================================
    //  Internal: Android Keystore AES-256-GCM
    // ==========================================================================

    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun getMasterKey(): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        // Prepend IV to ciphertext: [IV (12 bytes)][ciphertext + GCM tag]
        return ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(encrypted: ByteArray, associatedData: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encrypted)
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    // ==========================================================================
    //  Internal: File I/O
    // ==========================================================================

    private fun keysDir(): File =
        File(context.filesDir, KEYS_DIR).also { it.mkdirs() }

    private fun encryptedKeyFile(keyId: String): File =
        File(keysDir(), "$keyId.enc")

    private fun publicKeyFile(keyId: String): File =
        File(keysDir(), "$keyId.pub")

    private fun metadataFile(): File =
        File(keysDir(), METADATA_FILE)

    private fun encryptAndStore(keyId: String, plaintext: ByteArray) {
        val encrypted = encrypt(plaintext, keyId.toByteArray(Charsets.UTF_8))
        encryptedKeyFile(keyId).writeBytes(encrypted)
    }

    private fun savePublicKey(keyId: String, publicKeyOpenSsh: String) {
        publicKeyFile(keyId).writeText(publicKeyOpenSsh, Charsets.UTF_8)
    }

    /**
     * Overwrite file contents with random data before deleting.
     * Limited effectiveness on flash storage but better than plain deletion.
     */
    private fun secureDelete(file: File) {
        if (!file.exists()) return
        val random = SecureRandom()
        val size = file.length().toInt()
        if (size > 0) {
            val randomBytes = ByteArray(size)
            try {
                random.nextBytes(randomBytes)
                file.writeBytes(randomBytes)
            } finally {
                randomBytes.fill(0)
            }
        }
        file.delete()
    }

    // ==========================================================================
    //  Internal: Metadata persistence (JSON)
    //  Methods suffixed with "Locked" must be called while holding [mutex].
    // ==========================================================================

    /** Append a single key's metadata. Must be called while holding [mutex]. */
    private fun appendMetadata(info: SshKeyInfo) {
        val allMeta = loadAllMetadata().toMutableList()
        allMeta.add(info)
        writeAllMetadata(allMeta)
    }

    /** Read all key metadata from disk. Does not require the mutex. */
    private fun loadAllMetadata(): List<SshKeyInfo> {
        val file = metadataFile()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SshKeyInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    algorithm = obj.getString("algorithm"),
                    fingerprintSha256 = obj.getString("fingerprintSha256"),
                    createdAt = obj.getLong("createdAt"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load key metadata", e)
            emptyList()
        }
    }

    private fun writeAllMetadata(keys: List<SshKeyInfo>) {
        val arr = JSONArray()
        for (k in keys) {
            arr.put(
                JSONObject().apply {
                    put("id", k.id)
                    put("name", k.name)
                    put("algorithm", k.algorithm)
                    put("fingerprintSha256", k.fingerprintSha256)
                    put("createdAt", k.createdAt)
                }
            )
        }
        metadataFile().writeText(arr.toString(2), Charsets.UTF_8)
    }

    /** Must be called while holding [mutex]. */
    private fun removeMetadataLocked(keyId: String) {
        val allMeta = loadAllMetadata().toMutableList()
        allMeta.removeAll { it.id == keyId }
        writeAllMetadata(allMeta)
    }

    // ==========================================================================
    //  Internal: PEM parsing
    // ==========================================================================

    private sealed class ParsedKey {
        abstract val bcPrivateKey: AsymmetricKeyParameter
        abstract val algorithm: String
        abstract fun publicKeyOpenSsh(comment: String): String
        abstract fun fingerprint(): String

        class Ed25519Key(
            val privateKeyParams: Ed25519PrivateKeyParameters,
            val publicKeyParams: Ed25519PublicKeyParameters,
        ) : ParsedKey() {
            override val bcPrivateKey: AsymmetricKeyParameter get() = privateKeyParams
            override val algorithm = "ed25519"
            override fun publicKeyOpenSsh(comment: String) =
                encodeEd25519PublicKeyOpenSsh(publicKeyParams, comment)
            override fun fingerprint() = computeFingerprint(publicKeyParams)
        }

        class RsaKey(
            val privateKeyParams: RSAPrivateCrtKeyParameters,
            val publicKey: RSAPublicKey,
        ) : ParsedKey() {
            override val bcPrivateKey: AsymmetricKeyParameter get() = privateKeyParams
            override val algorithm = "rsa-4096"
            override fun publicKeyOpenSsh(comment: String): String {
                val params = RSAKeyParameters(false, publicKey.modulus, publicKey.publicExponent)
                return encodeRsaPublicKeyOpenSsh(params, comment)
            }
            override fun fingerprint(): String {
                val params = RSAKeyParameters(false, publicKey.modulus, publicKey.publicExponent)
                return computeRsaFingerprint(params)
            }
        }
    }

    private fun parsePemPrivateKey(pemString: String, passphrase: CharArray?): ParsedKey {
        // OpenSSH format is NOT supported by BouncyCastle PEMParser — handle separately
        if (pemString.trimStart().startsWith("-----BEGIN OPENSSH PRIVATE KEY-----")) {
            return parseOpenSshPrivateKey(pemString)
        }

        val reader = PEMParser(StringReader(pemString))
        val pemObject = reader.readObject()
            ?: throw IllegalArgumentException("No PEM object found in key data")

        val converter = JcaPEMKeyConverter()

        val javaKeyPair: KeyPair = when (pemObject) {
            is PEMEncryptedKeyPair -> {
                requireNotNull(passphrase) { "Key is passphrase-protected but no passphrase provided" }
                val decryptor = JcePEMDecryptorProviderBuilder().build(passphrase)
                converter.getKeyPair(pemObject.decryptKeyPair(decryptor))
            }
            is PEMKeyPair -> {
                converter.getKeyPair(pemObject)
            }
            is PKCS8EncryptedPrivateKeyInfo -> {
                requireNotNull(passphrase) { "Key is passphrase-protected but no passphrase provided" }
                val decProvider = JceOpenSSLPKCS8DecryptorProviderBuilder().build(passphrase)
                val privKeyInfo = pemObject.decryptPrivateKeyInfo(decProvider)
                val privKey = converter.getPrivateKey(privKeyInfo)
                KeyPair(null, privKey)
            }
            is PrivateKeyInfo -> {
                val privKey = converter.getPrivateKey(pemObject)
                KeyPair(null, privKey)
            }
            else -> throw IllegalArgumentException(
                "Unsupported PEM key format: ${pemObject.javaClass.simpleName}"
            )
        }

        val privKey = javaKeyPair.private
        return when (privKey) {
            is org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey -> {
                val encoded = privKey.encoded
                val privKeyInfo = PrivateKeyInfo.getInstance(encoded)
                val bcPriv = PrivateKeyFactory.createKey(privKeyInfo) as Ed25519PrivateKeyParameters
                ParsedKey.Ed25519Key(
                    privateKeyParams = bcPriv,
                    publicKeyParams = bcPriv.generatePublicKey(),
                )
            }
            is RSAPrivateCrtKey -> {
                val pubKey = if (javaKeyPair.public != null) {
                    javaKeyPair.public as RSAPublicKey
                } else {
                    val factory = KeyFactory.getInstance("RSA")
                    factory.generatePublic(
                        RSAPublicKeySpec(privKey.modulus, privKey.publicExponent)
                    ) as RSAPublicKey
                }
                val bcPriv = RSAPrivateCrtKeyParameters(
                    privKey.modulus,
                    privKey.publicExponent,
                    privKey.privateExponent,
                    privKey.primeP,
                    privKey.primeQ,
                    privKey.primeExponentP,
                    privKey.primeExponentQ,
                    privKey.crtCoefficient,
                )
                ParsedKey.RsaKey(
                    privateKeyParams = bcPriv,
                    publicKey = pubKey,
                )
            }
            else -> throw IllegalArgumentException(
                "Unsupported private key type: ${privKey.javaClass.simpleName}"
            )
        }
    }

    /**
     * Parse an OpenSSH-format private key (`-----BEGIN OPENSSH PRIVATE KEY-----`).
     *
     * BouncyCastle's [PEMParser] does not support this format, so we extract
     * the base64 body manually and use [OpenSSHPrivateKeyUtil.parsePrivateKeyBlob].
     */
    private fun parseOpenSshPrivateKey(pemString: String): ParsedKey {
        val b64 = pemString.lines()
            .filter { !it.startsWith("-----") && it.isNotBlank() }
            .joinToString("")
        val blob = Base64.decode(b64, Base64.DEFAULT)

        val keyParams = try {
            OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(blob)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException(
                "Encrypted OpenSSH keys are not supported. " +
                    "Remove the passphrase first with: ssh-keygen -p -f <keyfile>",
                e,
            )
        }

        return when (keyParams) {
            is Ed25519PrivateKeyParameters -> {
                ParsedKey.Ed25519Key(
                    privateKeyParams = keyParams,
                    publicKeyParams = keyParams.generatePublicKey(),
                )
            }
            is RSAPrivateCrtKeyParameters -> {
                val factory = KeyFactory.getInstance("RSA")
                val pubKey = factory.generatePublic(
                    RSAPublicKeySpec(keyParams.modulus, keyParams.publicExponent)
                ) as RSAPublicKey
                ParsedKey.RsaKey(
                    privateKeyParams = keyParams,
                    publicKey = pubKey,
                )
            }
            else -> throw IllegalArgumentException(
                "Unsupported OpenSSH key type: ${keyParams.javaClass.simpleName}"
            )
        }
    }

    /**
     * Encode a BouncyCastle private key as OpenSSH PEM text bytes.
     *
     * The returned bytes are the full PEM including `-----BEGIN OPENSSH PRIVATE KEY-----`
     * header, base64 body, and footer. This format is directly consumable by
     * ConnectBot sshlib's PEMDecoder.
     */
    private fun toOpenSshPem(keyParams: AsymmetricKeyParameter): ByteArray {
        val openSshBytes = OpenSSHPrivateKeyUtil.encodePrivateKey(keyParams)
        try {
            val b64 = Base64.encodeToString(openSshBytes, Base64.NO_WRAP)
            val sb = StringBuilder()
            sb.append("-----BEGIN OPENSSH PRIVATE KEY-----\n")
            for (i in b64.indices step 70) {
                sb.append(b64, i, minOf(i + 70, b64.length))
                sb.append('\n')
            }
            sb.append("-----END OPENSSH PRIVATE KEY-----\n")
            return sb.toString().toByteArray(Charsets.UTF_8)
        } finally {
            openSshBytes.fill(0)
        }
    }

    // ==========================================================================
    //  Internal: OpenSSH public key encoding & fingerprinting
    // ==========================================================================

    companion object {

        internal fun encodeEd25519PublicKeyOpenSsh(
            publicKey: Ed25519PublicKeyParameters,
            comment: String,
        ): String {
            val encoded = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
            val b64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
            return buildString {
                append("ssh-ed25519 ")
                append(b64)
                if (comment.isNotEmpty()) {
                    append(' ')
                    append(comment)
                }
            }
        }

        internal fun encodeRsaPublicKeyOpenSsh(
            publicKey: RSAKeyParameters,
            comment: String,
        ): String {
            val encoded = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
            val b64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
            return buildString {
                append("ssh-rsa ")
                append(b64)
                if (comment.isNotEmpty()) {
                    append(' ')
                    append(comment)
                }
            }
        }

        /**
         * Compute SHA-256 fingerprint in the format `SHA256:<base64>`,
         * matching OpenSSH `ssh-keygen -l` output.
         */
        internal fun computeFingerprint(publicKey: Ed25519PublicKeyParameters): String {
            val encoded = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
            return sha256Fingerprint(encoded)
        }

        internal fun computeRsaFingerprint(publicKey: RSAKeyParameters): String {
            val encoded = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
            return sha256Fingerprint(encoded)
        }

        private fun sha256Fingerprint(publicKeyBlob: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKeyBlob)
            val b64 = Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING)
            return "SHA256:$b64"
        }
    }
}
