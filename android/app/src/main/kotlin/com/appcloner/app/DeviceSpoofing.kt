package com.appcloner.app

import android.content.Context
import android.util.Log
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Device fingerprint spoofing for cloned apps.
 *
 * Many apps detect clones by checking device identifiers.
 * If two instances report the same ANDROID_ID, IMEI, etc.,
 * the server can detect they're on the same device.
 *
 * This class generates unique device identifiers per clone and
 * injects a small spoofing configuration that the clone can read
 * at runtime to override system values.
 *
 * Spoofed properties:
 * - ANDROID_ID (Settings.Secure)
 * - Device serial number
 * - Build fingerprint
 * - Wi-Fi MAC address
 * - Bluetooth MAC address
 * - GSF ID (Google Services Framework)
 * - Advertising ID
 */
class DeviceSpoofing(private val context: Context) {

    companion object {
        private const val TAG = "DeviceSpoofing"
    }

    /**
     * A complete set of spoofed device identifiers.
     */
    data class DeviceProfile(
        val androidId: String,
        val serialNumber: String,
        val buildFingerprint: String,
        val wifiMac: String,
        val bluetoothMac: String,
        val gsfId: String,
        val advertisingId: String,
        val imei: String,
        val buildModel: String,
        val buildManufacturer: String,
        val buildBrand: String,
        val buildDevice: String,
        val buildProduct: String
    ) {
        fun toConfigString(): String {
            return buildString {
                appendLine("# Device Profile for Clone")
                appendLine("android_id=$androidId")
                appendLine("serial=$serialNumber")
                appendLine("fingerprint=$buildFingerprint")
                appendLine("wifi_mac=$wifiMac")
                appendLine("bt_mac=$bluetoothMac")
                appendLine("gsf_id=$gsfId")
                appendLine("advertising_id=$advertisingId")
                appendLine("imei=$imei")
                appendLine("build_model=$buildModel")
                appendLine("build_manufacturer=$buildManufacturer")
                appendLine("build_brand=$buildBrand")
                appendLine("build_device=$buildDevice")
                appendLine("build_product=$buildProduct")
            }
        }

        fun toMap(): Map<String, String> = mapOf(
            "androidId" to androidId,
            "serialNumber" to serialNumber,
            "buildFingerprint" to buildFingerprint,
            "wifiMac" to wifiMac,
            "bluetoothMac" to bluetoothMac,
            "gsfId" to gsfId,
            "advertisingId" to advertisingId,
            "imei" to imei,
            "buildModel" to buildModel,
            "buildManufacturer" to buildManufacturer,
            "buildBrand" to buildBrand,
            "buildDevice" to buildDevice,
            "buildProduct" to buildProduct
        )
    }

    /**
     * Generate a unique device profile for a clone.
     * Uses deterministic generation based on package name + clone index
     * so the same clone always gets the same profile.
     */
    fun generateProfile(originalPackage: String, cloneIndex: Int): DeviceProfile {
        val seed = "$originalPackage:clone:$cloneIndex"

        return DeviceProfile(
            androidId = generateHex(seed, "android_id", 16),
            serialNumber = generateSerial(seed),
            buildFingerprint = generateFingerprint(seed),
            wifiMac = generateMac(seed, "wifi"),
            bluetoothMac = generateMac(seed, "bt"),
            gsfId = generateHex(seed, "gsf_id", 16),
            advertisingId = generateUUID(seed, "adid"),
            imei = generateImei(seed),
            buildModel = randomizeModel(seed),
            buildManufacturer = randomizeManufacturer(seed),
            buildBrand = randomizeBrand(seed),
            buildDevice = randomizeDevice(seed),
            buildProduct = randomizeProduct(seed)
        )
    }

