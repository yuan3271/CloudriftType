package com.yuan3271.cloudrift.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.theme.CloudriftTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        val requestMicrophone = intent?.getBooleanExtra(EXTRA_REQUEST_MICROPHONE, false) == true

        setContent {
            val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
            val userStats by AppGraph.profile.stats.collectAsStateWithLifecycle()
            val update by AppGraph.updates.available.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { AppGraph.updates.checkIfDue() }
            CloudriftTheme(
                themeMode = settings.themeMode,
                themeSource = settings.themeSource,
                accentHue = settings.accentHue,
                accentSaturation = settings.accentSaturation,
            ) {
                SettingsScreen(
                    settings = settings,
                    onUpdate = AppGraph.settings::update,
                    imeEnabled = rememberImeEnabled(),
                    userStats = userStats,
                    update = update,
                    actions = SettingsActions(
                        requestMicrophoneOnStart = requestMicrophone,
                        openSystemKeyboardSettings = ::openSystemKeyboardSettings,
                        showKeyboardPicker = ::showKeyboardPicker,
                        clearLearning = AppGraph.profile::clear,
                        checkForUpdate = { AppGraph.updates.checkIfDue(force = true) },
                        downloadUpdate = { update?.let(AppGraph.updates::download) },
                        openRelease = {
                            val url = update?.releaseUrl ?: RELEASES_URL
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        finish = ::finish,
                    ),
                )
            }
        }
    }

    private fun openSystemKeyboardSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    }

    private fun showKeyboardPicker() {
        runCatching {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    companion object {
        const val EXTRA_REQUEST_MICROPHONE = "request_microphone"
        const val MICROPHONE_PERMISSION: String = Manifest.permission.RECORD_AUDIO
        const val IME_ID = "com.yuan3271.cloudrift/.ime.CloudriftImeService"
        const val RELEASES_URL = "https://github.com/yuan3271/CloudriftType/releases"
    }
}

/** Kept in one place so the screen does not need to know about Android services. */
data class SettingsActions(
    val requestMicrophoneOnStart: Boolean,
    val openSystemKeyboardSettings: () -> Unit,
    val showKeyboardPicker: () -> Unit,
    val clearLearning: () -> Unit,
    val checkForUpdate: () -> Unit,
    val downloadUpdate: () -> Unit,
    val openRelease: () -> Unit,
    val finish: () -> Unit,
)

@Composable
internal fun rememberMicrophoneGranted(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, SettingsActivity.MICROPHONE_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED
}

/** True when 云隙输入 is both enabled and the keyboard currently in use. */
@Composable
internal fun rememberImeEnabled(): Boolean {
    val context = LocalContext.current
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    val enabled: List<InputMethodInfo> = manager.enabledInputMethodList
    val active = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    )
    return active == SettingsActivity.IME_ID ||
        enabled.any { "${it.packageName}/${it.serviceName}" == SettingsActivity.IME_ID }
}
