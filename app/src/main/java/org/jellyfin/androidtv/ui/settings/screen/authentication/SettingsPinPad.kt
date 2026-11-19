package org.jellyfin.androidtv.ui.settings.screen.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.pin.PinHasher
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.focus.focusKey

/**
 * TV-friendly PIN pad that avoids the system number IME (which can dismiss settings navigation).
 */
@Composable
fun SettingsPinPad(
	pin: String,
	onPinChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val masked = if (pin.isEmpty()) {
		stringResource(R.string.input_pin)
	} else {
		"•".repeat(pin.length)
	}

	Column(
		modifier = modifier.widthIn(max = 280.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = masked,
			fontSize = 22.sp,
			modifier = Modifier.padding(vertical = 8.dp)
		)

		val digits = listOf(
			listOf("1", "2", "3"),
			listOf("4", "5", "6"),
			listOf("7", "8", "9"),
			listOf("⌫", "0", "C"),
		)

		digits.forEachIndexed { rowIndex, row ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				row.forEach { label ->
					Button(
						onClick = {
							onPinChange(
								when (label) {
									"⌫" -> pin.dropLast(1)
									"C" -> ""
									else -> (pin + label).take(PinHasher.MAX_LENGTH)
								}
							)
						},
						modifier = Modifier
							.weight(1f)
							.focusKey("pin_pad_${rowIndex}_$label")
					) {
						Text(label)
					}
				}
			}
		}
	}
}
