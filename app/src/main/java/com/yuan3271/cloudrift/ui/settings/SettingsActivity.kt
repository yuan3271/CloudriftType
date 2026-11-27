package com.yuan3271.cloudrift.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.data.AppTheme
import com.yuan3271.cloudrift.data.ImportOutcome
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.theme.LocalLiquidGlassKeyDensity
import com.yuan3271.cloudrift.ui.AppThemeChoiceDialog

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        val launchSettings = AppGraph.settings.current
        val darkLaunch = when (launchSettings.themeMode) {
            com.yuan3271.cloudrift.data.ThemeMode.System ->
                resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            com.yuan3271.cloudrift.data.ThemeMode.Light -> false
            com.yuan3271.cloudrift.data.ThemeMode.Dark -> true
            }
        window.setBackgroundDrawable(
            ColorDrawable(if (darkLaunch) 0xFF101319.toInt() else 0xFFF9FAFD.toInt()),
        )
        val requestMicrophone = intent?.getBooleanExtra(EXTRA_REQUEST_MICROPHONE, false) == true

        setContent {
            val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
            val userStats by AppGraph.profile.stats.collectAsStateWithLifecycle()
            val update by AppGraph.updates.available.collectAsStateWithLifecycle()
            val updateStatus by AppGraph.updates.status.collectAsStateWithLifecycle()
            val imeStatus = rememberImeStatus()
            LaunchedEffect(Unit) { AppGraph.updates.checkIfDue() }
            CloudriftTheme(
                appTheme = settings.appTheme,
                themeMode = settings.themeMode,
                themeSource = settings.themeSource,
                accentHue = settings.accentHue,
                accentSaturation = settings.accentSaturation,
                appWindow = true,
            ) {
                CompositionLocalProvider(
                    LocalLiquidGlassKeyDensity provides settings.liquidGlassKeyDensity,
                ) {
                    SettingsScreen(
                        settings = settings,
                        onUpdate = AppGraph.settings::update,
                        imeEnabled = imeStatus.enabled,
                        imeActive = imeStatus.active,
                        userStats = userStats,
                        update = update,
                        updateStatus = updateStatus,
                        actions = SettingsActions(
                            requestMicrophoneOnStart = requestMicrophone,
                            openSystemKeyboardSettings = ::openSystemKeyboardSettings,
                            showKeyboardPicker = ::showKeyboardPicker,
                            clearLearning = AppGraph.profile::clear,
                            exportLearning = AppGraph.profile::exportPayload,
                            importLearning = AppGraph.profile::importPayload,
                            checkForUpdate = { AppGraph.updates.checkIfDue(force = true) },
                            downloadUpdate = { update?.let(AppGraph.updates::download) },
                            downloadUpdateViaMirror = {
                                update?.let { AppGraph.updates.download(it, viaMirror = true) }
                            },
                            openRelease = {
                                val url = update?.releaseUrl ?: RELEASES_URL
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            },
                            finish = ::finish,
                        ),
                    )
                    if (AppTheme.THEME_CHOICE_ENABLED && !settings.appThemeChosen) {
                        AppThemeChoiceDialog { theme ->
                            AppGraph.settings.update { it.withAppThemeChoice(theme) }
                        }
                    }
                }
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
    /** The learnt profile as one portable string; the screen picks how to hand it over. */
    val exportLearning: () -> String,
    val importLearning: (String) -> ImportOutcome,
    val checkForUpdate: () -> Unit,
    val downloadUpdate: () -> Unit,
    val downloadUpdateViaMirror: () -> Unit,
    val openRelease: () -> Unit,
    val finish: () -> Unit,
)

/** 麦克风权限现在有没有给。设置页每次回到前台都重读一次——授权也可能是在系统设置里做的。 */
internal fun microphoneGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, SettingsActivity.MICROPHONE_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 输入法在系统里的两种状态，**不能合成一个布尔值**：
 *
 * - [enabled]：系统设置里勾选了云隙输入（输入法列表里有它）；
 * - [active]：现在正在用的就是它（默认输入法）。
 *
 * 勾上了但还没切过去时，界面得说"还差一步"，而不是直接宣布"正在使用"。
 */
data class ImeStatus(val enabled: Boolean, val active: Boolean)

/**
 * 读一次系统的输入法状态，并在每次回到设置页时重读：勾选与切换都发生在另一个界面里，
 * 只在组合时读一次的话，用户切完输入法回来看到的还是旧状态。
 */
@Composable
internal fun rememberImeStatus(): ImeStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readImeStatus(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = readImeStatus(context) }
    return status
}

private fun readImeStatus(context: Context): ImeStatus {
    val manager = context.getSystemService(InputMethodManager::class.java)
    // InputMethodInfo.id 就是 DEFAULT_INPUT_METHOD 里那种短形式（pkg/.Class），两边可以直接比。
    val enabled: List<InputMethodInfo> = manager?.enabledInputMethodList.orEmpty()
    val active = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    )
    return ImeStatus(
        enabled = enabled.any { it.id == SettingsActivity.IME_ID },
        active = active == SettingsActivity.IME_ID,
    )
}
