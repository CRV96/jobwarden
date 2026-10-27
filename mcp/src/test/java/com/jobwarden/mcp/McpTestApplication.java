package com.jobwarden.mcp;

import com.jobwarden.search.QueryGenerator;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Web tests like {@code @WebMvcTest} start from a {@code @SpringBootApplication} class. The real one lives
 * in the app module, which this module can't see, so this one stands in for it during tests. The search module's
 * query generator has no state, so the tests use the real one.
 */
@SpringBootApplication
@Import(QueryGenerator.class)
class McpTestApplication {

}
