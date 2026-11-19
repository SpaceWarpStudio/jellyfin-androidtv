package org.jellyfin.androidtv.util

import android.content.Context
import android.content.res.Configuration
import android.view.InputDevice
import android.view.KeyEvent

/**
 * Jellyfin Android TV is remote-first. A plugged-in USB/Bluetooth keyboard makes Android
 * suppress the soft IME and can steal focus from on-screen pads. These helpers keep the app
 * in a "no hardware keyboard" posture and ignore alphabetic keyboard devices.
 *
 * TV remotes / DPAD devices are typically [InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC] and are
 * not ignored. Soft IME / virtual devices are also left alone.
 */
object PhysicalKeyboard {
	/**
	 * Returns a context whose configuration reports no hardware keyboard, so the system does
	 * not switch into hardware-keyboard input mode.
	 */
	fun wrapContext(base: Context): Context {
		val config = Configuration(base.resources.configuration).apply {
			keyboard = Configuration.KEYBOARD_NOKEYS
			keyboardHidden = Configuration.KEYBOARDHIDDEN_YES
			hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
		}
		return base.createConfigurationContext(config)
	}
}

/**
 * True for real alphabetic keyboards (USB/Bluetooth). False for remotes, virtual/IME devices,
 * and unknown sources.
 */
fun KeyEvent.isFromPhysicalAlphabeticKeyboard(): Boolean {
	// Virtual/IME synthesised events use a negative device id (historically -1).
	if (deviceId < 0) return false

	val device = InputDevice.getDevice(deviceId) ?: return false
	if (device.isVirtual) return false

	return device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
}
