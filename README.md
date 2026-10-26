# QuickQuestion for Claude

An IntelliJ Platform plugin for quick, throwaway questions about the code you have selected.

Select code in the editor, then use **Ask Claude** from the floating code toolbar, the editor context menu, or
`Ctrl+Alt+K`. A movable, resizable popup opens next to the selection with a multi-turn chat. It stays open while you
look back at the code (Esc or the close button closes it). **Move to sidebar** moves the live chat, with the same
process and transcript, into a tab of a right-hand tool window; closing the tab ends the chat.

## Requirements

- IntelliJ IDEA 2026.1 or newer (build 261+), or another IntelliJ Platform IDE of that version.
- The [Claude Code CLI](https://docs.claude.com/en/docs/claude-code), installed and signed in.
  The plugin finds `claude` via **Settings | Tools | QuickQuestion for Claude**, then your login-shell `PATH`, then
  `~/.local/bin`, `~/.claude/local`, `/opt/homebrew/bin` and `/usr/local/bin`.

## Privacy

The selected code and your questions are sent to Anthropic through the Claude Code CLI. Claude may also read other
files in the project to answer (read-only tools: `Read`, `Grep`, `Glob`).

## How it works

One headless `claude -p --input-format stream-json --output-format stream-json` process per chat, started when the
popup opens and killed when the chat closes. Follow-up questions are written as JSON lines to the same process's
stdin; sessions are never persisted, continued or resumed, so nothing shows up in `claude --resume`.

## Build

```bash
./gradlew buildPlugin verifyPlugin   # distribution in build/distributions
./gradlew runIde                     # sandbox IDE with the plugin
./gradlew test
```

Kotlin, Gradle wrapper, IntelliJ Platform Gradle Plugin 2.x, JDK 21 toolchain. Gson is bundled.

## Releasing

Secrets are read from environment variables and never stored in the repo:

| Variable | Meaning |
|---|---|
| `QQ_CERTIFICATE_CHAIN_FILE` | Path to the signing certificate chain (`chain.crt`) |
| `QQ_PRIVATE_KEY_FILE` | Path to the unencrypted private key (`private.pem`) |
| `QQ_PRIVATE_KEY_PASSWORD` | Key password (can be anything for an unencrypted key) |
| `QQ_PUBLISH_TOKEN` | JetBrains Marketplace token, only for `publishPlugin` |

```bash
./gradlew buildPlugin verifyPlugin signPlugin   # signed zip in build/distributions
```

The first upload to the Marketplace is done by hand on the plugin site; later releases can use `publishPlugin`.

_Independent plugin, not affiliated with or endorsed by Anthropic._

## License

[MIT](LICENSE).
