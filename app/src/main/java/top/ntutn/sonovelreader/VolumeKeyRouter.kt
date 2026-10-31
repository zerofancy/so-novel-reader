package top.ntutn.sonovelreader

/** Keeps a complete physical press with the owner chosen on its first DOWN. */
internal class VolumeKeyRouter {
    private val owners = mutableMapOf<Int, Boolean>()

    fun down(key: Int, repeat: Int, canceled: Boolean, handle: () -> Boolean): Boolean {
        if (repeat > 0 || canceled) return owners[key] == true
        // A second, overlapping key cannot turn another page.
        val overlapping = owners.isNotEmpty() && key !in owners
        val consumed = if (overlapping) owners.values.any { it } else handle()
        owners[key] = consumed
        return consumed
    }

    fun up(key: Int): Boolean = owners.remove(key) == true
}
