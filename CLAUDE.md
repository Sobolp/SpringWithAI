# CLAUDE.md

## Code style

- Indent with spaces only. Never use tab characters, in any file (Java, Gradle, properties, YAML, etc.).
- Use 4 spaces per indentation level.

## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues, using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
