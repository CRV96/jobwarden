-- The AI assistant a profile works with: Claude Code or Codex. Its pages write their prompts and terminal commands for
-- that one; JobWarden's MCP server is the same for both.
ALTER TABLE users
    ADD COLUMN assistant VARCHAR(20) NOT NULL DEFAULT 'CLAUDE_CODE' CHECK (assistant IN ('CLAUDE_CODE', 'CODEX'));
