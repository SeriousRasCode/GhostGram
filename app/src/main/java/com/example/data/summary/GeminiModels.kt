package com.example.data.summary

import org.json.JSONObject

internal object GeminiModels {
    // Only text Flash models documented with a free tier are selected automatically.
    // The catalog advertises API support, not the project's quota or billing tier.
    private val preferred = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash")

    fun supported(json: String): List<String> {
        val models = JSONObject(json).optJSONArray("models") ?: return emptyList()
        return (0 until models.length()).mapNotNull { index ->
            val model = models.optJSONObject(index) ?: return@mapNotNull null
            val id = model.optString("name").removePrefix("models/")
            val methods = model.optJSONArray("supportedGenerationMethods")
            if (GeminiSummaryProtocol.validModel(id) && methods != null &&
                (0 until methods.length()).any { methods.optString(it) == "generateContent" }) id else null
        }
    }

    fun choose(available: List<String>, selection: String): String {
        if (selection != "auto") {
            check(selection in available) { "This Gemini connection isn't available. Reconnect in settings." }
            return selection
        }
        return preferred.firstOrNull { it in available }
            ?: throw IllegalStateException("No compatible Gemini connection was found. Check your key in Google AI Studio.")
    }
}
