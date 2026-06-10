package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
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
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.platform.plugin.FullscreenNuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginCallback;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextEditorScreenPlugin implements FullscreenNuclrPlugin, NuclrEventListener {

	private static final String CLOSE_FULLSCREEN_ACTION = "plugin.fullscreen.close";
	private static final String TOGGLE_WRAP_ACTION = "plugin.text.editor.wrap";
	private static final String PREFERRED_EDITOR_FONT = "JetBrains Mono";

	private static final String PLUGIN_ID = "dev.nuclr.plugin.core.screen.texteditor";
	private static final String PLUGIN_NAME = "Text Editor";
	private static final String PLUGIN_VERSION = loadVersion();
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
			Map.entry("json", SyntaxConstants.SYNTAX_STYLE_JSON), Map.entry("xml", SyntaxConstants.SYNTAX_STYLE_XML),
			Map.entry("html", SyntaxConstants.SYNTAX_STYLE_HTML), Map.entry("htm", SyntaxConstants.SYNTAX_STYLE_HTML),
			Map.entry("css", SyntaxConstants.SYNTAX_STYLE_CSS), Map.entry("py", SyntaxConstants.SYNTAX_STYLE_PYTHON),
			Map.entry("sql", SyntaxConstants.SYNTAX_STYLE_SQL), Map.entry("c", SyntaxConstants.SYNTAX_STYLE_C),
			Map.entry("h", SyntaxConstants.SYNTAX_STYLE_C), Map.entry("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("cs", SyntaxConstants.SYNTAX_STYLE_CSHARP), Map.entry("go", SyntaxConstants.SYNTAX_STYLE_GO),
			Map.entry("rs", SyntaxConstants.SYNTAX_STYLE_RUST), Map.entry("php", SyntaxConstants.SYNTAX_STYLE_PHP),
			Map.entry("yaml", SyntaxConstants.SYNTAX_STYLE_YAML), Map.entry("yml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
			Map.entry("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
			Map.entry("ini", SyntaxConstants.SYNTAX_STYLE_INI), Map.entry("toml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("csv", SyntaxConstants.SYNTAX_STYLE_CSV), Map.entry("log", SyntaxConstants.SYNTAX_STYLE_NONE),
			Map.entry("txt", SyntaxConstants.SYNTAX_STYLE_NONE));

	private final String uuid = UUID.randomUUID().toString();
	private final JPanel panel = new JPanel(new BorderLayout());
	private final RSyntaxTextArea textArea = new RSyntaxTextArea();
	private final RTextScrollPane scroll = new RTextScrollPane(textArea);
	private NuclrPluginContext context;
	private NuclrResource currentResource;
	private boolean dirty;
	private boolean loading;

	public TextEditorScreenPlugin() {
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(4);
		textArea.setTabsEmulated(false);
		textArea.setLineWrap(false);
		textArea.setWrapStyleWord(true);
		try (InputStream themeIn = getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
			if (themeIn != null) {
				Theme.load(themeIn).apply(textArea);
			}
		} catch (IOException ignored) {
		}
		scroll.setLineNumbersEnabled(true);
		panel.add(scroll, BorderLayout.CENTER);
		attachDirtyTracking();
		registerWrapShortcut();
		registerFullscreenCloseShortcut();
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
	private static String loadVersion() {
		try (var stream = TextEditorScreenPlugin.class.getResourceAsStream("/plugin.properties")) {
			if (stream == null) return "unknown";
			var props = new java.util.Properties();
			props.load(stream);
			return props.getProperty("version", "unknown");
		} catch (java.io.IOException e) {
			return "unknown";
		}
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
	public JComponent panel() {
		return panel;
	}

	@Override
	public boolean supports(NuclrResource resource) {
		
		var path = resource != null ? resource.getPath() : null;

		if (path == null) {
			return false;
		}

		try {
			
			if (resource.isFolder() || false == resource.isReadable()) {
				return false;
			}
			
			var st = System.currentTimeMillis();
			var supported = TextFileDetector.isTextFile(resource);
			var et = System.currentTimeMillis();
			
			log.info("TextFileDetector result for {}: {} ({} ms)", path, supported, (et - st));
			
			if (false == supported) {
				
				// delete the temp file if it was created for detection
				Path tempFile = resource.getMetadata("tempPath", null);
				
				if (tempFile != null) {
					try {
						Files.deleteIfExists(tempFile);
					} catch (IOException ignored) {
					}
				}
				
				return false;
				
			}
			
			return true;
			
		} catch (Exception ex) {
			return false;
		}
		
	}

	@Override
	public void preinit(NuclrPluginContext context) {
		this.context = context;
		if (context.getEventBus() != null) {
			context.getEventBus().subscribe(this);
		}
		applyUiTheme();
	}

	@Override
	public void unload() {
		if (context != null && context.getEventBus() != null) {
			context.getEventBus().unsubscribe(this);
		}
	}

	@Override
	public boolean openResource(NuclrResource resource, AtomicBoolean cancelled) {
		
		if (cancelled != null && cancelled.get()) {
			return false;
		}
		if (!supports(resource)) {
			return false;
		}

		applyUiTheme();
		currentResource = resource;
		
		Path path = resource.getMetadata("tempPath", resource.getPath());
		
		String filename = path.getFileName() != null ? path.getFileName().toString() : path.toString();

		String content;
		
		boolean editable = isEditable();
		
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

		this.context.getEventBus().emit("main.window.title", Map.of("title", path.toString()), null);
		
		// Remove temp file
		if (resource.getMetadata("tempPath", null) != null) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
			}
		}

		return true;
	}

	public boolean isEditable() {
		return true;
	}

	@Override
	public void closeResource() {
		currentResource = null;
		dirty = false;
	}

	@Override
	public NuclrResource getCurrentResource() {
		return currentResource;
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
	public boolean isMessageSupported(String type) {
		return TOGGLE_WRAP_ACTION.equals(type);
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
		textArea.setFont(editorFont(base));

		Color background = themeColor(themeScheme, "Panel.background", textArea.getBackground());
		Color foreground = themeColor(themeScheme, "Panel.foreground", textArea.getForeground());
		Color accentSelection = themeColor(themeScheme, "Table.selectionBackground", textArea.getSelectionColor());
		Color selectionBackground = blend(background, accentSelection, 0.26f);
		Color selectionForeground = foreground;
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

	}

	private void registerFullscreenCloseShortcut() {
		var closeAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (context != null && context.getEventBus() != null) {
					context.getEventBus().emit(CLOSE_FULLSCREEN_ACTION);
				}
			}
		};
		var escape = KeyStroke.getKeyStroke("ESCAPE");
		var f3 = KeyStroke.getKeyStroke("F3");
		bindFullscreenClose(panel, escape, closeAction);
		bindFullscreenClose(scroll, escape, closeAction);
		bindFullscreenClose(textArea, escape, closeAction);
		bindFullscreenClose(panel, f3, closeAction);
		bindFullscreenClose(scroll, f3, closeAction);
		bindFullscreenClose(textArea, f3, closeAction);
	}

	private void registerWrapShortcut() {
		var wrapAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleWrap();
			}
		};
		var f2 = KeyStroke.getKeyStroke("F2");
		bindAction(panel, f2, TOGGLE_WRAP_ACTION, wrapAction);
		bindAction(scroll, f2, TOGGLE_WRAP_ACTION, wrapAction);
		bindAction(textArea, f2, TOGGLE_WRAP_ACTION, wrapAction);
	}

	private void toggleWrap() {
		textArea.setLineWrap(!textArea.getLineWrap());
		textArea.setWrapStyleWord(textArea.getLineWrap());
		textArea.revalidate();
		textArea.repaint();
	}

	private static void bindFullscreenClose(JComponent component, KeyStroke keyStroke, AbstractAction action) {
		bindAction(component, keyStroke, CLOSE_FULLSCREEN_ACTION, action);
	}

	private static void bindAction(JComponent component, KeyStroke keyStroke, String actionKey, AbstractAction action) {
		component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, actionKey);
		component.getActionMap().put(actionKey, action);
	}

	private static Color themeColor(NuclrThemeScheme themeScheme, String key, Color fallback) {
		if (themeScheme != null) {
			return themeScheme.color(key, fallback);
		}
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	private static Color blend(Color base, Color overlay, float overlayWeight) {
		float clamped = Math.max(0f, Math.min(1f, overlayWeight));
		float baseWeight = 1f - clamped;
		return new Color(Math.round(base.getRed() * baseWeight + overlay.getRed() * clamped),
				Math.round(base.getGreen() * baseWeight + overlay.getGreen() * clamped),
				Math.round(base.getBlue() * baseWeight + overlay.getBlue() * clamped));
	}

	private static Font editorFont(Font baseFont) {
		Font fallback = baseFont != null ? baseFont : new Font(Font.MONOSPACED, Font.PLAIN, 13);
		int size = Math.max(11, fallback.getSize());
		String family = hasFontFamily(PREFERRED_EDITOR_FONT) ? PREFERRED_EDITOR_FONT : Font.MONOSPACED;
		return new Font(family, Font.PLAIN, size);
	}

	private static boolean hasFontFamily(String family) {
		for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
			if (family.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private static String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1);
	}

	@Override
	public Developer developer() {
		return Developer.Official;
	}

	@Override
	public NuclrPluginContext getContext() {
		return this.context;
	}

	@Override
	public void init() {

	}

	@Override
	public void handleMessage(Object source, String type, Map<String, Object> eventData, NuclrPluginCallback callback) {

		if (!TOGGLE_WRAP_ACTION.equals(type) || !isFocused()) {
			return;
		}
		toggleWrap();

	}

	@Override
	public Role role() {
		return Role.Editor;
	}

}
