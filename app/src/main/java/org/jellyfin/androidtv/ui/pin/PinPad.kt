package org.jellyfin.androidtv.ui.pin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.pin.PinHasher
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.focus.focusKey

private val PIN_PAD_ROWS = listOf(
	listOf("1", "2", "3"),
	listOf("4", "5", "6"),
	listOf("7", "8", "9"),
	listOf("⌫", "0", "C"),
)

/** Center key — shortest D-pad path to every other digit. */
private const val INITIAL_ROW = 1
private const val INITIAL_COL = 1

/**
 * TV-friendly PIN pad that avoids the system number IME.
 *
 * Focus starts on **5** and wraps at every edge (e.g. right from 6 → 4, up from 3 → C).
 */
@Composable
fun PinPad(
	pin: String,
	onPinChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	showPlaceholderWhenEmpty: Boolean = true,
	initialFocusRequester: FocusRequester? = null,
) {
	val displayWhite = colorResource(R.color.white)
	val masked = when {
		// Explicit white asterisks — default Text color is black and disappears on dark login UI.
		pin.isNotEmpty() -> "*".repeat(pin.length)
		showPlaceholderWhenEmpty -> stringResource(R.string.input_pin)
		else -> ""
	}

	val requesters = remember {
		List(PIN_PAD_ROWS.size) { List(PIN_PAD_ROWS[0].size) { FocusRequester() } }
	}
	val centerRequester = initialFocusRequester ?: requesters[INITIAL_ROW][INITIAL_COL]

	fun cellRequester(row: Int, col: Int): FocusRequester =
		if (row == INITIAL_ROW && col == INITIAL_COL) centerRequester
		else requesters[row][col]

	Column(
		modifier = modifier.widthIn(max = 280.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = masked.ifEmpty { " " },
			color = displayWhite,
			fontSize = 28.sp,
			letterSpacing = 4.sp,
			textAlign = TextAlign.Center,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 48.dp)
				.border(1.dp, Color.White.copy(alpha = 0.35f))
				.padding(vertical = 12.dp, horizontal = 16.dp)
		)

		val rowCount = PIN_PAD_ROWS.size
		val colCount = PIN_PAD_ROWS[0].size

		PIN_PAD_ROWS.forEachIndexed { rowIndex, row ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				row.forEachIndexed { colIndex, label ->
					val leftCol = (colIndex + colCount - 1) % colCount
					val rightCol = (colIndex + 1) % colCount
					val upRow = (rowIndex + rowCount - 1) % rowCount
					val downRow = (rowIndex + 1) % rowCount

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
							.focusRequester(cellRequester(rowIndex, colIndex))
							.focusProperties {
								left = cellRequester(rowIndex, leftCol)
								right = cellRequester(rowIndex, rightCol)
								up = cellRequester(upRow, colIndex)
								down = cellRequester(downRow, colIndex)
							},
					) {
						Text(label)
					}
				}
			}
		}
	}
}
