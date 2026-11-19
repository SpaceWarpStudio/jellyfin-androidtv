package org.jellyfin.androidtv.ui.download

/**
 * Nexus-local Seerr endpoint for the Download tab spike.
 * Later: move to a user preference.
 */
object SeerrConfig {
	const val BASE_URL = "https://192.168.0.3:8457/"

	/** Host allowed to proceed on self-signed TLS in the Download WebView. */
	const val TRUSTED_HOST = "192.168.0.3"
}
