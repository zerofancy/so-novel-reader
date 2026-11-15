package top.ntutn.sonovelreader.data.fonts

/** Immutable official static Regular resources. Source/checksums also ship in fonts/manifest.json. */
data class BuiltinFont(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val version: String,
    val sha256: String,
    val bytes: Long,
    val licenseAssetPath: String,
) {
    fun entry() = FontEntry(id, displayName, FontSource.BUILTIN, sha256)
}

object BuiltinFonts {
    val fonts: List<BuiltinFont> = listOf(
        BuiltinFont(
            "builtin:zhuque", "朱雀仿宋", "fonts/ZhuqueFangsong-Regular.ttf", "v0.212",
            "558c62730844fe54ba220146ed62f859d4e2880188d92d985f8921c6e3743bc4",
            8_824_084, "fonts/licenses/zhuque.txt",
        ),
        BuiltinFont(
            "builtin:chill-duan-hei-song", "寒蝉端黑宋", "fonts/ChillDuanHeiSongRegular.otf", "v1.200",
            "48dd423be190731ae4b39fe0b2d3edcf44efcfafa9d92546d8116b30dae3c854",
            12_921_692, "fonts/licenses/chill-duan-hei-song.txt",
        ),
        BuiltinFont(
            "builtin:lxgw-wenkai", "霞鹜文楷", "fonts/LXGWWenKai-Regular.ttf", "v1.530",
            "2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b",
            25_928_940, "fonts/licenses/lxgw-wenkai.txt",
        ),
    )
    val entries: List<FontEntry> = listOf(FontEntry(SYSTEM_FONT_ID, "系统默认", FontSource.SYSTEM)) +
        fonts.map { it.entry() }
}
