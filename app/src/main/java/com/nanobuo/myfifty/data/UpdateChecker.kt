package com.nanobuo.myfifty.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Release pubblicata su GitHub con l'APK allegato. */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/**
 * Controlla le GitHub Releases del repository dell'app e scarica l'APK.
 * Il repository deve essere pubblico (API senza autenticazione).
 */
class UpdateChecker(private val repo: String) {

    @Serializable
    private data class GhAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GhAsset> = emptyList(),
    )

    /** Ultima release stabile con un APK allegato, oppure null. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/$repo/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                404 -> return@withContext null // nessuna release pubblicata
                in 200..299 -> {}
                else -> error("GitHub ha risposto ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Scarica l'APK in [dest], riportando l'avanzamento 0..1. */
    suspend fun download(release: AppRelease, dest: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode !in 200..299) error("Download non riuscito (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (total > 0 && tmp.length() != total) error("Download incompleto")
            dest.delete()
            check(tmp.renameTo(dest)) { "Impossibile salvare l'aggiornamento" }
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "myFifty-android")
        }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun parse(text: String): AppRelease? {
            val r = json.decodeFromString(GhRelease.serializer(), text)
            if (r.draft || r.prerelease) return null
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk", true) && !it.name.contains("debug", true) }
                ?: return null
            return AppRelease(r.tag.removePrefix("v"), r.body.orEmpty().trim(), apk.url, apk.size, r.htmlUrl)
        }

        /**
         * Confronto tra versioni "1.2.3" / "1.2.3-dev.4" / "1.2.3-debug".
         * A parità di numeri, una versione con suffisso è più vecchia di quella senza.
         */
        fun compare(a: String, b: String): Int {
            fun split(v: String): Pair<List<Int>, String?> {
                val clean = v.trim().removePrefix("v")
                val main = clean.substringBefore('-')
                val pre = clean.substringAfter('-', "").ifEmpty { null }
                return main.split('.').map { it.toIntOrNull() ?: 0 } to pre
            }
            val (na, pa) = split(a)
            val (nb, pb) = split(b)
            for (i in 0 until maxOf(na.size, nb.size)) {
                val c = (na.getOrElse(i) { 0 }).compareTo(nb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return when {
                pa == null && pb == null -> 0
                pa == null -> 1
                pb == null -> -1
                else -> pa.compareTo(pb)
            }
        }

        fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0
    }
}
