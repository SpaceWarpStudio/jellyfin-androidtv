package org.jellyfin.androidtv.auth.pin

import org.jellyfin.androidtv.auth.store.AuthenticationStore
import java.util.UUID

/**
 * Local per-user PIN unlock storage. PINs gate use of a stored access token and are never
 * sent to the Jellyfin server.
 */
interface UserPinRepository {
	fun isPinRequired(serverId: UUID, userId: UUID): Boolean

	/**
	 * Stores a new PIN hash for the user. Returns false if the user is missing or [pin] is invalid.
	 */
	fun setPin(serverId: UUID, userId: UUID, pin: String): Boolean

	/**
	 * Removes the PIN requirement for the user. Returns false if the user is missing.
	 */
	fun clearPin(serverId: UUID, userId: UUID): Boolean

	/**
	 * Returns true if the user has a PIN and [pin] matches. Missing user / no PIN / wrong PIN → false.
	 */
	fun verifyPin(serverId: UUID, userId: UUID, pin: String): Boolean
}

class UserPinRepositoryImpl(
	private val authenticationStore: AuthenticationStore,
) : UserPinRepository {
	override fun isPinRequired(serverId: UUID, userId: UUID): Boolean =
		!authenticationStore.getUser(serverId, userId)?.pinHash.isNullOrEmpty()

	override fun setPin(serverId: UUID, userId: UUID, pin: String): Boolean {
		if (!PinHasher.isValidFormat(pin)) return false
		val user = authenticationStore.getUser(serverId, userId) ?: return false
		return authenticationStore.putUser(serverId, userId, user.copy(pinHash = PinHasher.hash(pin)))
	}

	override fun clearPin(serverId: UUID, userId: UUID): Boolean {
		val user = authenticationStore.getUser(serverId, userId) ?: return false
		if (user.pinHash == null) return true
		return authenticationStore.putUser(serverId, userId, user.copy(pinHash = null))
	}

	override fun verifyPin(serverId: UUID, userId: UUID, pin: String): Boolean {
		val storedHash = authenticationStore.getUser(serverId, userId)?.pinHash ?: return false
		return PinHasher.verify(pin, storedHash)
	}
}
