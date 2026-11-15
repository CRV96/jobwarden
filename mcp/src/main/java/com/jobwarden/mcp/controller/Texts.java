package com.jobwarden.mcp.controller;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * The texts the tools save, cut to what JobWarden keeps of them.
 */
final class Texts {

	private Texts() {
	}

	/**
	 * @return the text, cut to {@code maxLength} characters, the last one an ellipsis; {@code null} for {@code null}
	 */
	@Contract("null, _ -> null; !null, _ -> !null")
	static @Nullable String shortened(@Nullable String text, int maxLength) {
		return (text == null || text.length() <= maxLength) ? text : text.substring(0, maxLength - 1) + "…";
	}

}
