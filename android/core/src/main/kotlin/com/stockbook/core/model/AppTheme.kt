package com.stockbook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Dark or light. Two, and no "System".
 *
 * Following the phone would make the app's appearance somebody else's decision:
 * the shop owner and the phone's owner are not always the same person, and a
 * counter under a shop light does not change its mind at sunset. That is the same
 * reasoning [com.stockbook.core.text.AppLanguage] already follows, and the reason
 * this is stored with the shop rather than read from the device.
 *
 * The serial names are **persisted and exported**, and match what the iOS build
 * writes, so a backup carried between the two keeps its theme.
 */
@Serializable
enum class AppTheme {
    @SerialName("dark") DARK,
    @SerialName("light") LIGHT;

    companion object {
        /** What the app was drawn in, and what it opens in until told otherwise. */
        val default = DARK
    }
}
