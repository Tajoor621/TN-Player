package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.LibraryAccessMachine
import com.tplayer.mediaplayer.data.LibraryAccessState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAccessMachineTest {
    private val machine = LibraryAccessMachine()
    private val flags = listOf(false, true)

    @Test fun everyPermissionSnapshotOnLegacyGranularAndSelectedVideoApis() {
        for (api in listOf(28, 33, 34, 36)) {
            for (full in flags) for (partial in flags) for (blocked in flags) {
                val expected = when {
                    full -> LibraryAccessState.FULL
                    partial && api >= 34 -> LibraryAccessState.PARTIAL
                    blocked -> LibraryAccessState.DENIED_PERMANENTLY
                    else -> LibraryAccessState.NEEDS_REQUEST
                }
                assertEquals("API $api full=$full selected=$partial blocked=$blocked",
                    expected, machine.state(full, partial, blocked, api))
            }
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