    /**
     * Inject the device profile into an APK as an asset file.
     * The clone reads this at runtime to spoof device values.
     */
    fun injectProfile(apkPath: String, profile: DeviceProfile): String {
        val sourceApk = File(apkPath)
        val outputApk = File(sourceApk.parent, "profiled.apk")

        ZipFile(sourceApk).use { zipIn ->
            ZipOutputStream(FileOutputStream(outputApk)).use { zipOut ->
                val entries = zipIn.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    val newEntry = ZipEntry(entry.name)
                    if (entry.method == ZipEntry.STORED) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = entry.size
                        newEntry.compressedSize = entry.compressedSize
                        newEntry.crc = entry.crc
                    }

                    zipOut.putNextEntry(newEntry)
                    zipOut.write(zipIn.getInputStream(entry).readBytes())
                    zipOut.closeEntry()
                }

                // Add device profile
                val profileEntry = ZipEntry("assets/clone_meta/device_profile.conf")
                zipOut.putNextEntry(profileEntry)
                zipOut.write(profile.toConfigString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                Log.d(TAG, "Injected device profile into APK")
            }
        }

        // Replace original
        sourceApk.delete()
        outputApk.renameTo(sourceApk)

        return sourceApk.absolutePath
    }

    // --- Generators ---

    private fun generateHex(seed: String, salt: String, length: Int): String {
        val hash = sha256("$seed:$salt")
        return hash.take(length)
    }

    private fun generateSerial(seed: String): String {
        val hash = sha256("$seed:serial")
        return hash.take(12).uppercase()
    }

    private fun generateFingerprint(seed: String): String {
        val manufacturers = listOf("samsung", "google", "xiaomi", "oneplus", "huawei", "oppo")
        val manufacturer = manufacturers[Math.abs(seed.hashCode()) % manufacturers.size]
        val hash = sha256("$seed:fp").take(8)
        return "$manufacturer/device/model:13/TQ3A.230901.001/$hash:user/release-keys"
    }

    private fun generateMac(seed: String, type: String): String {
        val hash = sha256("$seed:mac:$type")
        val bytes = hash.take(12)
        return bytes.chunked(2).joinToString(":")
    }

    private fun generateUUID(seed: String, salt: String): String {
        val hash = sha256("$seed:$salt")
        val hex = hash.take(32)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun generateImei(seed: String): String {
        val hash = sha256("$seed:imei")
        val digits = hash.filter { it.isDigit() }.take(14)
        val paddedDigits = digits.padEnd(14, '0')
        val checkDigit = luhnCheckDigit(paddedDigits)
        return paddedDigits + checkDigit
    }

    private fun luhnCheckDigit(number: String): Char {
        var sum = 0
        for (i in number.indices) {
            var digit = number[number.length - 1 - i].digitToInt()
            if (i % 2 == 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return ((10 - (sum % 10)) % 10).digitToChar()
    }

    private fun randomizeModel(seed: String): String {
        val models = listOf(
            "SM-G991B", "SM-A525F", "Pixel 7", "Pixel 6a",
            "M2101K6G", "22041219G", "CPH2399", "IN2020",
            "V2169", "RMX3393", "SM-S908B", "Pixel 8 Pro"
        )
        return models[Math.abs(sha256("$seed:model").hashCode()) % models.size]
    }

    private fun randomizeManufacturer(seed: String): String {
        val manufacturers = listOf(
            "samsung", "Google", "Xiaomi", "OnePlus",
            "OPPO", "vivo", "realme", "Huawei"
        )
        return manufacturers[Math.abs(sha256("$seed:mfr").hashCode()) % manufacturers.size]
    }

    private fun randomizeBrand(seed: String): String {
        val brands = listOf(
            "samsung", "google", "Xiaomi", "OnePlus",
            "OPPO", "vivo", "realme", "HUAWEI"
        )
        return brands[Math.abs(sha256("$seed:brand").hashCode()) % brands.size]
    }

    private fun randomizeDevice(seed: String): String {
        val devices = listOf(
            "o1s", "a52sxq", "panther", "bluejay",
            "venus", "raven", "lahaina", "taro"
        )
        return devices[Math.abs(sha256("$seed:device").hashCode()) % devices.size]
    }

    private fun randomizeProduct(seed: String): String {
        val products = listOf(
            "o1sxxx", "a52sxxx", "panther", "bluejay",
            "venus_global", "raven", "lahaina", "taro"
        )
        return products[Math.abs(sha256("$seed:product").hashCode()) % products.size]
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
