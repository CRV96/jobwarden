package com.jobwarden.inbox.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The inbox module's settings, bound and checked as the app starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InboxProperties.class)
class InboxConfiguration {

}
