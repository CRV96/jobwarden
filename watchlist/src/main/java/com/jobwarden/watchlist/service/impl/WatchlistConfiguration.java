package com.jobwarden.watchlist.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The watchlist module's settings, bound and checked as the app starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WatchlistProperties.class)
class WatchlistConfiguration {

}
