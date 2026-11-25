package io.github.icenux.ccc.data

/**
 * Whether ccc can be reached at all (#72). The app counts the failed attempts to reach it (the event stream
 * that keeps reconnecting, the first load of the list) and, after [limit] in a row with no answer between,
 * says the server is unreachable — so a phone paired with a computer that is gone can offer to connect to
 * another one, instead of loading for ever. A short break in the network is only a few failures.
 */
class Reachability(private val limit: Int = LIMIT) {
    private var failures = 0

    /** An attempt failed: true when the server now counts as unreachable. */
    fun failed(): Boolean {
        if (failures < limit) failures++
        return unreachable
    }

    /** ccc answered (or the person asked to try again): count from zero. */
    fun reached() {
        failures = 0
    }

    val unreachable: Boolean get() = failures >= limit

    companion object {
        /** Reconnecting waits 1, 2, 4 s (and each try can take up to 10 s to give up): about 15 s. */
        const val LIMIT = 3
    }
}
