package rs.ove.crypt.proto;

import android.os.ParcelFileDescriptor;

/** File-descriptor facade for Rust mst5-client media streams. */
public final class Mst5MediaClient {
	public interface Observer {
		boolean isCancelled();
		void onProgress(long completed, long total);
	}

	private Mst5MediaClient() {}

	public static final class Upload {
		public final String endpoint;
		public final String publicKey;
		public final String ticket;
		public final String fileId;
		public final long size;
		public final ParcelFileDescriptor source;

		public Upload(String endpoint, String publicKey, String ticket, String fileId,
		              long size, ParcelFileDescriptor source) {
			this.endpoint = endpoint;
			this.publicKey = publicKey;
			this.ticket = ticket;
			this.fileId = fileId;
			this.size = size;
			this.source = source;
		}
	}

	public static void uploadDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                    String fileId, long size, ParcelFileDescriptor source,
	                                    final Observer observer) throws Exception {
		NativeMst5.upload(endpoint, publicKeyB64, ticket, fileId, size, source, adapt(observer));
	}

	public static void uploadE2EDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                      String fileId, long plaintextSize, ParcelFileDescriptor source,
	                                      long identityHandle, byte[] peer, String from, String to,
	                                      final Observer observer) throws Exception {
		NativeMst5.uploadE2E(endpoint, publicKeyB64, ticket, fileId, plaintextSize, source,
				identityHandle, peer, from, to, adapt(observer));
	}

	public static void uploadDescriptors(java.util.List<Upload> uploads, final Observer observer) throws Exception {
		if (uploads == null || uploads.isEmpty()) return;
		int count = uploads.size();
		String[] endpoints = new String[count];
		String[] publicKeys = new String[count];
		String[] tickets = new String[count];
		String[] fileIds = new String[count];
		long[] sizes = new long[count];
		int[] fds = new int[count];
		for (int i = 0; i < count; i++) {
			Upload upload = uploads.get(i);
			if (upload == null || upload.source == null) throw new java.io.IOException("media descriptor is unavailable");
			endpoints[i] = upload.endpoint;
			publicKeys[i] = upload.publicKey;
			tickets[i] = upload.ticket;
			fileIds[i] = upload.fileId;
			sizes[i] = upload.size;
			fds[i] = upload.source.getFd();
		}
		NativeMst5.Observer nativeObserver = adapt(observer);
		if (nativeObserver == null) {
			nativeObserver = new NativeMst5.Observer() {
				@Override public boolean isCancelled() { return false; }
				@Override public void onProgress(long completed, long total) {}
			};
		}
		NativeMst5.uploadBatch(endpoints, publicKeys, tickets, fileIds, sizes, fds, nativeObserver);
	}

	public static long downloadDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                     String fileId, long expectedSize, ParcelFileDescriptor target,
	                                     final Observer observer) throws Exception {
		return NativeMst5.download(endpoint, publicKeyB64, ticket, fileId, expectedSize, target, adapt(observer));
	}

	public static long downloadE2EDescriptor(String endpoint, String publicKeyB64, String ticket,
	                                        String fileId, long encryptedSize, ParcelFileDescriptor target,
	                                        long identityHandle, byte[] peer, String from, String to,
	                                        final Observer observer) throws Exception {
		return NativeMst5.downloadE2E(endpoint, publicKeyB64, ticket, fileId, encryptedSize, target,
				identityHandle, peer, from, to, adapt(observer));
	}

	public static byte[] downloadE2EBytes(String endpoint, String publicKeyB64, String ticket,
	                                    String fileId, long encryptedSize, int maxBytes, long identityHandle,
	                                    byte[] peer, String from, String to) throws Exception {
		return NativeMst5.downloadE2EBytes(endpoint, publicKeyB64, ticket, fileId, encryptedSize,
				maxBytes, identityHandle, peer, from, to);
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
