package com.acornjuice.downloadwidget.data.local

/**
 * Per-widget user configuration.
 *
 * @property apiUrl Base URL for GitHub's releases endpoint (`.../repos/{owner}/{name}/releases`).
 * @property tag Release tag (`v1.2.3`).
 *
 * A widget is [isComplete] when both fields are non-blank. Blank fields drive the
 * [com.acornjuice.downloadwidget.ui.widget.WidgetState.UnconfiguredEmpty] state.
 */
data class WidgetConfig(
    val apiUrl: String,
    val tag: String,
) {
    val isComplete: Boolean get() = apiUrl.isNotBlank() && tag.isNotBlank()

    companion object {
        val EMPTY: WidgetConfig = WidgetConfig(apiUrl = "", tag = "")
    }
}
