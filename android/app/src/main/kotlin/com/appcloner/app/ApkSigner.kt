package com.appcloner.app

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Signs modified APK files using BouncyCastle for certificate generation
 * and JAR signing (v1) for broad compatibility.
 */
class ApkSigner(private val context: Context) {

    companion object {
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

        ensureKeystore()

        val keystore = KeyStore.getInstance("BKS", "BC")
        FileInputStream(keystoreFile).use { fis ->
            keystore.load(fis, keystorePassword.toCharArray())
        }

        val privateKey = keystore.getKey(keyAlias, keyPassword.toCharArray()) as PrivateKey
        val cert = keystore.getCertificateChain(keyAlias)[0] as X509Certificate

        signV1(inputFile, outputFile, privateKey, cert)
        return outputFile.absolutePath
    }

    /**
     * V1 (JAR) signing — creates META-INF/MANIFEST.MF, CERT.SF, CERT.RSA
     */
    private fun signV1(
        inputApk: File,
        outputApk: File,
        privateKey: PrivateKey,
        cert: X509Certificate
    ) {
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Created-By")] = "App Cloner"

        val zipFile = ZipFile(inputApk)
        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/")) continue
            if (entry.isDirectory) continue

            val data = zipFile.getInputStream(entry).readBytes()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = Base64.getEncoder().encodeToString(digest.digest(data))

            val attr = Attributes()
            attr[Attributes.Name("SHA-256-Digest")] = hash
            manifest.entries[entry.name] = attr
        }

        ZipOutputStream(FileOutputStream(outputApk)).use { zipOut ->
            // Write MANIFEST.MF
            val manifestBaos = ByteArrayOutputStream()
            manifest.write(manifestBaos)
            val manifestData = manifestBaos.toByteArray()

            zipOut.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zipOut.write(manifestData)
            zipOut.closeEntry()

            // Write CERT.SF
            val sfBytes = createSignatureFile(manifest)
            zipOut.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zipOut.write(sfBytes)
            zipOut.closeEntry()

            // Write CERT.RSA (PKCS7 signature block)
            val sigBlock = createSignatureBlock(sfBytes, privateKey, cert)
            zipOut.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zipOut.write(sigBlock)
            zipOut.closeEntry()

            // Copy all original entries (except META-INF)
            val inputZip = ZipFile(inputApk)
            val inputEntries = inputZip.entries()
            while (inputEntries.hasMoreElements()) {
                val entry = inputEntries.nextElement()
                if (entry.name.startsWith("META-INF/")) continue

                zipOut.putNextEntry(ZipEntry(entry.name))
                zipOut.write(inputZip.getInputStream(entry).readBytes())
                zipOut.closeEntry()
            }
            inputZip.close()
        }
        zipFile.close()
    }

    /**
     * Create the .SF (signature file) from the manifest.
     */
    private fun createSignatureFile(manifest: Manifest): ByteArray {
        val manifestBaos = ByteArrayOutputStream()
        manifest.write(manifestBaos)

        val digest = MessageDigest.getInstance("SHA-256")
        val mainDigest = Base64.getEncoder().encodeToString(
            digest.digest(manifestBaos.toByteArray())
        )

        val sb = StringBuilder()
        sb.append("Signature-Version: 1.0\r\n")
        sb.append("Created-By: App Cloner\r\n")
        sb.append("SHA-256-Digest-Manifest: $mainDigest\r\n")
        sb.append("\r\n")

        for ((name, _) in manifest.entries) {
            val entryBlock = "Name: $name\r\n"
            val entryDigest = Base64.getEncoder().encodeToString(
                digest.digest(entryBlock.toByteArray())
            )
            sb.append("Name: $name\r\n")
            sb.append("SHA-256-Digest: $entryDigest\r\n")
            sb.append("\r\n")
        }

        return sb.toString().toByteArray()
    }

    /**
     * Create PKCS7 signature block using BouncyCastle CMS.
     */
    private fun createSignatureBlock(
        sfData: ByteArray,
        privateKey: PrivateKey,
        cert: X509Certificate
    ): ByteArray {
        val generator = CMSSignedDataGenerator()

        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(privateKey)

        val digestProvider = JcaDigestCalculatorProviderBuilder()
            .setProvider("BC")
            .build()

        generator.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(digestProvider)
                .build(contentSigner, cert)
        )

        val content = CMSProcessableByteArray(sfData)
        val signedData = generator.generate(content, true)
        return signedData.encoded
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
