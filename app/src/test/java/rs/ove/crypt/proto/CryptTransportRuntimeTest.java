package rs.ove.crypt.proto;

import org.junit.Assume;
import org.junit.Test;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CryptTransportRuntimeTest {
	@Test
	public void javaClientTalksToV5Server() throws Exception {
		String address = System.getenv("MICROMSG_TEST_ADDR");
		String publicKey = System.getenv("MICROMSG_TEST_PUBLIC_KEY_B64");
		Assume.assumeTrue(address != null && publicKey != null);
		System.setProperty("rs.ove.crypt.server_public_key_b64", publicKey);
		String login = "runtime_java_" + Long.toString(System.nanoTime(), 36);
		byte[] body = ("{\"username\":\"" + login + "\",\"password\":\"secret\"}").getBytes("UTF-8");
		final CryptTcpClient client = new CryptTcpClient();
		CryptTcpClient.Response response = client.request(
				address,
				"",
				"POST",
				"/register",
				body,
				10000
		);
		assertEquals(new String(response.body(), "UTF-8"), 200, response.code());
		assertTrue(new String(response.body(), "UTF-8").contains(login));
		final String token = new JSONObject(new String(response.body(), "UTF-8")).getString("token");
		final CountDownLatch done = new CountDownLatch(8);
		final Throwable[] failures = new Throwable[8];
		for (int i = 0; i < failures.length; i++) {
			final int index = i;
			new Thread(new Runnable() {
				@Override public void run() {
					try {
						CryptTcpClient.Response me = client.request(address, token, "GET", "/me", null, 10000);
						assertEquals(200, me.code());
					} catch (Throwable error) {
						failures[index] = error;
					} finally {
						done.countDown();
					}
				}
			}, "mst5-runtime-" + i).start();
		}
		done.await();
		for (Throwable failure : failures) {
			if (failure != null) throw new AssertionError("multiplexed request failed", failure);
		}

		byte[] directData = new byte[] {0, 1, 2, 3, (byte)255};
		String clientMessageId = "runtime-media-" + Long.toString(System.nanoTime(), 36);
		JSONObject mediaItem = new JSONObject();
		mediaItem.put("client_id", "runtime-attachment-0001");
		mediaItem.put("name", "runtime.bin");
		mediaItem.put("mime", "application/octet-stream");
		mediaItem.put("size", directData.length);
		JSONObject quoteBody = new JSONObject();
		quoteBody.put("media", new org.json.JSONArray().put(mediaItem));
		CryptTcpClient.Response quoted = client.request(address, token, "POST", "/media/quote",
				quoteBody.toString().getBytes("UTF-8"), 10000);
		assertEquals(new String(quoted.body(), "UTF-8"), 200, quoted.code());
		long dsrRequired = new JSONObject(new String(quoted.body(), "UTF-8")).optLong("dsr_amount");
		JSONObject initBody = new JSONObject();
		initBody.put("to", login);
		initBody.put("text", "runtime media");
		initBody.put("client_message_id", clientMessageId);
		initBody.put("max_dsr_amount", dsrRequired);
		initBody.put("media", new org.json.JSONArray().put(mediaItem));
		CryptTcpClient.Response initialized = client.request(address, token, "POST", "/messages/prepare",
				initBody.toString().getBytes("UTF-8"), 10000);
		Assume.assumeTrue("runtime account needs a funded DSR wallet", initialized.code() == 200);
		JSONObject operation = new JSONObject(new String(initialized.body(), "UTF-8"));
		JSONObject upload = operation.getJSONArray("uploads").getJSONObject(0);
		HttpURLConnection put = (HttpURLConnection)new URL(upload.getString("upload_url")).openConnection();
		put.setRequestMethod("PUT");
		put.setRequestProperty("Authorization", "Bearer " + upload.getString("ticket"));
		put.setFixedLengthStreamingMode(directData.length);
		put.setDoOutput(true);
		OutputStream output = put.getOutputStream();
		output.write(directData);
		output.close();
		assertEquals(200, put.getResponseCode());
		put.disconnect();
		JSONObject completeBody = new JSONObject();
		completeBody.put("operation_id", operation.getString("operation_id"));
		CryptTcpClient.Response completed = client.request(address, token, "POST", "/messages/commit",
				completeBody.toString().getBytes("UTF-8"), 10000);
		assertEquals(new String(completed.body(), "UTF-8"), 200, completed.code());
		assertTrue(new String(completed.body(), "UTF-8").contains("runtime.bin"));
		CryptTcpClient.Response downloadTicket = client.request(address, token, "GET",
				"/file/ticket?id=" + upload.getString("file_id"), null, 10000);
		assertEquals(200, downloadTicket.code());
		JSONObject download = new JSONObject(new String(downloadTicket.body(), "UTF-8"));
		InputStream input = new URL(download.getString("download_url")).openStream();
		ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
		int value;
		while ((value = input.read()) >= 0) downloaded.write(value);
		input.close();
		assertTrue(java.util.Arrays.equals(directData, downloaded.toByteArray()));
	}
}
