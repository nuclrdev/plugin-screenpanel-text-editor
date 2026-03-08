# Nuclr Screen Panel — Text Editor

An official [Nuclr Commander](https://nuclr.dev) plugin that provides a syntax-highlighted text editor screen (F4) for any readable file.

## Features

- **Syntax highlighting** for 25+ languages via [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea)
- **Code folding** and **anti-aliased rendering**
- **Line numbers** in the gutter
- **Theme-aware** — inherits colors from the active FlatLaf UI theme
- **Graceful fallback** — opens files in read-only mode when a read error occurs
- **Low priority** (10) — higher-priority screen providers take precedence for specialized formats

## Supported Languages

| Extension(s) | Language |
|---|---|
| `.java` | Java |
| `.js`, `.mjs` | JavaScript |
| `.ts`, `.tsx` | TypeScript |
| `.json` | JSON |
| `.xml` | XML |
| `.html`, `.htm` | HTML |
| `.css` | CSS |
| `.py` | Python |
| `.sql` | SQL |
| `.c`, `.h` | C |
| `.cpp`, `.hpp` | C++ |
| `.cs` | C# |
| `.go` | Go |
| `.rs` | Rust |
| `.php` | PHP |
| `.yaml`, `.yml` | YAML |
| `.md` | Markdown |
| `.properties` | Java Properties |
| `.ini` | INI |
| `.toml` | TOML |
| `.csv` | CSV |

All other readable files open with no syntax highlighting.

## Requirements

- Java 21+
- Maven 3.9+
- [Nuclr plugins-sdk](https://nuclr.dev) 1.0.0 installed in local Maven repository
- Signing keystore at `C:/nuclr/key/nuclr-signing.p12` (for `mvn verify`)

## Build

Install the plugins-sdk first if you haven't already:

```bash
cd plugins-sdk
mvn clean install
```

Then build this plugin:

```bash
cd plugins/core/screenpanel-text-editor
mvn clean verify -Djarsigner.storepass=<keystore-password>
```

This produces a signed plugin archive in `target/`:

```
target/
  screenpanel-text-editor-1.0.0.zip      # plugin archive
  screenpanel-text-editor-1.0.0.zip.sig  # RSA SHA256 signature
```

## Installation

Copy both files to the Nuclr Commander `plugins/` directory:

```bash
cp target/screenpanel-text-editor-1.0.0.zip     <commander>/plugins/
cp target/screenpanel-text-editor-1.0.0.zip.sig <commander>/plugins/
```

Or run `deploy.bat` on Windows to build and deploy in one step (targets `C:\nuclr\sources\commander\plugins\`).

## Plugin Manifest

```json
{
  "id": "dev.nuclr.plugin.core.screen.texteditor",
  "type": "Official",
  "screenProviders": ["dev.nuclr.plugin.core.screen.texteditor.TextEditorScreenProvider"]
}
```

## License

Apache-2.0 — see [nuclr.dev](https://nuclr.dev) for details.
