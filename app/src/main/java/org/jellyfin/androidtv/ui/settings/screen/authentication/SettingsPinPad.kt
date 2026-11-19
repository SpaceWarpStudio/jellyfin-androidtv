package org.jellyfin.androidtv.ui.settings.screen.authentication

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.ui.pin.PinPad

/**
 * Settings-screen alias for the shared [PinPad].
 */
@Composable
fun SettingsPinPad(
	pin: String,
	onPinChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	PinPad(
		pin = pin,
		onPinChange = onPinChange,
		modifier = modifier,
	)
}
