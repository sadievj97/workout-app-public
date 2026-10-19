package com.cutcoach.data.protocol

import android.content.Context

/**
 * Where the phone keeps the protocol.
 *
 * [ProtocolRepository] lives in `:core` and has no Android in it, so it cannot
 * open an asset: it asks for the text of a named file and does not care where
 * the text comes from. This is the phone's answer — the app's own assets — and
 * it is the whole of what the module boundary cost.
 *
 * Named for where it reads rather than just "load", because that is now a real
 * distinction: a watch app answers the same question differently, and the two
 * sitting side by side with the same name would invite reading one as the
 * other. The session plan and the rules it feeds are identical on both, by
 * construction rather than by discipline.
 */
fun ProtocolRepository.Companion.loadFromAssets(context: Context): ProtocolLoad =
    load { name -> context.readAssetOrNull(name) }

/** Null when the asset is not there, which is normal for the override. */
private fun Context.readAssetOrNull(name: String): String? =
    runCatching { assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
