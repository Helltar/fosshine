package com.helltar.fosshine.protocol

/**
 * The choices the Fossibot+ settings screen offers for the timer registers, in register-value order:
 * the value written is the index. Each entry is a duration in seconds, 0 meaning "never".
 *
 * These follow the app's settings screen, not its register table, which describes some of them differently.
 */
object Options {

    val SCREEN_OFF_TIME = listOf(0, 30, 60, 5 * 60, 10 * 60, 30 * 60)

    /** The whole station powers off after this long with every output closed. */
    val SHUTDOWN_TIME = listOf(0, 5 * 60, 10 * 60, 3600, 8 * 3600)

    /** An output turns itself off after this long with no load; shared by AC, DC and USB. */
    val IDLE_OFF = listOf(0, 30 * 60, 3600, 4 * 3600, 8 * 3600, 12 * 3600, 24 * 3600)
}
