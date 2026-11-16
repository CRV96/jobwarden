package com.jobwarden.market.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The market module's settings, bound and checked as the app starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketProperties.class)
class MarketConfiguration {

}
