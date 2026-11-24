package com.jobwarden.web.view;

import org.springframework.stereotype.Component;

/**
 * Where the browser extension is installed from: its pages on the Chrome Web Store (for Chrome, Edge, Brave and the
 * other Chromium browsers) and on Firefox Add-ons, which each open in the visitor's language. For the templates:
 * {@code ${@extensionStores.chrome()}}.
 */
@Component("extensionStores")
public class ExtensionStores {

	public static final String CHROME = "https://chromewebstore.google.com/detail/bjklifdmffkhiffbicofeohgdcpmobek";

	public static final String FIREFOX = "https://addons.mozilla.org/firefox/addon/jobwarden/";

	public String chrome() {
		return CHROME;
	}

	public String firefox() {
		return FIREFOX;
	}

}
