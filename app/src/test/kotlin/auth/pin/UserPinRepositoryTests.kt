package org.jellyfin.androidtv.auth.pin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import java.util.UUID

class UserPinRepositoryTests : FunSpec({
	val serverId = UUID.fromString("11111111-1111-1111-1111-111111111111")
	val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")

	test("isPinRequired is false when user is missing or has no hash") {
		val store = mockk<AuthenticationStore> {
			every { getUser(serverId, userId) } returns null
		}
		UserPinRepositoryImpl(store).isPinRequired(serverId, userId) shouldBe false

		every { store.getUser(serverId, userId) } returns AuthenticationStoreUser(name = "Kid")
		UserPinRepositoryImpl(store).isPinRequired(serverId, userId) shouldBe false
	}

	test("setPin stores a hash and verifyPin accepts the same PIN") {
		val stored = slot<AuthenticationStoreUser>()
		var current = AuthenticationStoreUser(name = "Kid", accessToken = "token")

		val store = mockk<AuthenticationStore> {
			every { getUser(serverId, userId) } answers { current }
			every { putUser(serverId, userId, capture(stored)) } answers {
				current = stored.captured
				true
			}
		}

		val repository = UserPinRepositoryImpl(store)
		repository.setPin(serverId, userId, "1357") shouldBe true
		repository.isPinRequired(serverId, userId) shouldBe true
		repository.verifyPin(serverId, userId, "1357") shouldBe true
		repository.verifyPin(serverId, userId, "1358") shouldBe false
		current.pinHash shouldBe stored.captured.pinHash
		current.accessToken shouldBe "token"
	}

	test("setPin rejects invalid format and missing users") {
		val store = mockk<AuthenticationStore> {
			every { getUser(serverId, userId) } returns AuthenticationStoreUser(name = "Kid")
		}
		val repository = UserPinRepositoryImpl(store)

		repository.setPin(serverId, userId, "12") shouldBe false
		verify(exactly = 0) { store.putUser(any(), any(), any()) }

		every { store.getUser(serverId, userId) } returns null
		repository.setPin(serverId, userId, "1234") shouldBe false
	}

	test("clearPin removes the hash") {
		val hash = PinHasher.hash("9999")
		var current = AuthenticationStoreUser(name = "Kid", pinHash = hash)
		val stored = slot<AuthenticationStoreUser>()

		val store = mockk<AuthenticationStore> {
			every { getUser(serverId, userId) } answers { current }
			every { putUser(serverId, userId, capture(stored)) } answers {
				current = stored.captured
				true
			}
		}

		val repository = UserPinRepositoryImpl(store)
		repository.clearPin(serverId, userId) shouldBe true
		repository.isPinRequired(serverId, userId) shouldBe false
		current.pinHash shouldBe null
	}
})
