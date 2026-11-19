package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.PhysicalKeyboard

class JellyfinApplication : Application() {
	override fun attachBaseContext(base: Context?) {
		// Pretend no hardware keyboard is attached so Android keeps remote / on-screen input
		// behavior (soft IME and custom pads) instead of hardware-keyboard mode.
		val wrapped = base?.let(PhysicalKeyboard::wrapContext)
		super.attachBaseContext(wrapped)
		TelemetryService.init(this)
	}
}
