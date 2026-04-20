package com.bytesmith.scriptler.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ScriptLog(
    val id: String = "",
    val scriptId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val runNumber: Int = 0,
    val output: String = "",
    val status: String = "success", // "success" or "error"
    val isError: Boolean = false
) : Serializable
