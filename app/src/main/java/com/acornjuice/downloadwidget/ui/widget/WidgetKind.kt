package com.acornjuice.downloadwidget.ui.widget

import androidx.annotation.LayoutRes
import com.acornjuice.downloadwidget.R

/**
 * The two flavors of widget provider this app ships. Encapsulates the (provider class, layout)
 * pair so [com.acornjuice.downloadwidget.worker.RefreshWorker] doesn't have to reflect on
 * class names.
 */
enum class WidgetKind(
    val providerClass: Class<out BaseDownloadWidgetProvider>,
    @LayoutRes val layoutRes: Int,
) {
    FULL(DownloadWidgetProvider::class.java, R.layout.widget_layout),
    SMALL(DownloadWidgetSmallProvider::class.java, R.layout.widget_layout_small),
    ;

    companion object {
        /** Look up by the fully-qualified provider class name, or `null` if unrecognized. */
        fun fromProviderClassName(name: String): WidgetKind? =
            entries.firstOrNull { it.providerClass.name == name }
    }
}
