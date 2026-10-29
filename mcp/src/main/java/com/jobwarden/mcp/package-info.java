/**
 * MCP: a Model Context Protocol server, so Claude can work on the job hunt. It reads the jobs, checks whether their
 * postings are still up and updates their status, and adds the jobs it finds for the saved searches to the AI Assistant
 * page. The protocol is JSON-RPC over HTTP (the Streamable HTTP transport, answering with JSON only), at
 * {@code jobwarden.mcp.path}.
 * <p>
 * Adapter only, like {@code capture}: no business logic and no tables. It calls the identity, jobs, search and
 * discover modules.
 */
package com.jobwarden.mcp;
