package dev.co508.drums.ui.practice

import android.content.Context

/** The practice screen's two views (DECISIONS.md, "Two Practice Views; Falling Notes Is The Default"). */
enum class PracticeView { FALLING_NOTES, SHEET }

/**
 * Remembers which view was used last. A plain SharedPreferences value until
 * the Settings screen brings DataStore (#18); the key is all that moves.
 */
class PracticeViewPreference(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var view: PracticeView
        get() =
            PracticeView.entries.firstOrNull { it.name == preferences.getString(KEY, null) }
                ?: PracticeView.FALLING_NOTES
        set(value) = preferences.edit().putString(KEY, value.name).apply()

    private companion object {
        const val FILE = "practice"
        const val KEY = "view"
    }
}
