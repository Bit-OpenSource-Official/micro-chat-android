package rs.ove.crypt.proto;

import android.os.ParcelFileDescriptor;

/** File-descriptor facade for Rust mst5-client media streams. */
public final class Mst5MediaClient {
	public interface Observer {
		boolean isCancelled();
		void onProgress(long completed, long total);
	}

	private Mst5MediaClient() {}

	public static void uploadDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                    String fileId, long size, ParcelFileDescriptor source,
	                                    final Observer observer) throws Exception {
		NativeMst5.upload(endpoint, publicKeyB64, ticket, fileId, size, source, adapt(observer));
	}

	public static long downloadDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                     String fileId, long expectedSize, ParcelFileDescriptor target,
	                                     final Observer observer) throws Exception {
		return NativeMst5.download(endpoint, publicKeyB64, ticket, fileId, expectedSize, target, adapt(observer));
	}

	public static byte[] downloadBytes(String endpoint, String publicKeyB64, String ticket,
	                                   String fileId, long expectedSize, int maxBytes) throws Exception {
		return NativeMst5.downloadBytes(endpoint, publicKeyB64, ticket, fileId, expectedSize, maxBytes);
	}

	private static NativeMst5.Observer adapt(final Observer observer) {
		if (observer == null) return null;
		return new NativeMst5.Observer() {
			@Override public boolean isCancelled() { return observer.isCancelled(); }
			@Override public void onProgress(long completed, long total) {
				observer.onProgress(completed, total);
			}
		};
	}
}
