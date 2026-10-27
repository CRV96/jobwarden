package com.jobwarden.common.net;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAddressesTests {

	@Test
	void allowsOnlyThePublicInternet() throws IOException {
		for (String local : new String[] { "127.0.0.1", "0.0.0.0", "10.1.2.3", "172.16.0.1", "192.168.1.1",
				"169.254.169.254", "100.64.0.1", "224.0.0.1", "::1", "fd00::1", "fe80::1" }) {
			assertThat(PublicAddresses.isPublic(InetAddress.getByName(local))).as(local).isFalse();
		}
		for (String remote : new String[] { "8.8.8.8", "151.101.1.1", "2001:4860:4860::8888" }) {
			assertThat(PublicAddresses.isPublic(InetAddress.getByName(remote))).as(remote).isTrue();
		}
	}

}
