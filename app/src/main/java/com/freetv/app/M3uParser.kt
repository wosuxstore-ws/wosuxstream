package com.freetv.app

import java.io.BufferedReader

/** Minimal, fast M3U/M3U8 playlist parser (supports logos, groups, referrer and user-agent options). */
object M3uParser {

    private val ATTR = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()

        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var referrer: String? = null
        var userAgent: String? = null

        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR.findAll(line).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2]
                    }
                    val comma = line.lastIndexOf(',')
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    name = title.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { "Channel" }
                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    referrer = null
                    userAgent = null
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val kv = line.substringAfter(':')
                    val key = kv.substringBefore('=').trim().lowercase()
                    val value = kv.substringAfter('=', "").trim()
                    when (key) {
                        "http-referrer", "http-referer" -> referrer = value.ifBlank { null }
                        "http-user-agent" -> userAgent = value.ifBlank { null }
                    }
                }

                line.startsWith("#") -> Unit

                else -> {
                    val n = name
                    val isWeb = line.startsWith("http://") || line.startsWith("https://")
                    if (n != null && isWeb && seen.add(line)) {
                        out.add(Channel(n, line, logo, group, referrer, userAgent))
                    }
                    name = null
                    logo = null
                    group = null
                    referrer = null
                    userAgent = null
                }
            }
        }
        return out
    }
}
