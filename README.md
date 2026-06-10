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
| 💾 Dirty tracking | Unsaved changes are tracked; the title updates accordingly |
| ↩️ Word wrap | Toggleable with `F2` |
| 🛡️ Safe fallback | Non-UTF-8 files open in read-only mode |

## ⌨️ Keyboard Shortcuts

| Key | Action |
|---|---|
| `F2` | Toggle word wrap |
| `F3` / `Escape` | Close editor / viewer |

## 🎯 Supported Formats

| Category | Extensions |
|---|---|
| JVM | `java`, `kt`, `scala`, `groovy` |
| Web | `js`, `mjs`, `ts`, `tsx`, `html`, `htm`, `css` |
| Data / config | `json`, `xml`, `yaml`, `yml`, `properties`, `ini`, `toml`, `csv` |
| Systems | `c`, `h`, `cpp`, `hpp`, `cs`, `go`, `rs`, `php` |
| Scripts | `py`, `sql` |
| Plain text | `txt`, `log`, `md` |

> 🔍 The plugin uses `TextFileDetector` to check whether a file is text before offering to open it — binary files are automatically skipped.

## 📥 Installation

Copy the signed plugin archive and detached signature into the Nuclr Commander `plugins/` directory:

```text
screenpanel-text-editor-<version>.zip
screenpanel-text-editor-<version>.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. The plugin becomes available immediately without a restart.

## ⚙️ How it works

`TextEditorScreenPlugin` implements `FullscreenNuclrPlugin` with `Role.Editor`. It creates an `RSyntaxTextArea` inside an `RTextScrollPane`, applies the Bined dark theme XML, and binds the theme updater to `NuclrThemeScheme`. `TextViewerScreenPlugin` extends it and overrides `isEditable()` to return `false`. `TextFileDetector` performs a binary scan before the plugin reports support for a resource.

## 🗂️ Source Layout

```text
src/main/java/dev/nuclr/plugin/core/screen/texteditor/
├── TextEditorScreenPlugin.java   fullscreen text editor (Editor role)
├── TextViewerScreenPlugin.java   read-only viewer variant (Viewer role)
└── TextFileDetector.java         text vs binary file detection
```

## 📚 Dependencies

| Library | Version | Purpose |
|---|---|---|
| `dev.nuclr:platform-sdk` | `3.0.1` | Nuclr platform interfaces |
| `rsyntaxtextarea` | `3.6.1` | Syntax-highlighted text editor component |

## 📜 License

Apache License 2.0 — see [LICENSE](LICENSE).
