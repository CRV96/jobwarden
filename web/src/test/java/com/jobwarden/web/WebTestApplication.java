package com.jobwarden.web;

import com.jobwarden.web.view.Dates;
import com.jobwarden.web.view.Names;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Web tests like {@code @WebMvcTest} start from a {@code @SpringBootApplication} class. The real one lives
 * in the app module, which this module can't see, so this one stands in for it during tests.
 * <p>
 * {@code @WebMvcTest} only picks up MVC components (controllers, advice, configurers), so the helpers the templates
 * call are imported here, for every web test.
 */
@SpringBootApplication
@Import({ Dates.class, Names.class })
class WebTestApplication {

}
