package com.jobwarden.mcp.controller;

import tools.jackson.databind.node.ObjectNode;

/**
 * A tool Claude can call: what tools/list says about it, and what calling it does.
 *
 * @param inputSchema the JSON Schema of its arguments
 * @param readOnly whether it only reads: clients may call it without asking
 * @param openWorld whether it reaches beyond JobWarden, to the web
 * @param handler returns a record or map, which becomes the result's JSON
 */
record McpTool(String name, String title, String description, ObjectNode inputSchema, boolean readOnly,
		boolean openWorld, Handler handler) {

	@FunctionalInterface
	interface Handler {

		/**
		 * @throws ToolException for arguments it can't use: the client gets the message as the tool's error
		 */
		Object call(ToolCall call);

	}

}
