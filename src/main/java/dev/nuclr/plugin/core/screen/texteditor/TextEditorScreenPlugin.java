package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;

public class TextEditorScreenPlugin implements NuclrPlugin {

	private static final String PLUGIN_ID = "dev.nuclr.plugin.core.screen.texteditor";
	private static final String PLUGIN_NAME = "Screen Text Editor";
	private static final String PLUGIN_VERSION = "1.0.0";
	private static final String PLUGIN_DESCRIPTION = "Text editor screen provider (F4) for readable files.";
	private static final String PLUGIN_AUTHOR = "Nuclr Development Team";
	private static final String PLUGIN_LICENSE = "Apache-2.0";
	private static final String PLUGIN_WEBSITE = "https://nuclr.dev";
	private static final String PLUGIN_PAGE_URL = "https://nuclr.dev/plugins/core/screenpanel-text-editor.html";
	private static final String PLUGIN_DOC_URL = PLUGIN_PAGE_URL;

	private static final Map<String, String> EXTENSION_TO_SYNTAX = Map.ofEntries(
			Map.entry("java", SyntaxConstants.SYNTAX_STYLE_JAVA),
			Map.entry("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("mjs", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("tsx", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("json", SyntaxConstants.SYNTAX_STYLE_JSON),
			Map.entry("xml", SyntaxConstants.SYNTAX_STYLE_XML),
			Map.entry("html", SyntaxConstants.SYNTAX_STYLE_HTML),
			Map.entry("htm", SyntaxConstants.SYNTAX_STYLE_HTML),
			Map.entry("css", SyntaxConstants.SYNTAX_STYLE_CSS),
			Map.entry("py", SyntaxConstants.SYNTAX_STYLE_PYTHON),
			Map.entry("sql", SyntaxConstants.SYNTAX_STYLE_SQL),
			Map.entry("c", SyntaxConstants.SYNTAX_STYLE_C),
			Map.entry("h", SyntaxConstants.SYNTAX_STYLE_C),
			Map.entry("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("cs", SyntaxConstants.SYNTAX_STYLE_CSHARP),
			Map.entry("go", SyntaxConstants.SYNTAX_STYLE_GO),
			Map.entry("rs", SyntaxConstants.SYNTAX_STYLE_RUST),
			Map.entry("php", SyntaxConstants.SYNTAX_STYLE_PHP),
			Map.entry("yaml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("yml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
			Map.entry("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
			Map.entry("ini", SyntaxConstants.SYNTAX_STYLE_INI),
			Map.entry("toml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("csv", SyntaxConstants.SYNTAX_STYLE_CSV),
			Map.entry("log", SyntaxConstants.SYNTAX_STYLE_NONE),
			Map.entry("txt", SyntaxConstants.SYNTAX_STYLE_NONE));

	private final String uuid = UUID.randomUUID().toString();
	private final JPanel panel = new JPanel(new BorderLayout());
	private final RSyntaxTextArea textArea = new RSyntaxTextArea();
	private final RTextScrollPane scroll = new RTextScrollPane(textArea);
	private NuclrPluginContext context;
	private NuclrResourcePath currentResource;
	private boolean dirty;
	private boolean loading;

	public TextEditorScreenPlugin() {
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(4);
		textArea.setTabsEmulated(false);
		try (InputStream themeIn = getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
			if (themeIn != null) {
				Theme.load(themeIn).apply(textArea);
			}
		} catch (IOException ignored) {
		}
		scroll.setLineNumbersEnabled(true);
		panel.add(scroll, BorderLayout.CENTER);
		attachDirtyTracking();
		applyUiTheme();
	}

	@Override
	public boolean onFocusGained() {
		textArea.requestFocusInWindow();
		return true;
	}

	@Override
	public void onFocusLost() {
	}

	@Override
	public boolean isFocused() {
		return textArea.isFocusOwner() || scroll.isFocusOwner() || panel.isFocusOwner();
	}

	@Override
	public String id() {
		return PLUGIN_ID;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public String version() {
		return PLUGIN_VERSION;
	}

	@Override
	public String description() {
		return PLUGIN_DESCRIPTION;
	}

	@Override
	public String author() {
		return PLUGIN_AUTHOR;
	}

	@Override
	public String license() {
		return PLUGIN_LICENSE;
	}

	@Override
	public String website() {
		return PLUGIN_WEBSITE;
	}

	@Override
	public String pageUrl() {
		return PLUGIN_PAGE_URL;
	}

	@Override
	public String docUrl() {
		return PLUGIN_DOC_URL;
	}

	@Override
	public Developer type() {
		return Developer.Official;
	}

	@Override
	public JComponent panel() {
		return panel;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		if (resource == null || resource.getPath() == null) {
			return false;
		}
		Path path = resource.getPath();
		try {
			if (false == Files.isRegularFile(path) || false == Files.isReadable(path)) {
				return false;
			}
			return TextFileDetector.isTextFile(path);
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public NuclrPluginRole role() {
		return NuclrPluginRole.FullScreenEditor;
	}

	@Override
	public void load(NuclrPluginContext context, boolean template) {
		this.context = context;
		applyUiTheme();
	}

	@Override
	public void unload() {
	}

	@Override
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
		if (cancelled != null && cancelled.get()) {
			return false;
		}
		if (!supports(resource)) {
			return false;
		}

		applyUiTheme();
		currentResource = resource;
		Path path = resource.getPath();
		String filename = path.getFileName() != null ? path.getFileName().toString() : path.toString();

		String content;
		boolean editable = true;
		try {
			content = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			content = "Error reading file: " + ex.getMessage();
			editable = false;
		}

		setText(filename, content);
		textArea.setEditable(editable);
		textArea.setCaretPosition(0);
		dirty = false;
		return true;
	}

	@Override
	public void closeResource() {
		currentResource = null;
		dirty = false;
	}

	@Override
	public NuclrResourcePath getCurrentResource() {
		return currentResource;
	}

	@Override
	public int priority() {
		return 10;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
		applyUiTheme(themeScheme);
	}

	@Override
	public boolean singleton() {
		return false;
	}

	@Override
	public String uuid() {
		return uuid;
	}

	public boolean isDirty() {
		return dirty;
	}

	public boolean save() throws Exception {
		if (currentResource == null || currentResource.getPath() == null || !textArea.isEditable()) {
			return false;
		}
		Files.writeString(currentResource.getPath(), textArea.getText(), StandardCharsets.UTF_8);
		dirty = false;
		return true;
	}

	private void setText(String filename, String text) {
		String ext = extension(filename).toLowerCase();
		String style = EXTENSION_TO_SYNTAX.getOrDefault(ext, SyntaxConstants.SYNTAX_STYLE_NONE);

		RSyntaxDocument newDoc = new RSyntaxDocument(style);
		try {
			newDoc.insertString(0, text, null);
		} catch (BadLocationException e) {
			return;
		}
		loading = true;
		textArea.setDocument(newDoc);
		attachDirtyTracking();
		textArea.setSyntaxEditingStyle(style);
		textArea.discardAllEdits();
		loading = false;
	}

	private void attachDirtyTracking() {
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				markDirty();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				markDirty();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				markDirty();
			}
		});
	}

	private void markDirty() {
		if (!loading && textArea.isEditable()) {
			dirty = true;
		}
	}

	private void applyUiTheme() {
		applyUiTheme(context != null ? context.getTheme() : null);
	}

	private void applyUiTheme(NuclrThemeScheme themeScheme) {
		Font base = themeScheme != null ? themeScheme.defaultFont() : UIManager.getFont("defaultFont");
		if (base == null) {
			base = new Font("JetBrains Mono", Font.PLAIN, 12);
		}
		textArea.setFont(base.deriveFont(Font.PLAIN, base.getSize2D()));

		Color background = themeColor(themeScheme, "Panel.background", textArea.getBackground());
		Color foreground = themeColor(themeScheme, "Panel.foreground", textArea.getForeground());
		Color selectionBackground = themeColor(themeScheme, "Table.selectionBackground", textArea.getSelectionColor());
		Color selectionForeground = themeColor(themeScheme, "Table.selectionForeground", textArea.getSelectedTextColor());
		Color gutterBackground = themeColor(themeScheme, "TableHeader.background", background);
		Color gutterForeground = themeColor(themeScheme, "Label.foreground", foreground);

		textArea.setBackground(background);
		textArea.setForeground(foreground);
		textArea.setCaretColor(foreground);
		textArea.setSelectionColor(selectionBackground);
		textArea.setSelectedTextColor(selectionForeground);
		textArea.setCurrentLineHighlightColor(themeColor(themeScheme, "Table.gridColor", gutterBackground));

		var gutter = scroll.getGutter();
		if (gutter != null) {
			gutter.setBackground(gutterBackground);
			gutter.setLineNumberColor(gutterForeground);
			gutter.setLineNumberFont(textArea.getFont());
		}
		scroll.getViewport().setBackground(background);
		scroll.setBackground(background);
		panel.setBackground(background);
		
		// If ESC is pressed, emit "plugin.fullscreen.close"
		textArea.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "plugin.fullscreen.close");
		
		
	}

	private static Color themeColor(NuclrThemeScheme themeScheme, String key, Color fallback) {
		if (themeScheme != null) {
			return themeScheme.color(key, fallback);
		}
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	private static String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1);
	}
}
