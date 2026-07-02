package rs.ove.crypt.proto;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class CompatSocketConnector {
	private static final int DEFAULT_TIMEOUT_MS = 10000;

	private CompatSocketConnector() {
	}

	public static Socket connect(String host, int port, boolean tls, int timeoutMs) throws IOException {
		if (host == null || host.trim().length() == 0) {
			throw new IOException("server host is required");
		}
		if (port < 1 || port > 65535) {
			throw new IOException("server port is invalid");
		}
		int timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
		String cleanHost = stripBrackets(host.trim());
		InetAddress[] addresses = InetAddress.getAllByName(cleanHost);
		ArrayList<InetAddress> ordered = orderForDevice(cleanHost, addresses);
		ArrayList<String> errors = new ArrayList<String>();
		IOException last = null;
		for (int i = 0; i < ordered.size(); i++) {
			InetAddress address = ordered.get(i);
			Socket base = new Socket();
			try {
				base.connect(new InetSocketAddress(address, port), Math.min(timeout, DEFAULT_TIMEOUT_MS));
				base.setSoTimeout(timeout);
				if (!tls) {
					return base;
				}
				SSLSocket ssl = (SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(base, cleanHost, port, true);
				ssl.setSoTimeout(timeout);
				ssl.startHandshake();
				return ssl;
			} catch (IOException ex) {
				last = ex;
				errors.add(address.getHostAddress() + ":" + port + " - " + message(ex));
				try {
					base.close();
				} catch (IOException ignored) {
				}
			}
		}
		String suffix = errors.isEmpty() ? "" : "; tried " + join(errors);
		String hint = hasOnlyIpv6(addresses) ? "; DNS returned only IPv6 and this network has no IPv6 route; use an IPv6-capable network or add an A record for IPv4 fallback" : "";
		throw new IOException("connect failed to " + cleanHost + ":" + port + suffix + hint, last);
	}

	private static ArrayList<InetAddress> orderForDevice(String host, InetAddress[] addresses) {
		ArrayList<InetAddress> out = new ArrayList<InetAddress>();
		if (addresses == null) {
			return out;
		}
		if (isIpv6Literal(host) || preferIpv6()) {
			addType(out, addresses, Inet6Address.class);
			addType(out, addresses, Inet4Address.class);
		} else if (isOldAndroid()) {
			addType(out, addresses, Inet4Address.class);
			addType(out, addresses, Inet6Address.class);
		} else {
			addOriginalOrder(out, addresses);
		}
		addOriginalOrder(out, addresses);
		return out;
	}

	private static void addOriginalOrder(List<InetAddress> out, InetAddress[] addresses) {
		for (int i = 0; i < addresses.length; i++) {
			if (!contains(out, addresses[i])) {
				out.add(addresses[i]);
			}
		}
	}

	private static void addType(List<InetAddress> out, InetAddress[] addresses, Class type) {
		for (int i = 0; i < addresses.length; i++) {
			if (type.isInstance(addresses[i]) && !contains(out, addresses[i])) {
				out.add(addresses[i]);
			}
		}
	}

	private static boolean contains(List<InetAddress> values, InetAddress address) {
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i).equals(address)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasOnlyIpv6(InetAddress[] addresses) {
		if (addresses == null || addresses.length == 0) {
			return false;
		}
		boolean hasIpv6 = false;
		boolean hasIpv4 = false;
		for (int i = 0; i < addresses.length; i++) {
			if (addresses[i] instanceof Inet6Address) {
				hasIpv6 = true;
			} else if (addresses[i] instanceof Inet4Address) {
				hasIpv4 = true;
			}
		}
		return hasIpv6 && !hasIpv4;
	}

	private static boolean isOldAndroid() {
		try {
			String sdk = System.getProperty("ro.build.version.sdk");
			if (sdk != null && sdk.length() > 0) {
				return Integer.parseInt(sdk) <= 10;
			}
		} catch (Throwable ignored) {
		}
		try {
			Class version = Class.forName("android.os.Build$VERSION");
			return version.getField("SDK_INT").getInt(null) <= 10;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean preferIpv6() {
		try {
			return "true".equalsIgnoreCase(System.getProperty("rs.ove.crypt.prefer_ipv6"));
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isIpv6Literal(String host) {
		return host != null && host.indexOf(':') >= 0;
	}

	private static String stripBrackets(String host) {
		if (host != null && host.startsWith("[") && host.endsWith("]")) {
			return host.substring(1, host.length() - 1);
		}
		return host;
	}

	private static String message(Throwable ex) {
		String msg = ex == null ? null : ex.getMessage();
		if (msg == null || msg.length() == 0) {
			msg = ex == null ? "unknown" : ex.getClass().getName();
		}
		return msg;
	}

	private static String join(List<String> values) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				out.append(" | ");
			}
			out.append(values.get(i));
		}
		return out.toString();
	}
}
