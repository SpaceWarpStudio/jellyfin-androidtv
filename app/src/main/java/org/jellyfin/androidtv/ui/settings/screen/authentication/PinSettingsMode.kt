package org.jellyfin.androidtv.ui.settings.screen.authentication

/**
 * PIN management actions available to administrators in account settings.
 */
enum class PinSettingsMode {
	SET,
	CHANGE,
	CLEAR,
	;

	companion object {
		fun fromRouteParameter(value: String?): PinSettingsMode? =
			entries.find { it.name.equals(value, ignoreCase = true) }
	}
}
