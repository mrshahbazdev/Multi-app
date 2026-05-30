package com.appcloner.app

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner as ApkSigTool
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*

/**
 * Signs modified APK files.
 *
 * Uses Google's apksig library to produce APK Signature Scheme v1 + v2 + v3
 * signatures. v2/v3 are required to install on Android 7+/11+ for apps that
 * target modern SDK levels — a v1-only ("JAR") signature is rejected by the
 * package installer on those devices, which is the most common reason a clone
 * fails to install on a non-rooted phone.
 *
 * The signing key/certificate are generated once with BouncyCastle and stored
 * in an app-private BKS keystore.
 */
class ApkSigner(private val context: Context) {

    companion object {
        private const val TAG = "ApkSigner"
        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val keystoreFile: File
        get() = File(context.filesDir, "clone_keystore.bks")

    private val keystorePassword = "clone_ks_pass"
    private val keyAlias = "clone_key"
    private val keyPassword = "clone_key_pass"

    /**
     * Sign an APK file. Returns path to the signed APK.
     */
    fun signApk(apkPath: String): String {
        val inputFile = File(apkPath)
        val outputFile = File(inputFile.parent, "signed.apk")
        if (outputFile.exists()) outputFile.delete()

        ensureKeystore()

        val keystore = KeyStore.getInstance("BKS", "BC")
        FileInputStream(keystoreFile).use { fis ->
            keystore.load(fis, keystorePassword.toCharArray())
        }

        val privateKey = keystore.getKey(keyAlias, keyPassword.toCharArray()) as PrivateKey
        val cert = keystore.getCertificateChain(keyAlias)[0] as X509Certificate

        val signerConfig = ApkSigTool.SignerConfig.Builder("CERT", privateKey, listOf(cert))
            .build()

        ApkSigTool.Builder(listOf(signerConfig))
            .setInputApk(inputFile)
            .setOutputApk(outputFile)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()

        Log.d(TAG, "Signed APK (v1+v2+v3): ${outputFile.absolutePath} (${outputFile.length() / 1024}KB)")
        return outputFile.absolutePath
    }

    /**
     * Ensure a signing keystore exists. Generate one if not.
     */
    private fun ensureKeystore() {
        if (keystoreFile.exists()) return

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val cert = generateSelfSignedCert(keyPair)

        val keystore = KeyStore.getInstance("BKS", "BC")
        keystore.load(null, keystorePassword.toCharArray())
        keystore.setKeyEntry(
            keyAlias,
            keyPair.private,
            keyPassword.toCharArray(),
            arrayOf(cert)
        )

        FileOutputStream(keystoreFile).use { fos ->
            keystore.store(fos, keystorePassword.toCharArray())
        }
    }

    /**
     * Generate a self-signed X509 certificate using BouncyCastle.
     */
    private fun generateSelfSignedCert(keyPair: KeyPair): X509Certificate {
        val subject = X500Name("CN=App Cloner, O=Clone, C=US")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 25L * 365 * 24 * 3600 * 1000) // 25 years
        val serialNumber = BigInteger(128, SecureRandom())

        val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.private)

        val certHolder = certBuilder.build(contentSigner)
        return JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)
    }
}
