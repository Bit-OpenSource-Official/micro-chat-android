package ru.e6atb.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import rs.ove.crypt.proto.NativeMst5
import java.io.IOException
import java.util.Locale

internal class VoiceCall {
    interface Listener { fun onState(text: String?) }
    @Volatile private var active = false; @Volatile private var stream = 0L; private var recorder: AudioRecord? = null; private var player: AudioTrack? = null; private var readThread: Thread? = null; private var micThread: Thread? = null; private var listener: Listener? = null
    fun running(): Boolean = active
    fun start(context: Context?, access: MST5.VoiceAccess?, listener: Listener?) { if (active) return; this.listener = listener; active = true; readThread = Thread({ run(context?.applicationContext, access) }, "e6atb-voice-read").also { it.start() } }
    fun stop() = stop(true)
    private fun stop(notify: Boolean) { active = false; val current = stream; stream = 0; NativeMst5.closeVoice(current); releaseRecorder(); releasePlayer(); if (notify) state(STATE_ENDED) }
    private fun run(context: Context?, access: MST5.VoiceAccess?) {
        var remoteClosed = false
        try {
            val current = access ?: throw IOException("voice access is unavailable"); stream = NativeMst5.openVoice(current.endpoint, current.serverPublicKey, current.ticket); setupPlayer(context); setupRecorder(context); player!!.play(); recorder!!.startRecording(); startMic(); state(STATE_CONNECTED)
            while (active) { val pcm = NativeMst5.receiveVoice(stream); if (pcm.isNotEmpty()) player?.write(pcm, 0, pcm.size) }
        } catch (_: SecurityException) { state(STATE_MICROPHONE_PERMISSION_DENIED) }
        catch (error: IOException) { if (active) { remoteClosed = connectionClosed(error); if (!remoteClosed) state(STATE_ERROR_PREFIX + errorText(error)) } }
        catch (error: Exception) { if (active) state(STATE_ERROR_PREFIX + errorText(error)) }
        finally { if (active && remoteClosed) state(STATE_CONNECTION_CLOSED); stop(false) }
    }
    private fun setupPlayer(context: Context?) { val size = maxOf(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), FRAME_BYTES * 8); player = AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM); if (player!!.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException(text(context, R.string.status_speaker_unavailable)) }
    private fun setupRecorder(context: Context?) { if (context != null && context.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException(text(context, R.string.status_microphone_denied)); val size = maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), FRAME_BYTES * 8); val source = if (android.os.Build.VERSION.SDK_INT >= 11) audioSource("VOICE_COMMUNICATION", MediaRecorder.AudioSource.MIC) else MediaRecorder.AudioSource.MIC; recorder = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size); if (recorder!!.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException(text(context, R.string.status_microphone_unavailable)) }
    private fun audioSource(name: String, fallback: Int): Int = try { MediaRecorder.AudioSource::class.java.getField(name).getInt(null) } catch (_: Exception) { fallback }
    private fun startMic() { micThread = Thread({ val buffer = ByteArray(FRAME_BYTES); while (active) { val count = recorder?.read(buffer, 0, buffer.size) ?: -1; if (count > 0) try { NativeMst5.sendVoice(stream, buffer.copyOf(count)) } catch (error: Exception) { if (active) state(STATE_SEND_ERROR_PREFIX + errorText(error)); active = false } } }, "e6atb-voice-mic").also { it.start() } }
    private fun releaseRecorder() { recorder?.let { try { it.stop() } catch (_: Exception) {}; it.release() }; recorder = null }
    private fun releasePlayer() { player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }; player = null }
    private fun state(text: String) { listener?.onState(text) }
    private fun errorText(error: Throwable?): String { if (error == null) return ""; return error.message?.takeIf { it.trim().isNotEmpty() } ?: error.javaClass.simpleName }
    private fun connectionClosed(error: Throwable): Boolean = errorText(error).lowercase(Locale.US).let { it.contains("stream closed") || it.contains("connection closed") || it.contains("broken pipe") }
    private fun text(context: Context?, resId: Int): String = context?.getString(resId) ?: ""
    companion object { @JvmField val STATE_ENDED = "call_ended"; @JvmField val STATE_CONNECTED = "voice_connected"; @JvmField val STATE_CONNECTION_CLOSED = "voice_connection_closed"; @JvmField val STATE_MICROPHONE_PERMISSION_DENIED = "voice_microphone_permission_denied"; @JvmField val STATE_ERROR_PREFIX = "voice_error:"; @JvmField val STATE_SEND_ERROR_PREFIX = "voice_send_error:"; private const val SAMPLE_RATE = 8000; private const val FRAME_BYTES = 640 }
}
