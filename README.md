# 📝 Text Editor

An official [Nuclr Commander](https://nuclr.dev) plugin providing a fullscreen syntax-highlighted text editor. Activated with **F4** from any readable file. Powered by [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea).

This plugin ships **two roles**:

| Role | Shortcut | Description |
|---|---|---|
| 📝 **Text Editor** | `F4` | Editable mode with save support |
| 👁️ **Text Viewer** | `F3` | Read-only mode — safe inspection |

## ✨ What It Does

| Feature | Details |
|---|---|
| 🎨 Syntax highlighting | Common source and config formats via RSyntaxTextArea |
| 🔢 Line numbers | Gutter with line numbers |
| 🪄 Code folding | Foldable regions where the language supports it |
| 🔤 Preferred font | JetBrains Mono when available; falls back to system monospace |
| 🌑 Dark theme | Colors follow the Nuclr Commander theme via `NuclrThemeScheme` |
| 💾 Dirty tracking | Unsaved changes are tracked; the title updates accordingly, and closing prompts **Save / Don't Save / Cancel** |
| 🔍 Search | Modeless find dialog (`F7` / `Ctrl+F`) with find next/previous, wrap-around, and a match position status line |
| ⚙️ Search options | Case sensitive · regular expressions · whole words · fuzzy search |
| ↩️ Word wrap | On by default, wrapping at word boundaries |
| 🛡️ Safe fallback | Non-UTF-8 files open in read-only mode |

## ⌨️ Keyboard Shortcuts

| Key | Action |
|---|---|
| `F2` / `Ctrl+S` | Save (editor only) |
| `F3` / `Escape` | Close editor / viewer |
| `F7` / `Ctrl+F` | Open the search dialog |

## 🎯 Supported Formats

**Any** file that `TextFileDetector` recognises as text can be opened — detection is content-based (an 8 KB sample), not extension-based, so binary files are skipped automatically and extensionless text files open fine.

The extension only picks the **syntax highlighting**:

| Category | Extensions |
|---|---|
| JVM | `java` |
| Web | `js`, `mjs`, `ts`, `tsx`, `html`, `htm`, `css`, `php` |
| Data / config | `json`, `xml`, `yaml`, `yml`, `toml`, `properties`, `ini`, `csv` |
| Systems | `c`, `h`, `cpp`, `hpp`, `cs`, `go`, `rs` |
| Scripts | `py`, `sql` |
| Markup / plain text | `md`, `txt`, `log` |

Anything else opens with highlighting off.

## 📥 Installation

Copy the signed plugin archive and detached signature into the Nuclr Commander `plugins/` directory:

```text
screenpanel-text-editor-<version>.zip
screenpanel-text-editor-<version>.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. The plugin becomes available immediately without a restart.

## ⚙️ How it works

`TextEditorScreenPlugin` implements `FullscreenNuclrPlugin` in the editor role. It creates an `RSyntaxTextArea` inside an `RTextScrollPane`, applies the theme XML, and binds the theme updater to `NuclrThemeScheme`. `TextViewerScreenPlugin` extends it and overrides `isEditable()` to return `false`, so both roles share all editing, search, and theming code. `TextFileDetector` performs a binary scan before the plugin reports support for a resource.

Key bindings are installed on the panel, the scroll pane *and* the text area, so a shortcut fires regardless of which of the three currently holds focus.

## 🗂️ Source Layout

```text
src/main/java/dev/nuclr/plugin/core/screen/texteditor/
├── TextEditorScreenPlugin.java   fullscreen text editor (editor role), search dialog
├── TextViewerScreenPlugin.java   read-only viewer variant (viewer role)
└── TextFileDetector.java         text vs binary file detection
```

## 📚 Dependencies

| Library | Version | Purpose |
|---|---|---|
| `dev.nuclr:platform-sdk` | `3.0.2` | Nuclr platform interfaces |
| `rsyntaxtextarea` | `3.6.1` | Syntax-highlighted text editor component |
| `commons-io` | `2.22.0` | File reading / encoding helpers |
| `commons-lang3` | `3.20.0` | String utilities |

## 📜 License

Apache License 2.0 — see [LICENSE](LICENSE).
