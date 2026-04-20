# Nuclr Screen Panel - Text Editor

An official [Nuclr Commander](https://nuclr.dev) plugin that provides a
syntax-highlighted text editor screen for readable files.

## Features

- Syntax highlighting for common source and config formats via
  [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea)
- Code folding and anti-aliased rendering
- Line numbers in the gutter
- Theme-aware editor colors and font sizing through `NuclrThemeScheme`
- Graceful fallback to read-only mode when the file cannot be read as UTF-8
- Exposes the SDK `FullScreenEditor` role

## Requirements

- Java 21+
- Maven 3.9+
- `platform-sdk` 2.0.4 installed in the local Maven repository
- Signing keystore at `C:/nuclr/key/nuclr-signing.p12` for `mvn verify`

## Build

```bash
cd plugins/core/screenpanel-text-editor
mvn clean verify -Djarsigner.storepass=<keystore-password>
```

This produces:

```text
target/
  screenpanel-text-editor-1.0.0.zip
  screenpanel-text-editor-1.0.0.zip.sig
```

## SDK Notes

This plugin now follows the `NuclrPlugin` contract from `platform-sdk 2.0.4`
and is implemented as a concrete `NuclrPlugin` with role
`NuclrPluginRole.FullScreenEditor`.

Commander currently discovers that role in the SDK, but fullscreen UI routing is
still an application concern rather than a plugin concern.

## Installation

Copy both files to Commander `plugins/`:

```bash
cp target/screenpanel-text-editor-1.0.0.zip     <commander>/plugins/
cp target/screenpanel-text-editor-1.0.0.zip.sig <commander>/plugins/
```

## License

Apache-2.0 - see [nuclr.dev](https://nuclr.dev) for details.
