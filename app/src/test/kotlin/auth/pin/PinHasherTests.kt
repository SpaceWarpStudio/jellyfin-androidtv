package org.jellyfin.androidtv.auth.pin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith

class PinHasherTests : FunSpec({
	test("isValidFormat accepts 4–8 digit PINs") {
		PinHasher.isValidFormat("1234") shouldBe true
		PinHasher.isValidFormat("12345678") shouldBe true
		PinHasher.isValidFormat("0000") shouldBe true
	}

	test("isValidFormat rejects invalid PINs") {
		PinHasher.isValidFormat("") shouldBe false
		PinHasher.isValidFormat("123") shouldBe false
		PinHasher.isValidFormat("123456789") shouldBe false
		PinHasher.isValidFormat("12ab") shouldBe false
		PinHasher.isValidFormat("12 34") shouldBe false
		PinHasher.isValidFormat("1234!") shouldBe false
	}

	test("hash produces versioned salted format") {
		val hash = PinHasher.hash("1234")
		hash shouldStartWith "1:"
		hash shouldMatch Regex("""1:[0-9a-f]{32}:[0-9a-f]{64}""")
	}

	test("hash uses a unique salt each time") {
		val first = PinHasher.hash("1234")
		val second = PinHasher.hash("1234")
		first shouldNotBe second
	}

	test("verify accepts the correct PIN") {
		val hash = PinHasher.hash("2468")
		PinHasher.verify("2468", hash) shouldBe true
	}

	test("verify rejects the wrong PIN") {
		val hash = PinHasher.hash("2468")
		PinHasher.verify("2469", hash) shouldBe false
		PinHasher.verify("24680", hash) shouldBe false
	}

	test("verify rejects invalid PIN format without throwing") {
		val hash = PinHasher.hash("1234")
		PinHasher.verify("123", hash) shouldBe false
		PinHasher.verify("abcd", hash) shouldBe false
	}

	test("verify rejects corrupt stored hashes") {
		PinHasher.verify("1234", "") shouldBe false
		PinHasher.verify("1234", "not-a-hash") shouldBe false
		PinHasher.verify("1234", "2:00:00") shouldBe false
		PinHasher.verify("1234", "1:zz:00") shouldBe false
	}

	test("hash rejects invalid PIN format") {
		shouldThrow<IllegalArgumentException> {
			PinHasher.hash("123")
		}
	}
})
