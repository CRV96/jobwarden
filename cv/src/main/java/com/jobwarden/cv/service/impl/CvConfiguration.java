package com.jobwarden.cv.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The CV module's settings, bound and checked as the app starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CvProperties.class)
class CvConfiguration {

}
