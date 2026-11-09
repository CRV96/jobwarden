package com.jobwarden.inbox.service.impl;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.jspecify.annotations.Nullable;

/**
 * Trusts a mail server on this computer, such as Proton Mail Bridge, by the certificate it signed itself, which no
 * authority vouches for: while it's being connected, whichever it shows, which this remembers; after, only that one.
 * Between JobWarden and such a server nothing leaves the computer, so what matters is that the server stays the same.
 */
final class LocalServerTrust extends X509ExtendedTrustManager {

	private static final HexFormat FINGERPRINT = HexFormat.ofDelimiter(":").withUpperCase();

	private final @Nullable String expected;

	private volatile @Nullable String seen;

	/**
	 * @param expected the fingerprint of the one certificate to trust, or {@code null} to trust the one shown
	 */
	LocalServerTrust(@Nullable String expected) {
		this.expected = expected;
	}

	/**
	 * @return the fingerprint of the certificate the server showed, or {@code null} before it showed one
	 */
	@Nullable String seen() {
		return this.seen;
	}

	SSLSocketFactory socketFactory() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { this }, null);
			return context.getSocketFactory();
		} catch (GeneralSecurityException noTls) {
			throw new IllegalStateException("No TLS in this Java", noTls);
		}
	}

	/**
	 * @return the certificate's SHA-256 digest, as AB:CD:… like the browsers show it
	 */
	static String fingerprint(X509Certificate certificate) throws CertificateException {
		try {
			return FINGERPRINT.formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
		} catch (NoSuchAlgorithmException noSha256) {
			throw new IllegalStateException("No SHA-256 in this Java", noSha256);
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		check(chain);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
		check(chain);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		check(chain);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new CertificateException("Only a server is trusted here");
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
		throw new CertificateException("Only a server is trusted here");
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		throw new CertificateException("Only a server is trusted here");
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}

	private void check(X509Certificate @Nullable [] chain) throws CertificateException {
		if (chain == null || chain.length == 0) {
			throw new CertificateException("The server showed no certificate");
		}
		String shown = fingerprint(chain[0]);
		this.seen = shown;
		if (this.expected != null && !this.expected.equals(shown)) {
			throw new CertificateChanged(this.expected, shown);
		}
	}

	/**
	 * The server shows another certificate than the one trusted.
	 */
	static final class CertificateChanged extends CertificateException {

		CertificateChanged(String expected, String shown) {
			super("The server on this computer shows another certificate (" + shown + ") than when it was connected ("
					+ expected + ")");
		}

	}

}
