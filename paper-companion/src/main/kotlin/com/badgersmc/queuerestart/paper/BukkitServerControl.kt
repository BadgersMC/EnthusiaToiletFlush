package com.badgersmc.queuerestart.paper

import org.bukkit.Bukkit

/**
 * REQ-021. Paper-bound [ServerControl].
 *
 *  * `shutdown()`  → `Bukkit.shutdown()` (clean stop; the process supervisor
 *    typically restarts the JVM).
 *  * `dispatchConsoleCommand(cmd)` → `Bukkit.dispatchCommand(consoleSender, cmd)`,
 *    routed through the main thread (Bukkit requires it).
 *  * `exitProcess(code)` → `System.exit(code)` for hard restart on a code path
 *    the supervisor watches (see `restart-mode: EXIT_CODE`).
 *
 * Behaviour validated under T-100 (e2e on a live Paper backend).
 */
class BukkitServerControl : ServerControl {

    override fun shutdown() {
        Bukkit.shutdown()
    }

    override fun dispatchConsoleCommand(command: String) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
    }

    override fun exitProcess(code: Int) {
        System.exit(code)
    }
}
