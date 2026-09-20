package com.acornjuice.downloadwidget.data.remote

import com.acornjuice.downloadwidget.domain.model.Asset
import com.acornjuice.downloadwidget.domain.model.Release
import org.json.JSONException
import org.json.JSONObject

/**
 * Pure JSON → [Release] conversion.
 *
 * Consumes the response body of GitHub's
 * `GET /repos/{owner}/{repo}/releases/tags/{tag}` endpoint, which is a single release object.
 * No Android APIs, no I/O — safe to unit-test on the JVM without Robolectric.
 */
object ReleaseParser {

    private const val KEY_TAG = "tag_name"
    private const val KEY_ASSETS = "assets"
    private const val KEY_ASSET_NAME = "name"
    private const val KEY_ASSET_DOWNLOAD_COUNT = "download_count"
    private const val DEFAULT_ASSET_NAME = "asset"

    /**
     * @param json The response body as a single release JSON object.
     * @param fetchedAtEpochSeconds Wall-clock timestamp injected by the caller.
     * @return The parsed [Release].
     * @throws JSONException If [json] is malformed or does not contain `tag_name`.
     */
    fun parse(json: String, fetchedAtEpochSeconds: Long): Release {
        val root = JSONObject(json)
        val tag = root.optString(KEY_TAG).takeIf { it.isNotBlank() }
            ?: throw JSONException("Response is missing '$KEY_TAG'")

        val assetsArray = root.optJSONArray(KEY_ASSETS)
        val assets = if (assetsArray == null) {
            emptyList()
        } else {
            buildList(capacity = assetsArray.length()) {
                for (i in 0 until assetsArray.length()) {
                    val obj = assetsArray.optJSONObject(i) ?: continue
                    add(
                        Asset(
                            name = obj.optString(KEY_ASSET_NAME, DEFAULT_ASSET_NAME).ifBlank { DEFAULT_ASSET_NAME },
                            downloadCount = obj.optInt(KEY_ASSET_DOWNLOAD_COUNT, 0).coerceAtLeast(0),
                        ),
                    )
                }
            }
        }
        return Release(tag = tag, assets = assets, fetchedAtEpochSeconds = fetchedAtEpochSeconds)
    }
}
