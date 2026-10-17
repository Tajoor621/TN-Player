package com.tplayer.mediaplayer.subtitle

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Bounded, local text subtitle import. Original files are never rewritten. */
data class SubtitleCue(val start: Long, val end: Long, val text: String)
data class SubtitleDocument(val cues: List<SubtitleCue>) {
    fun adjacent(position: Long, direction: Int, delayMs: Long): Long? {
        val starts = cues.map { (it.start + delayMs).coerceAtLeast(0) }.distinct()
        return if (direction > 0) starts.firstOrNull { it > position + 100 }
            else starts.lastOrNull { it < position - 100 }
    }
    fun srt(delayMs: Long): String {
        require(delayMs in -600000L..600000L) { "Subtitle delay must be within 10 minutes." }
        return buildString {
            var index = 0
            for (cue in cues) {
                val end = cue.end + delayMs
                if (end <= 0) continue
                append(++index).append('\n').append(stamp((cue.start + delayMs).coerceAtLeast(0)))
                    .append(" --> ").append(stamp(end)).append('\n').append(cue.text).append("\n\n")
            }
        }
    }
    private fun stamp(ms: Long) = String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
}
object SubtitleImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CUES = 20000
    private const val MAX_TIME = 604800000L
    fun read(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Subtitles must be smaller than 2 MB." }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    }
    fun parse(text: String, name: String): SubtitleDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Subtitles must be smaller than 2 MB." }
        require(!text.contains('\u0000')) { "This is not a text subtitle file." }
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines()
        require(lines.all { it.length <= 16384 }) { "Subtitle line is too long." }
        val cues = mutableListOf<SubtitleCue>()
        fun add(start: Long, end: Long, value: String) {
            require(start in 0..MAX_TIME && end in 1..MAX_TIME && end > start) { "Invalid subtitle timing." }
            require(cues.size < MAX_CUES) { "Too many subtitle cues." }
            val clean = value.trim()
            if (clean.isNotBlank()) cues += SubtitleCue(start, end, clean)
        }
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val arrow = Regex("^\\s*(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\s*-->\\s*(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})(?:\\s+.*)?$")
        if (extension in setOf("srt", "vtt")) {
            var i = 0
            while (i < lines.size) {
                val match = arrow.matchEntire(lines[i++]) ?: continue
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) body += lines[i++]
                add(clock(match.groupValues[1]), clock(match.groupValues[2]), body.joinToString("\n"))
            }
        } else if (extension == "mpl") {
            val pattern = Regex("^\\[(\\d+)]\\[(\\d+)](.*)$")
            for (line in lines.filter { it.isNotBlank() }) {
                val m = pattern.matchEntire(line) ?: error("Invalid MPL2 subtitle line.")
                add(tenths(m.groupValues[1]), tenths(m.groupValues[2]), m.groupValues[3].replace('|', '\n'))
            }
        } else if (extension == "pjs") {
            val pattern = Regex("^(\\d+),\\s*(\\d+),\\s*\"(.*)\"$")
            for (line in lines.filter { it.isNotBlank() }) {
                val m = pattern.matchEntire(line.trim()) ?: error("Invalid PJS subtitle line.")
                add(tenths(m.groupValues[1]), tenths(m.groupValues[2]), m.groupValues[3].replace('|', '\n'))
            }
        } else if (extension == "sub" && lines.firstOrNull { it.isNotBlank() }?.startsWith("{") == true) {
            val pattern = Regex("^\\{(\\d+)}\\{(\\d+)}(.*)$")
            var fps: Double? = null
            for (line in lines.filter { it.isNotBlank() }) {
                val m = pattern.matchEntire(line) ?: error("Invalid MicroDVD subtitle line.")
                val start = m.groupValues[1].toLong(); val end = m.groupValues[2].toLong()
                require(start in 0..145152000 && end in 0..145152000) { "Invalid MicroDVD frame index." }
                if (fps == null && start == end && start in 0..1) {
                    fps = m.groupValues[3].toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..240.0 }
                    require(fps != null) { "Invalid MicroDVD frame rate." }; continue
                }
                val rate = fps ?: error("MicroDVD requires a {1}{1} frame-rate header; no frame rate is guessed.")
                add((start * 1000 / rate).toLong(), (end * 1000 / rate).toLong(), m.groupValues[3].replace('|', '\n'))
            }
        } else if (extension == "sub") {
            val pattern = Regex("^(\\d{1,3}:\\d{2}:\\d{2}[.,]\\d{1,3}),(\\d{1,3}:\\d{2}:\\d{2}[.,]\\d{1,3})$")
            var i = 0
            while (i < lines.size) {
                val m = pattern.matchEntire(lines[i++].trim()) ?: continue
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) body += lines[i++]
                add(clock(m.groupValues[1]), clock(m.groupValues[2]), body.joinToString("\n").replace("[br]", "\n"))
            }
        } else if (extension == "txt") {
            val pattern = Regex("^(\\d{1,3}:\\d{2}:\\d{2}):(.*)$")
            val timed = lines.filter { it.isNotBlank() }.map { line ->
                val m = pattern.matchEntire(line) ?: error("TXT subtitles must use TMPlayer h:mm:ss:text timing.")
                clock(m.groupValues[1]) to m.groupValues[2].replace('|', '\n')
            }
            require(timed.zipWithNext().all { it.first.first < it.second.first }) { "TMPlayer times must increase." }
            timed.forEachIndexed { index, entry -> add(entry.first, timed.getOrNull(index + 1)?.first ?: (entry.first + 5000), entry.second) }
        } else error("Choose SRT, VTT, MPL2 (.mpl), PJS, MicroDVD/SubViewer (.sub), or TMPlayer (.txt). ASS/SSA and TTML use the native renderer.")
        require(cues.isNotEmpty()) { "No valid subtitle cues found." }
        return SubtitleDocument(cues.sortedBy { it.start })
    }
    private fun tenths(value: String): Long = value.toLong().also { require(it in 0..6048000) { "Invalid subtitle timestamp." } } * 100
    private fun clock(value: String): Long {
        val parts = value.replace(',', '.').split(':')
        require(parts.size in 2..3)
        val hours = if (parts.size == 3) parts[0].toLong() else 0L
        val minutes = parts[parts.size - 2].toLong()
        val seconds = parts.last().split('.')
        val whole = seconds[0].toLong(); val fraction = seconds.getOrNull(1).orEmpty()
        require(hours in 0..168 && minutes in 0..59 && whole in 0..59 && fraction.length <= 3) { "Invalid subtitle timestamp." }
        return hours * 3600000 + minutes * 60000 + whole * 1000 + fraction.padEnd(3, '0').toLong()
    }
}
