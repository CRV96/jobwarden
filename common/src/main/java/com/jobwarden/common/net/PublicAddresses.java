package com.jobwarden.common.net;

import java.net.InetAddress;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Which addresses the server may fetch from when the address comes from a posting or a link someone saved: only the
 * public internet, never this machine or the local network, which a link could otherwise reach through the server.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PublicAddresses {

	/**
	 * @return whether the address is on the public internet: not this machine, the local network, a link-local or
	 * multicast address, the carrier-grade NAT range (100.64.0.0/10) or an IPv6 unique local one (fc00::/7)
	 */
	public static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			// 100.64.0.0/10
			return !((bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64);
		}
		// fc00::/7
		return (bytes[0] & 0xFE) != 0xFC;
	}

}
