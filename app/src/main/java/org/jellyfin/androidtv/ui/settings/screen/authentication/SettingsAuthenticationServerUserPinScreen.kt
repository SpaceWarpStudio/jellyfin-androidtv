package org.jellyfin.androidtv.ui.settings.screen.authentication

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.pin.PinHasher
import org.jellyfin.androidtv.auth.pin.UserPinRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.util.Utils
import org.koin.compose.koinInject
import java.util.UUID

private enum class PinEntryStep {
	CURRENT,
	NEW,
	CONFIRM,
}

@Composable
fun SettingsAuthenticationServerUserPinScreen(
	serverId: UUID,
	userId: UUID,
	mode: PinSettingsMode,
) {
	val router = LocalRouter.current
	val serverRepository = koinInject<ServerRepository>()
	val serverUserRepository = koinInject<ServerUserRepository>()
	val userPinRepository = koinInject<UserPinRepository>()
	val userRepository = koinInject<UserRepository>()
	val sessionRepository = koinInject<SessionRepository>()

	LaunchedEffect(serverRepository) { serverRepository.loadStoredServers() }

	val servers by serverRepository.storedServers.collectAsState()
	val server = remember(servers, serverId) { servers.find { it.id == serverId } }
	val user = remember(server, userId) {
		server?.let(serverUserRepository::getStoredServerUsers)?.find { it.id == userId }
	}

	val currentUser by userRepository.currentUser.collectAsState()
	val currentSession by sessionRepository.currentSession.collectAsState()
	val canManagePins = Utils.isUserAdmin(currentUser) && currentSession?.serverId == serverId

	val initialStep = when (mode) {
		PinSettingsMode.SET -> PinEntryStep.NEW
		PinSettingsMode.CHANGE,
		PinSettingsMode.CLEAR -> PinEntryStep.CURRENT
	}

	var step by remember(mode) { mutableStateOf(initialStep) }
	var pin by remember { mutableStateOf("") }
	var pendingNewPin by remember(mode) { mutableStateOf<String?>(null) }
	var errorRes by remember { mutableStateOf<Int?>(null) }

	val titleRes = when (mode) {
		PinSettingsMode.SET -> R.string.lbl_set_pin
		PinSettingsMode.CHANGE -> R.string.lbl_change_pin
		PinSettingsMode.CLEAR -> R.string.lbl_remove_pin
	}

	val stepCaptionRes = when (step) {
		PinEntryStep.CURRENT -> R.string.lbl_enter_current_pin
		PinEntryStep.NEW -> R.string.lbl_enter_new_pin
		PinEntryStep.CONFIRM -> R.string.lbl_confirm_new_pin
	}

	fun submit() {
		errorRes = null

		when (step) {
			PinEntryStep.CURRENT -> {
				if (!userPinRepository.verifyPin(serverId, userId, pin)) {
					errorRes = R.string.login_invalid_pin
					pin = ""
					return
				}
				when (mode) {
					PinSettingsMode.CLEAR -> {
						userPinRepository.clearPin(serverId, userId)
						router.back()
					}

					PinSettingsMode.CHANGE -> {
						pin = ""
						step = PinEntryStep.NEW
					}

					PinSettingsMode.SET -> Unit
				}
			}

			PinEntryStep.NEW -> {
				if (!PinHasher.isValidFormat(pin)) {
					errorRes = R.string.lbl_pin_invalid_length
					return
				}
				pendingNewPin = pin
				pin = ""
				step = PinEntryStep.CONFIRM
			}

			PinEntryStep.CONFIRM -> {
				val expected = pendingNewPin
				if (expected == null || pin != expected) {
					errorRes = R.string.lbl_pin_mismatch
					pendingNewPin = null
					pin = ""
					step = PinEntryStep.NEW
					return
				}
				if (!userPinRepository.setPin(serverId, userId, pin)) {
					errorRes = R.string.lbl_pin_invalid_length
					return
				}
				router.back()
			}
		}
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(user?.name?.uppercase().orEmpty()) },
				headingContent = { Text(stringResource(titleRes)) },
				captionContent = { Text(stringResource(stepCaptionRes)) },
			)
		}

		if (!canManagePins) {
			item {
				ListMessage {
					Text(stringResource(R.string.lbl_pin_admin_required))
				}
			}
			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.lbl_ok)) },
					onClick = { router.back() },
					modifier = Modifier.focusKey("pin_admin_back")
				)
			}
		} else {
			item {
				SettingsPinPad(
					pin = pin,
					onPinChange = {
						pin = it
						errorRes = null
					},
					modifier = Modifier.focusKey("pin_pad")
				)
			}

			if (errorRes != null) {
				item {
					ListMessage {
						Text(stringResource(errorRes!!))
					}
				}
			}

			item {
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.lbl_ok)) },
					onClick = { submit() },
					modifier = Modifier.focusKey("pin_confirm")
				)
			}
		}
	}
}
