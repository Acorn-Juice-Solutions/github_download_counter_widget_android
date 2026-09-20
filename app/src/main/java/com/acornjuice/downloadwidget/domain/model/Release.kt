package com.acornjuice.downloadwidget.domain.model

/**
 * A GitHub release snapshot as consumed by the widget.
 *
 * @property tag Release tag (e.g. `v1.0.0`).
 * @property assets Downloadable assets attached to this release, in the order returned by the API.
 * @property fetchedAtEpochSeconds Unix timestamp of when this snapshot was fetched from the API.
 */
data class Release(
    val tag: String,
    val assets: List<Asset>,
    val fetchedAtEpochSeconds: Long,
) {
    val totalDownloads: Int get() = assets.sumOf { it.downloadCount }
}
