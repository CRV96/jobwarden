package com.jobwarden.web.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Controller;

/**
 * A controller whose pages render inside layout.html: {@link LayoutAdvice} gives each of them what the sidebar shows.
 * The other controllers (images, files to download, the calendar feed) answer without it, and without its queries.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller
@interface PageController {

}
