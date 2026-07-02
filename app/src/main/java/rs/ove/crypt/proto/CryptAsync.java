package rs.ove.crypt.proto;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CryptAsync implements Closeable {
	private final CryptSession session;
	private final ExecutorService executor;
	private final boolean ownsExecutor;

	public CryptAsync(CryptSession session) {
		this(session, Executors.newSingleThreadExecutor(), true);
	}

	public CryptAsync(CryptSession session, ExecutorService executor) {
		this(session, executor, false);
	}

	private CryptAsync(CryptSession session, ExecutorService executor, boolean ownsExecutor) {
		if (session == null) {
			throw new IllegalArgumentException("session is required");
		}
		if (executor == null) {
			throw new IllegalArgumentException("executor is required");
		}
		this.session = session;
		this.executor = executor;
		this.ownsExecutor = ownsExecutor;
	}

	public Future<byte[]> seal(final byte[] plain) {
		return executor.submit(new Callable<byte[]>() {
			@Override
			public byte[] call() throws Exception {
				return session.seal(plain);
			}
		});
	}

	public Future<byte[]> open(final byte[] sealed) {
		return executor.submit(new Callable<byte[]>() {
			@Override
			public byte[] call() throws Exception {
				return session.open(sealed);
			}
		});
	}

	@Override
	public void close() {
		if (ownsExecutor) {
			executor.shutdownNow();
		}
	}
}
