package com.jobwarden.common.net;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * The one way the server makes an HTTP client, for every call it makes out: to the APIs it reads (GitHub, the ECB,
 * Eurostat, Adzuna, the job boards) and to the addresses pages give it (logos, postings). Each client has a connect
 * timeout and does its own work on virtual threads; a request still sets its own timeout, as only it knows how long its
 * answer may take.
 */
public final class HttpClients {

	private HttpClients() {
	}

	/**
	 * For an API: redirects followed, except from https to http.
	 */
	public static HttpClient followingRedirects(Duration connectTimeout) {
		return builder(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/**
	 * For an address the caller checks at each hop (a page's, which mustn't lead to this computer or its network:
	 * {@link PublicAddresses}), or one that carries a key no other host should see.
	 */
	public static HttpClient notFollowingRedirects(Duration connectTimeout) {
		return builder(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
	}

	/**
	 * @return how JobWarden names itself to an API, e.g. JobWarden/1.2.0 (+https://github.com/CRV96/jobwarden)
	 */
	public static String userAgent(String version) {
		return "JobWarden/" + version + " (+https://github.com/CRV96/jobwarden)";
	}

	private static HttpClient.Builder builder(Duration connectTimeout) {
		return HttpClient.newBuilder().connectTimeout(connectTimeout).executor(Executors.newVirtualThreadPerTaskExecutor());
	}

}
