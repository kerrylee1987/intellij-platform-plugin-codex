# Codex IntelliJ Plugin

Codex brings a VS Code–style assistant workflow into IntelliJ IDEA (253 / 2025.3) by pairing a chat tool window with your local `codex` CLI. It supports contextual requests, code insertion, and patch application without requiring an API key in the UI.

<!-- Plugin description -->
Codex adds a dedicated tool window for chat, can include the active editor selection or file as context, and applies Codex responses back into your code (insert or unified diff). Authentication is delegated to the locally installed `codex` CLI—no API key prompts inside the IDE.
<!-- Plugin description end -->

## Features

- **Codex Tool Window** with message list, status, cancel button, and input.
- **Context controls**: send selected text or the current file as context.
- **CLI execution**: runs `codex chat` via IntelliJ process APIs with timeout and cancellation.
- **Apply changes**: insert code blocks at the caret or apply unified diff patches with undo support.
- **Settings**: configure Codex CLI path, timeout, and max context length.

## Requirements

- IntelliJ IDEA 253 / 2025.3 (or compatible)
- `codex` CLI installed and logged in

## Getting Started

1. **Install dependencies & build**
   ```bash
   ./gradlew clean build
   ```

2. **Run the IDE with the plugin**
   ```bash
   ./gradlew runIde
   ```

3. **Open the Codex Tool Window**
   - View → Tool Windows → **Codex**

4. **Configure Codex**
   - Settings → Tools → **Codex**
   - Leave CLI path blank to use `codex` from `PATH`, or set an absolute path.

## Usage

1. Type a prompt in the Codex tool window and click **Send** (Ctrl+Enter).
2. Optional: click **Use Selection** or **Use File** to add editor context.
3. When Codex responds:
   - **Insert to Editor** inserts the first code block at the caret.
   - **Apply Patch** applies a unified diff to the current file.

### Editor Action

Use the editor action to send selected text:

- **Send Selection to Codex** (Right-click in editor or press `Ctrl+Alt+C`)

## Validation

Run the verification task to ensure plugin wiring and tests pass:

```bash
./gradlew verifyCodexPlugin
```

This runs unit tests and verifies:
- Tool window registration
- Action registration
- Core class loadability

## Troubleshooting

| Issue | Fix |
| --- | --- |
| `Codex CLI not found` | Install the CLI and ensure `codex` is on `PATH`, or set an absolute CLI path in Settings. |
| `Codex CLI is not logged in` | Run `codex login` in a terminal. |
| Requests timeout | Increase timeout in Settings → Tools → Codex. |
| Patch fails | Ensure the diff targets the current file and includes correct context lines. |
