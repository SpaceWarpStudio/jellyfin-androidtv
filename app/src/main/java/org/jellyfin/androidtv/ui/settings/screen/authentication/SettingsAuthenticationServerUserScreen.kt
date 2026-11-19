package org.jellyfin.androidtv.ui.settings.screen.authentication

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.pin.UserPinRepository
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.util.Utils
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun SettingsAuthenticationServerUserScreen(serverId: UUID, userId: UUID) {
	val router = LocalRouter.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val lifecycleScope = lifecycleOwner.lifecycleScope
	val serverRepository = koinInject<ServerRepository>()
	val serverUserRepository = koinInject<ServerUserRepository>()
	val authenticationRepository = koinInject<AuthenticationRepository>()
	val userPinRepository = koinInject<UserPinRepository>()
	val userRepository = koinInject<UserRepository>()
	val sessionRepository = koinInject<SessionRepository>()

	LaunchedEffect(serverRepository) { serverRepository.loadStoredServers() }

	val server by remember(serverRepository.storedServers) {
		serverRepository.storedServers.map { it.find { server -> server.id == serverId } }
	}.collectAsState(null)

	val user = remember(server) { server?.let(serverUserRepository::getStoredServerUsers)?.find { user -> user.id == userId } }

	val currentUser by userRepository.currentUser.collectAsState()
	val currentSession by sessionRepository.currentSession.collectAsState()
	val canManagePins = Utils.isUserAdmin(currentUser) && currentSession?.serverId == serverId

	// Refresh when returning from the PIN editor
	var pinStatusRevision by remember { mutableIntStateOf(0) }
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) pinStatusRevision++
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	val pinRequired = remember(user, pinStatusRevision) {
		user != null && userPinRepository.isPinRequired(serverId, userId)
	}

	fun openPinSettings(mode: PinSettingsMode) {
		router.push(
			route = Routes.AUTHENTICATION_SERVER_USER_PIN,
			parameters = mapOf(
				"serverId" to serverId.toString(),
				"userId" to userId.toString(),
				"mode" to mode.name,
			),
		)
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(server?.name?.uppercase().orEmpty()) },
				headingContent = { Text(user?.name.orEmpty()) },
			)
		}

		if (canManagePins && user != null) {
			item {
				ListSection(
					headingContent = { Text(stringResource(R.string.lbl_user_pin)) },
					captionContent = {
						Text(
							stringResource(
								if (pinRequired) R.string.lbl_user_pin_enabled
								else R.string.lbl_user_pin_disabled
							)
						)
					},
				)
			}

			if (!pinRequired) {
				item {
					ListButton(
						leadingContent = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
						headingContent = { Text(stringResource(R.string.lbl_set_pin)) },
						captionContent = { Text(stringResource(R.string.lbl_set_pin_description)) },
						onClick = { openPinSettings(PinSettingsMode.SET) },
						modifier = Modifier.focusKey("set_pin")
					)
				}
			} else {
				item {
					ListButton(
						leadingContent = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
						headingContent = { Text(stringResource(R.string.lbl_change_pin)) },
						captionContent = { Text(stringResource(R.string.lbl_change_pin_description)) },
						onClick = { openPinSettings(PinSettingsMode.CHANGE) },
						modifier = Modifier.focusKey("change_pin")
					)
				}

				item {
					ListButton(
						leadingContent = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
						headingContent = { Text(stringResource(R.string.lbl_remove_pin)) },
						captionContent = { Text(stringResource(R.string.lbl_remove_pin_description)) },
						onClick = { openPinSettings(PinSettingsMode.CLEAR) },
						modifier = Modifier.focusKey("remove_pin")
					)
				}
			}
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.lbl_sign_out)) },
				captionContent = { Text(stringResource(R.string.lbl_sign_out_content)) },
				onClick = {
					lifecycleScope.launch {
						if (user != null) authenticationRepository.logout(user)
						router.back()
					}
				},
				modifier = Modifier.focusKey("sign_out")
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.lbl_remove)) },
				captionContent = { Text(stringResource(R.string.lbl_remove_user_content)) },
				onClick = {
					lifecycleScope.launch {
						if (user != null) serverUserRepository.deleteStoredUser(user)
						router.back()
					}
				},
				modifier = Modifier.focusKey("remove")
			)
		}
	}
}
