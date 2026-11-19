package org.jellyfin.androidtv.auth.pin

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lightweight local PIN hashing for per-account unlock.
 *
 * Stored format: `1:<hex-salt>:<hex-sha256(salt || pin-utf8)>`
 */
object PinHasher {
	const val MIN_LENGTH = 4
	const val MAX_LENGTH = 8

	private const val VERSION = 1
	private const val SALT_BYTES = 16
	private const val SEPARATOR = ':'
	private const val HEX_DIGITS = "0123456789abcdef"

	private val secureRandom = SecureRandom()

	fun isValidFormat(pin: String): Boolean =
		pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

	/**
	 * Creates a new salted hash for [pin]. Throws [IllegalArgumentException] if the PIN format is invalid.
	 */
	fun hash(pin: String): String {
		require(isValidFormat(pin)) {
			"PIN must be $MIN_LENGTH–$MAX_LENGTH digits"
		}

		val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
		return encode(VERSION, salt, digest(salt, pin))
	}

	/**
	 * Returns true when [pin] matches [storedHash]. Invalid PIN format or corrupt hash → false.
	 */
	fun verify(pin: String, storedHash: String): Boolean {
		if (!isValidFormat(pin)) return false

		val parsed = parse(storedHash) ?: return false
		val actual = digest(parsed.salt, pin)
		return MessageDigest.isEqual(actual, parsed.hash)
	}

	private fun digest(salt: ByteArray, pin: String): ByteArray {
		val digest = MessageDigest.getInstance("SHA-256")
		digest.update(salt)
		digest.update(pin.toByteArray(Charsets.UTF_8))
		return digest.digest()
	}

	private fun encode(version: Int, salt: ByteArray, hash: ByteArray): String =
		"$version$SEPARATOR${salt.toHex()}$SEPARATOR${hash.toHex()}"

	private fun parse(storedHash: String): ParsedHash? {
		val parts = storedHash.split(SEPARATOR)
		if (parts.size != 3) return null

		val version = parts[0].toIntOrNull() ?: return null
		if (version != VERSION) return null

		val salt = parts[1].fromHex() ?: return null
		val hash = parts[2].fromHex() ?: return null
		if (salt.size != SALT_BYTES || hash.isEmpty()) return null

		return ParsedHash(salt, hash)
	}

	private fun ByteArray.toHex(): String = buildString(size * 2) {
		for (byte in this@toHex) {
			val value = byte.toInt() and 0xff
			append(HEX_DIGITS[value ushr 4])
			append(HEX_DIGITS[value and 0x0f])
		}
	}

	private fun String.fromHex(): ByteArray? {
		if (length % 2 != 0) return null
		val result = ByteArray(length / 2)
		for (i in result.indices) {
			val high = this[i * 2].hexValue() ?: return null
			val low = this[i * 2 + 1].hexValue() ?: return null
			result[i] = ((high shl 4) or low).toByte()
		}
		return result
	}

	private fun Char.hexValue(): Int? = when (this) {
		in '0'..'9' -> this - '0'
		in 'a'..'f' -> this - 'a' + 10
		in 'A'..'F' -> this - 'A' + 10
		else -> null
	}

	private data class ParsedHash(
		val salt: ByteArray,
		val hash: ByteArray,
	)
}
