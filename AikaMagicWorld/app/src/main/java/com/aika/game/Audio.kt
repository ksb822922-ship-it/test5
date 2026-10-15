package com.aika.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlin.math.PI
import kotlin.math.sin

/** Short beeps for game events (no asset files needed). */
object Sfx {
    private var tg: ToneGenerator? = null

    /** kind: 0 step, 1 sparkle/spell, 2 hit, 3 hurt, 4 win, 5 lose */
    fun play(kind: Int) {
        try {
            if (tg == null) tg = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            val tone = when (kind) {
                0 -> ToneGenerator.TONE_DTMF_5
                1 -> ToneGenerator.TONE_DTMF_9
                2 -> ToneGenerator.TONE_PROP_BEEP
                3 -> ToneGenerator.TONE_PROP_NACK
                4 -> ToneGenerator.TONE_PROP_ACK
                else -> ToneGenerator.TONE_CDMA_LOW_L
            }
            tg?.startTone(tone, if (kind == 0) 30 else 120)
        } catch (e: Exception) { /* sound is optional */ }
    }
}

/** Tiny procedurally generated looping melody (pentatonic arpeggio). */
object Music {
    private var track: AudioTrack? = null

    fun start() {
        try {
            track?.let { it.play(); return }
            val rate = 22050
            val notes = intArrayOf(440, 523, 659, 784, 659, 523, 587, 698)
            val len = rate / 4
            val data = ShortArray(notes.size * len)
            for (n in notes.indices) for (i in 0 until len) {
                val t = i.toDouble() / rate
                val env = 1.0 - i.toDouble() / len
                data[n * len + i] = (sin(2 * PI * notes[n] * t) * env * 2500).toInt().toShort()
            }
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC).build()
            t.write(data, 0, data.size)
            t.setLoopPoints(0, data.size, -1)
            t.setVolume(0.35f)
            t.play()
            track = t
        } catch (e: Exception) { /* music is optional */ }
    }

    fun pause() { try { track?.pause() } catch (e: Exception) { } }
}
