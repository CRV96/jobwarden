package com.jobwarden.updates.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The updates module's settings, bound and checked as the app starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UpdatesProperties.class)
class UpdatesConfiguration {

}
