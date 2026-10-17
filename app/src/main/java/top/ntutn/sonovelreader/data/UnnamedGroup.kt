package top.ntutn.sonovelreader.data

internal fun nextUnnamedGroupName(existing: Set<String>): String {
    val base = "未命名分类"
    if (base !in existing) return base
    var suffix = 2
    while ("$base $suffix" in existing) suffix++
    return "$base $suffix"
}
