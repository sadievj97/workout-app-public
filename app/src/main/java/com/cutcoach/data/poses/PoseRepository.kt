package com.cutcoach.data.poses

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Loads poses.json, the same way the protocol is loaded: once, from assets,
 * with a readable error rather than a crash if it is malformed.
 */
object PoseRepository {

    private const val ASSET = "poses.json"

    @Volatile
    private var cached: PoseLibrary? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    fun load(context: Context): PoseLoad {
        cached?.let { return PoseLoad.Success(it) }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val result = parse(text)
        if (result is PoseLoad.Success) cached = result.library
        return result
    }

    /** Separate from [load] so tests can run the shipped file on the desktop. */
    fun parse(text: String): PoseLoad = try {
        PoseLoad.Success(json.decodeFromString<PoseLibrary>(text))
    } catch (e: Exception) {
        PoseLoad.Failure(e)
    }
}

sealed interface PoseLoad {
    data class Success(val library: PoseLibrary) : PoseLoad
    data class Failure(val cause: Throwable) : PoseLoad {
        val message: String
            get() = "Could not read poses.json: ${cause.message ?: cause::class.simpleName}"
    }
}
