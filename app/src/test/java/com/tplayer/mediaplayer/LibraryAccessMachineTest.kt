package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.LibraryAccessMachine
import com.tplayer.mediaplayer.data.LibraryAccessState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAccessMachineTest {
    private val machine = LibraryAccessMachine()
    private val flags = listOf(false, true)

    @Test fun everyPermissionSnapshotOnLegacyGranularAndSelectedVideoApis() {
        val legacy = listOf(LibraryAccessState.NEEDS_REQUEST, LibraryAccessState.DENIED_PERMANENTLY,
            LibraryAccessState.NEEDS_REQUEST, LibraryAccessState.DENIED_PERMANENTLY) + List(4) { LibraryAccessState.FULL }
        val selected = listOf(LibraryAccessState.NEEDS_REQUEST, LibraryAccessState.DENIED_PERMANENTLY,
            LibraryAccessState.PARTIAL, LibraryAccessState.PARTIAL) + List(4) { LibraryAccessState.FULL }
        for ((api, expected) in mapOf(28 to legacy, 33 to legacy, 34 to selected, 36 to selected)) {
            val actual = flags.flatMap { full ->
                flags.flatMap { partial -> flags.map { blocked -> machine.state(full, partial, blocked, api) } }
            }
            assertEquals("API $api", expected, actual)
        }
    }

    @Test fun upgradeDenialAndSettingsRecoveryUseCurrentGrants() {
        val states = listOf(Triple(false, false, false), Triple(false, true, false),
            Triple(false, true, true), Triple(true, false, true), Triple(false, false, true),
            Triple(true, false, true))
        assertEquals(listOf(LibraryAccessState.NEEDS_REQUEST, LibraryAccessState.PARTIAL,
            LibraryAccessState.PARTIAL, LibraryAccessState.FULL, LibraryAccessState.DENIED_PERMANENTLY,
            LibraryAccessState.FULL), states.map { (full, partial, blocked) -> machine.state(full, partial, blocked, 34) })
    }
}
