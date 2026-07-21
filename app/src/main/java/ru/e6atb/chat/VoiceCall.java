package ru.e6atb.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.io.Closeable;
import java.io.IOException;

import rs.ove.crypt.proto.SecureSessionV4;

final class VoiceCall {
	static final String STATE_ENDED = "call_ended";
	static final String STATE_CONNECTED = "voice_connected";
	static final String STATE_CONNECTION_CLOSED = "voice_connection_closed";
	static final String STATE_MICROPHONE_PERMISSION_DENIED = "voice_microphone_permission_denied";
	static final String STATE_ERROR_PREFIX = "voice_error:";
	static final String STATE_SEND_ERROR_PREFIX = "voice_send_error:";

	private static final int SAMPLE_RATE = 8000;
	private static final int FRAME_BYTES = 640;

	private volatile boolean running;
	private SimpleWebSocket ws;
	private SecureSessionV4 crypt;
	private AudioRecord recorder;
	private AudioTrack player;
	private Thread readThread;
	private Thread micThread;
	private Listener listener;

	interface Listener {
		void onState(String text);
	}

	boolean running() {
		return running;
	}

	void start(final Context context, final String url, Listener listener) {
		if (running) {
			return;
		}
		this.listener = listener;
		running = true;
		readThread = new Thread(new Runnable() {
			@Override
			public void run() {
				VoiceCall.this.run(context == null ? null : context.getApplicationContext(), url);
			}
		}, "e6atb-voice-read");
		readThread.start();
	}

	void stop() {
		stop(true);
	}

	private void stop(boolean notify) {
		running = false;
		crypt = null;
		closeQuietly(ws);
		ws = null;
		releaseRecorder();
		releasePlayer();
		if (notify) state(STATE_ENDED);
	}

	private void run(Context context, String url) {
		boolean remoteClosed = false;
		try {
			ws = new SimpleWebSocket();
			ws.connect(url);
			crypt = connectCrypt(context, ws);
			setupPlayer(context);
			setupRecorder(context);
			player.play();
			recorder.startRecording();
			startMic();
			state(STATE_CONNECTED);

			while (running) {
				SimpleWebSocket.Frame frame = ws.readFrame();
				if (frame.opcode == SimpleWebSocket.CLOSE) {
					remoteClosed = true;
					break;
				}
				if (frame.opcode == SimpleWebSocket.BINARY && frame.payload.length > 0) {
					byte[] plain = crypt.openApplicationRecord(frame.payload);
					if (plain.length > 0) {
						player.write(plain, 0, plain.length);
					}
				}
			}
		} catch (SecurityException e) {
			state(STATE_MICROPHONE_PERMISSION_DENIED);
		} catch (IOException e) {
			if (running) {
				state(STATE_ERROR_PREFIX + errorText(e));
			}
		} catch (Exception e) {
			if (running) {
				state(STATE_ERROR_PREFIX + errorText(e));
			}
		} finally {
			if (running && remoteClosed) {
				state(STATE_CONNECTION_CLOSED);
			}
			stop(false);
		}
	}

	private SecureSessionV4 connectCrypt(Context context, SimpleWebSocket ws) throws Exception {
		SecureSessionV4.ClientHello hello = SecureSessionV4.createClientHello();
		byte[] message = hello.message();
		ws.sendBinary(message, message.length);
		SimpleWebSocket.Frame frame = ws.readFrame();
		if (frame.opcode != SimpleWebSocket.BINARY) {
			throw new IOException(text(context, R.string.status_crypto_handshake_failed));
		}
		return SecureSessionV4.openClient(hello, frame.payload);
	}

	private void setupPlayer(Context context) {
		int min = AudioTrack.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT
		);
		int size = Math.max(min, FRAME_BYTES * 8);
		player = new AudioTrack(
				AudioManager.STREAM_VOICE_CALL,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				size,
				AudioTrack.MODE_STREAM
		);
		if (player.getState() != AudioTrack.STATE_INITIALIZED) {
			throw new IllegalStateException(text(context, R.string.status_speaker_unavailable));
		}
	}

	private void setupRecorder(Context context) {
		if (context != null
				&& context.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
				!= PackageManager.PERMISSION_GRANTED) {
			throw new SecurityException(text(context, R.string.status_microphone_denied));
		}
		int min = AudioRecord.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT
		);
		int size = Math.max(min, FRAME_BYTES * 8);
		int source = MediaRecorder.AudioSource.MIC;
		if (android.os.Build.VERSION.SDK_INT >= 11) {
			source = audioSource("VOICE_COMMUNICATION", source);
		}
		recorder = new AudioRecord(
				source,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				size
		);
		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			throw new IllegalStateException(text(context, R.string.status_microphone_unavailable));
		}
	}

	private int audioSource(String name, int fallback) {
		try {
			return MediaRecorder.AudioSource.class.getField(name).getInt(null);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private void startMic() {
		micThread = new Thread(new Runnable() {
			@Override
			public void run() {
				byte[] buf = new byte[FRAME_BYTES];
				while (running) {
					int n = recorder.read(buf, 0, buf.length);
					if (n > 0) {
						try {
							byte[] plain = new byte[n];
							System.arraycopy(buf, 0, plain, 0, n);
							byte[] sealed = crypt.sealApplicationRecord(plain);
							ws.sendBinary(sealed, sealed.length);
						} catch (Exception e) {
							if (running) {
								state(STATE_SEND_ERROR_PREFIX + errorText(e));
							}
							running = false;
						}
					}
				}
			}
		}, "e6atb-voice-mic");
		micThread.start();
	}

	private void releaseRecorder() {
		AudioRecord r = recorder;
		recorder = null;
		if (r == null) {
			return;
		}
		try {
			r.stop();
		} catch (Exception ignored) {
		}
		r.release();
	}

	private void releasePlayer() {
		AudioTrack p = player;
		player = null;
		if (p == null) {
			return;
		}
		try {
			p.stop();
		} catch (Exception ignored) {
		}
		p.release();
	}

	private void state(String text) {
		Listener l = listener;
		if (l != null) {
			l.onState(text);
		}
	}

	private static void closeQuietly(Closeable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (Exception ignored) {
		}
	}

	private static String errorText(Throwable error) {
		if (error == null) return "";
		String message = error.getMessage();
		if (message == null || message.trim().length() == 0) {
			message = error.getClass().getSimpleName();
		}
		return message;
	}

	private static String text(Context context, int resId) {
		return context == null ? "" : context.getString(resId);
	}
}
