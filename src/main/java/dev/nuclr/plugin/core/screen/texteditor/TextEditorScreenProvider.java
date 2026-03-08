package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import dev.nuclr.plugin.PluginTheme;
import dev.nuclr.plugin.ScreenProvider;

public class TextEditorScreenProvider implements ScreenProvider {

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
			Map.entry("csv", SyntaxConstants.SYNTAX_STYLE_CSV));

	private final JPanel panel = new JPanel(new BorderLayout());
	private final RSyntaxTextArea textArea = new RSyntaxTextArea();
	private final RTextScrollPane scroll = new RTextScrollPane(textArea);
	private Path currentPath;
	private boolean dirty;
	private boolean loading;
	private PluginTheme pluginTheme;

	public TextEditorScreenProvider() {
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(4);
		textArea.setTabsEmulated(false);
		scroll.setLineNumbersEnabled(true);
		panel.add(scroll, BorderLayout.CENTER);
		applyUiTheme();
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

	@Override
	public String getPluginClass() {
		return getClass().getName();
	}

	@Override
	public boolean matches(Path path) {
		try {
			return path != null && Files.isRegularFile(path) && Files.isReadable(path);
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public JComponent open(Path path) throws Exception {
		applyUiTheme();
		currentPath = path;

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
		return panel;
	}

	@Override
	public void applyTheme(PluginTheme theme) {
		this.pluginTheme = theme;
		applyUiTheme();
	}

	@Override
	public void close() {
		// Keep component instance for reuse.
	}

	@Override
	public void focus() {
		textArea.requestFocusInWindow();
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public boolean save() throws Exception {
		if (currentPath == null || !textArea.isEditable()) {
			return false;
		}
		Files.writeString(currentPath, textArea.getText(), StandardCharsets.UTF_8);
		dirty = false;
		return true;
	}

	@Override
	public int priority() {
		return 10;
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
		textArea.setSyntaxEditingStyle(style);
		textArea.discardAllEdits();
		loading = false;
	}

	private void markDirty() {
		if (!loading && textArea.isEditable()) {
			dirty = true;
		}
	}

	private void applyUiTheme() {
		Font base = pluginTheme != null
				? pluginTheme.defaultFont()
				: UIManager.getFont("defaultFont");
		if (base == null) {
			base = new Font("JetBrains Mono", Font.PLAIN, 12);
		}
		textArea.setFont(base.deriveFont(Font.PLAIN, base.getSize2D()));
		Color background = themeColor("Panel.background", textArea.getBackground());
		Color foreground = themeColor("Panel.foreground", textArea.getForeground());
		Color selectionBackground = themeColor("Table.selectionBackground", textArea.getSelectionColor());
		Color selectionForeground = themeColor("Table.selectionForeground", textArea.getSelectedTextColor());
		Color gutterBackground = themeColor("TableHeader.background", background);
		Color gutterForeground = themeColor("Label.foreground", foreground);

		textArea.setBackground(background);
		textArea.setForeground(foreground);
		textArea.setCaretColor(foreground);
		textArea.setSelectionColor(selectionBackground);
		textArea.setSelectedTextColor(selectionForeground);
		textArea.setCurrentLineHighlightColor(themeColor("Table.gridColor", gutterBackground));

		var gutter = scroll.getGutter();
		if (gutter != null) {
			gutter.setBackground(gutterBackground);
			gutter.setLineNumberColor(gutterForeground);
			gutter.setLineNumberFont(textArea.getFont());
		}
		scroll.getViewport().setBackground(background);
		scroll.setBackground(background);
		panel.setBackground(background);
	}

	private Color themeColor(String key, Color fallback) {
		if (pluginTheme != null) {
			return pluginTheme.color(key, fallback);
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
