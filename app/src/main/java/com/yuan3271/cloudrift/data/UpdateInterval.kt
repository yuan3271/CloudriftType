package com.yuan3271.cloudrift.data

import java.util.concurrent.TimeUnit

/**
 * How often to look for a new release.
 *
 * The names are what [SettingsRepository] persists, so an unknown key (an interval that a later
 * build adds, read by an older one) falls back to [Daily] instead of throwing.
 */
enum class UpdateInterval(val millis: Long) {
    /** Never checks on its own; the settings screen's 立即检测 still works. */
    Never(0L),

    /**
     * Checks every time the IME is shown. Only the *interval* is expressed here - the checker
     * itself drops a second request while one is still in flight, so tapping through five input
     * boxes in a row is one request, not five.
     */
    EveryKeyboardOpen(0L),

    Every6Hours(TimeUnit.HOURS.toMillis(6)),
    Daily(TimeUnit.DAYS.toMillis(1)),
    Weekly(TimeUnit.DAYS.toMillis(7)),
    Monthly(TimeUnit.DAYS.toMillis(30)),
    ;

    /**
     * Whether a check is due given the last attempt. Pure, so the frequency rules can be tested
     * without a window, a network or a Context.
     *
     * [Never] is never due; [EveryKeyboardOpen] has an interval of zero, so it is always due - the
     * caller is the one that only calls it when the keyboard actually opens.
     */
    fun isDue(lastCheckMillis: Long, nowMillis: Long): Boolean =
        this != Never && nowMillis - lastCheckMillis >= millis

    companion object {
        fun fromKey(key: String?): UpdateInterval =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Daily
    }
}
