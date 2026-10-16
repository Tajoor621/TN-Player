package com.tplayer.mediaplayer.playback

import android.media.audiofx.*
import android.os.Bundle
import com.tplayer.mediaplayer.data.Settings

/** Effects belong to the service's audio session, not to a short-lived Activity. */
class AudioEffects(private val settings: Settings) {
    private var sessionId = 0
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    fun attach(id: Int) {
        if (id <= 0 || id == sessionId) return
        release(); sessionId = id
        eq = try { Equalizer(0, id).let { if (it.hasControl()) it else { it.release(); null } } } catch (_: RuntimeException) { null }
        bass = try { BassBoost(0, id).let { if (it.hasControl() && it.strengthSupported) it else { it.release(); null } } } catch (_: RuntimeException) { null }
        virtualizer = try { Virtualizer(0, id).let { if (it.hasControl() && it.strengthSupported) it else { it.release(); null } } } catch (_: RuntimeException) { null }
        try {
            eq?.let { effect ->
                val levels = settings.eqLevels.split(',').mapNotNull { it.toIntOrNull() }
                val range = effect.bandLevelRange
                if (levels.size == effect.numberOfBands.toInt()) levels.forEachIndexed { i, level -> effect.setBandLevel(i.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
                effect.enabled = settings.eqEnabled
            }
            bass?.apply { setStrength(settings.bass.toShort()); enabled = settings.bass > 0 }
            virtualizer?.apply { setStrength(settings.virtualizer.toShort()); enabled = settings.virtualizer > 0 }
        } catch (_: RuntimeException) { release() }
    }
    fun state(): Bundle = Bundle().apply {
        val effect = eq
        val count = effect?.numberOfBands?.toInt()?.coerceIn(0, 32) ?: 0
        putBoolean("eq", effect != null); putBoolean("enabled", effect?.enabled == true)
        putIntArray("frequencies", IntArray(count) { effect!!.getCenterFreq(it.toShort()) / 1000 })
        putIntArray("levels", IntArray(count) { effect!!.getBandLevel(it.toShort()).toInt() })
        putInt("min", effect?.bandLevelRange?.get(0)?.toInt() ?: -1500)
        putInt("max", effect?.bandLevelRange?.get(1)?.toInt() ?: 1500)
        putBoolean("bass", bass != null); putInt("bassStrength", settings.bass)
        putBoolean("virtualizer", virtualizer != null); putInt("virtualizerStrength", settings.virtualizer)
    }
    fun apply(args: Bundle) {
        val enabled = args.getBoolean("enabled")
        val levels = args.getIntArray("levels") ?: intArrayOf()
        val bassValue = args.getInt("bassStrength"); val virtualValue = args.getInt("virtualizerStrength")
        require(bassValue in 0..1000 && virtualValue in 0..1000)
        val effect = eq
        require(bass != null || bassValue == 0)
        require(virtualizer != null || virtualValue == 0)
        if (effect != null) {
            val range = effect.bandLevelRange
            require(levels.size == effect.numberOfBands.toInt() && levels.all { it in range[0].toInt()..range[1].toInt() })
            levels.forEachIndexed { index, level -> effect.setBandLevel(index.toShort(), level.toShort()) }
            check(effect.setEnabled(enabled) == AudioEffect.SUCCESS)
        } else require(!enabled && levels.isEmpty())
        bass?.let { it.setStrength(bassValue.toShort()); check(it.setEnabled(bassValue > 0) == AudioEffect.SUCCESS) } ?: require(bassValue == 0)
        virtualizer?.let { it.setStrength(virtualValue.toShort()); check(it.setEnabled(virtualValue > 0) == AudioEffect.SUCCESS) } ?: require(virtualValue == 0)
        settings.eqEnabled = enabled; settings.eqLevels = levels.joinToString(",")
        settings.bass = bassValue; settings.virtualizer = virtualValue
    }
    fun release() {
        eq?.release(); bass?.release(); virtualizer?.release()
        eq = null; bass = null; virtualizer = null; sessionId = 0
    }
}
