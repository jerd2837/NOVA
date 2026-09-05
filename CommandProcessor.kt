package com.nova.assistantlite

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

sealed class NovaAction {
    data class OpenUrl(val url: String) : NovaAction()
    data class SearchWeb(val query: String) : NovaAction()
    data class SearchYouTube(val query: String) : NovaAction()
    data class OpenKnownSite(val site: String, val url: String) : NovaAction()
    data class LaunchApp(val name: String) : NovaAction()
}

object CommandProcessor {

    fun execute(context: Context, raw: String): String {
        val actions = parse(raw)
        if (actions.isEmpty()) return "I couldn't understand that command."

        actions.forEach { runAction(context, it) }

        return if (actions.size > 1)
            "Completed ${actions.size} actions."
        else describe(actions.first())
    }

    fun parse(raw: String): List<NovaAction> {
        val clean = raw.trim()
        if (clean.isBlank()) return emptyList()

        val parts = clean.split(
            Regex("""\s+(?:and\s+then|then|after\s+that)\s+""", RegexOption.IGNORE_CASE)
        )

        return parts.flatMap { parseSingle(it.trim()) }
    }

    private fun parseSingle(text: String): List<NovaAction> {
        val lower = text.lowercase(Locale.getDefault())

        // Any URL gets priority.
        extractUrl(text)?.let { return listOf(NovaAction.OpenUrl(it)) }

        val youtubeIntent = listOf(
            "youtube", "watch", "play", "video", "music", "song", "listen"
        ).any { lower.contains(it) }

        val webSearchPatterns = listOf(
            "search google for", "google", "look up", "lookup", "find out about", "search for"
        )

        val knownSites = mapOf(
            "facebook" to "https://www.facebook.com",
            "youtube" to "https://www.youtube.com",
            "google" to "https://www.google.com",
            "instagram" to "https://www.instagram.com",
            "tiktok" to "https://www.tiktok.com",
            "gmail" to "https://mail.google.com",
            "wikipedia" to "https://www.wikipedia.org"
        )

        knownSites.entries.firstOrNull { Regex("""\b${Regex.escape(it.key)}\b""").containsMatchIn(lower) }
            ?.let { site ->
                if (lower.matches(Regex(""".*\b(open|launch|take me to|go to|visit)\b.*${Regex.escape(site.key)}.*"""))) {
                    return listOf(NovaAction.OpenKnownSite(site.key, site.value))
                }
            }

        if (youtubeIntent) {
            val query = extractQuery(lower)
            if (query.isNotBlank() && query !in listOf("youtube", "video", "music")) {
                return listOf(NovaAction.SearchYouTube(query))
            }
        }

        if (webSearchPatterns.any { lower.contains(it) }) {
            val query = extractQuery(lower)
            if (query.isNotBlank()) return listOf(NovaAction.SearchWeb(query))
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val name = lower.replaceFirst(Regex("""^(open|launch|start)\s+"""), "").trim()
            knownSites[name]?.let { return listOf(NovaAction.OpenKnownSite(name, it)) }
            if (name.contains(".")) return listOf(NovaAction.OpenUrl(normalizeUrl(name)))
            return listOf(NovaAction.LaunchApp(name))
        }

        // Natural fallback: entertainment/watch requests go to YouTube.
        if (lower.contains("i want to watch") || lower.contains("show me") || lower.contains("find me")) {
            val query = extractQuery(lower)
            if (query.isNotBlank()) return listOf(NovaAction.SearchYouTube(query))
        }

        // Final fallback: web search.
        return listOf(NovaAction.SearchWeb(text))
    }

    private fun extractQuery(input: String): String {
        var q = input
        q = q.replace(Regex("""^(can you|please|could you|i want to|i'd like to|help me)\s+"""), "")
        q = q.replace(Regex("""\b(open|launch|start|go to|take me to|search|find|look up|lookup|play|watch|listen to|show me|find me)\b"""), " ")
        q = q.replace(Regex("""\b(on|in|using)\s+(youtube|google)\b"""), " ")
        q = q.replace(Regex("""\b(for me|please|something|videos?|video)\b"""), " ")
        return q.replace(Regex("""\s+"""), " ").trim()
    }

    private fun runAction(context: Context, action: NovaAction) {
        when (action) {
            is NovaAction.OpenUrl -> openUrl(context, action.url)
            is NovaAction.OpenKnownSite -> openUrl(context, action.url)
            is NovaAction.SearchWeb ->
                openUrl(context, "https://www.google.com/search?q=" + Uri.encode(action.query))
            is NovaAction.SearchYouTube ->
                openUrl(context, "https://www.youtube.com/results?search_query=" + Uri.encode(action.query))
            is NovaAction.LaunchApp -> launchByName(context, action.name)
        }
    }

    private fun launchByName(context: Context, name: String) {
        // Android does not provide a universal safe API to launch arbitrary apps by visible name.
        // Fall back to a web search if no exact package mapping is known.
        val packageMap = mapOf(
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana",
            "gmail" to "com.google.android.gm"
        )

        val packageName = packageMap[name.lowercase()]
        val intent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }

        if (intent != null) {
            context.startActivity(intent)
        } else {
            openUrl(context, "https://www.google.com/search?q=" + Uri.encode(name))
        }
    }

    private fun describe(action: NovaAction): String = when (action) {
        is NovaAction.OpenUrl -> "Opening link."
        is NovaAction.OpenKnownSite -> "Opening ${action.site}."
        is NovaAction.SearchWeb -> "Searching the web for ${action.query}."
        is NovaAction.SearchYouTube -> "Searching YouTube for ${action.query}."
        is NovaAction.LaunchApp -> "Trying to launch ${action.name}."
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("""((https?://)?([A-Za-z0-9-]+\.)+[A-Za-z]{2,}(/[^\s]*)?)""")
        return regex.find(text)?.value?.let { normalizeUrl(it) }
    }

    private fun normalizeUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"

    private fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
