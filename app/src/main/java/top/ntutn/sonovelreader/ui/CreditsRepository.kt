package top.ntutn.sonovelreader.ui

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

/** Metadata is bundled, never fetched from the network. User-imported fonts are not listed. */
data class CreditEntry(
    val id: String,
    val name: String,
    val version: String,
    val authors: String,
    val copyright: String,
    val license: String,
    val projectUrl: String,
    val licenseUrl: String,
    val texts: List<String>,
    val sourceUrl: String = "",
)

data class CreditsCatalog(val fonts: List<CreditEntry>, val libraries: List<CreditEntry>)

internal object CreditsRepository {
    fun load(assets: AssetManager): CreditsCatalog {
        val fonts = readArray(assets, "credits/fonts.json")
        val libraries = listOf("credits/libraries", "credits/local").flatMap { directory ->
            assets.list(directory).orEmpty().sorted().flatMap { file ->
                readArray(assets, "$directory/$file")
            }
        }
        return CreditsCatalog(fonts, libraries)
    }

    private fun readArray(assets: AssetManager, path: String): List<CreditEntry> {
        val array = assets.open(path).bufferedReader().use { JSONArray(it.readText()) }
        return (0 until array.length()).map { index -> parse(array.getJSONObject(index)) }
    }

    internal fun parse(item: JSONObject): CreditEntry {
        val texts = item.getJSONArray("texts")
        return CreditEntry(
            id = item.getString("id"), name = item.getString("name"),
            version = item.getString("version"), authors = item.getString("authors"),
            copyright = item.getString("copyright"), license = item.getString("license"),
            projectUrl = item.getString("projectUrl"), licenseUrl = item.getString("licenseUrl"),
            texts = (0 until texts.length()).map { texts.getString(it) },
            sourceUrl = item.optString("sourceUrl"),
        )
    }

    fun text(assets: AssetManager, entry: CreditEntry): String = entry.texts.joinToString("") { path ->
        require(!path.startsWith('/') && path.split('/').none { it == ".." })
        assets.open(path).bufferedReader().use { it.readText() }
    }
}
