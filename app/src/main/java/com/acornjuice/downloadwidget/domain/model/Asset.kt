package com.acornjuice.downloadwidget.domain.model

/**
 * A single downloadable file attached to a GitHub release.
 *
 * @property name Human-readable filename of the asset (e.g. `app-1.0.0.apk`).
 * @property downloadCount Number of times the asset has been downloaded. Non-negative.
 */
data class Asset(
    val name: String,
    val downloadCount: Int,
) {
    init {
        require(downloadCount >= 0) { "downloadCount must be >= 0, was $downloadCount" }
    }
}
