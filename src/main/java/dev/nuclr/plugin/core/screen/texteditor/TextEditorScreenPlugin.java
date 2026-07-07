package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
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
import dev.nuclr.platform.plugin.BaseNuclrPlugin;
import dev.nuclr.platform.plugin.FullscreenNuclrPlugin;
import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPluginCallback;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextEditorScreenPlugin implements FullscreenNuclrPlugin, NuclrEventListener {

	private static final String CLOSE_FULLSCREEN_ACTION = "plugin.fullscreen.close";
	private static final String REQUEST_CLOSE_ACTION = "plugin.text.editor.close";
	private static final String TOGGLE_WRAP_ACTION = "plugin.text.editor.wrap";
	private static final String SAVE_ACTION = "plugin.text.editor.save";

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
	private String titlePath;
	private FileStamp lastKnownFileStamp;
	private boolean dirty;
	private boolean loading;
	private boolean discardOnClose;
	private Popup saveToast;
	private Timer saveToastTimer;

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
		registerPrimaryShortcut();
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
	public List<NuclrMenuResource> menuItems(NuclrResource resource) {
		var f2 = new NuclrMenuResource("Save", "F2", SAVE_ACTION);
		var f3 = new NuclrMenuResource("Quit", "F3", REQUEST_CLOSE_ACTION);

		return List.of(f2, f3);
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
		discardOnClose = false;
		
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
		textArea.setLineWrap(wrapByDefault());
		textArea.setWrapStyleWord(wrapByDefault());
		textArea.setCaretPosition(0);
		dirty = false;
		lastKnownFileStamp = readFileStampQuietly(resource.getPath());

		titlePath = path.toString();
		updateTitle();
		
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
		if (dirty && textArea.isEditable() && !discardOnClose) {
			SaveResult result = saveWithUserFeedback(false);
			if (!isSaveSuccess(result)) {
				log.warn("Closing text editor without saving {}", titlePath);
			}
		}
		hideSaveToast();
		currentResource = null;
		dirty = false;
		discardOnClose = false;
		lastKnownFileStamp = null;
		titlePath = null;
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
		return (isEditable() && SAVE_ACTION.equals(type))
				|| REQUEST_CLOSE_ACTION.equals(type)
				|| TOGGLE_WRAP_ACTION.equals(type);
	}

	@Override
	public String uuid() {
		return uuid;
	}

	public boolean isDirty() {
		return dirty;
	}

	public boolean save() throws Exception {
		return isSaveSuccess(saveInternal(true));
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
		if (loading || !textArea.isEditable() || dirty) {
			return;
		}
		dirty = true;
		updateTitle();
	}

	private SaveResult saveWithUserFeedback() {
		return saveWithUserFeedback(true);
	}

	private SaveResult saveWithUserFeedback(boolean showSuccessNotification) {
		try {
			SaveResult result = saveInternal(true);
			if (result == SaveResult.UNAVAILABLE) {
				JOptionPane.showMessageDialog(panel, "Could not save the file.", "Save failed",
						JOptionPane.ERROR_MESSAGE);
			} else if (showSuccessNotification && isSaveSuccess(result)) {
				showSaveToast(result == SaveResult.UNCHANGED ? "Already saved" : "Saved");
			}
			return result;
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(panel, "Error saving file: " + ex.getMessage(), "Save failed",
					JOptionPane.ERROR_MESSAGE);
			return SaveResult.FAILED;
		}
	}

	private SaveResult saveInternal(boolean askBeforeOverwrite) throws Exception {
		if (currentResource == null || currentResource.getPath() == null || !textArea.isEditable()) {
			return SaveResult.UNAVAILABLE;
		}

		Path path = currentResource.getPath();
		FileStamp currentStamp = readFileStamp(path);

		if (!dirty && !changedExternally(currentStamp)) {
			return SaveResult.UNCHANGED;
		}

		if (askBeforeOverwrite && changedExternally(currentStamp) && !confirmOverwriteExternalChanges()) {
			return SaveResult.CANCELLED;
		}

		Files.writeString(path, textArea.getText(), StandardCharsets.UTF_8);
		lastKnownFileStamp = readFileStamp(path);
		dirty = false;
		updateTitle();
		return SaveResult.SAVED;
	}

	private static boolean isSaveSuccess(SaveResult result) {
		return result == SaveResult.SAVED || result == SaveResult.UNCHANGED;
	}

	private boolean changedExternally(FileStamp currentStamp) {
		return lastKnownFileStamp != null && !lastKnownFileStamp.equals(currentStamp);
	}

	private boolean confirmOverwriteExternalChanges() {
		int choice = JOptionPane.showConfirmDialog(panel,
				"The file has changed on disk since it was opened or last saved.\nOverwrite those changes?",
				"File modified elsewhere", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}

	private static FileStamp readFileStamp(Path path) throws IOException {
		if (path == null || !Files.exists(path)) {
			return null;
		}
		BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
		return new FileStamp(attrs.lastModifiedTime(), attrs.size());
	}

	private static FileStamp readFileStampQuietly(Path path) {
		try {
			return readFileStamp(path);
		} catch (IOException ignored) {
			return null;
		}
	}

	private void showSaveToast(String message) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> showSaveToast(message));
			return;
		}
		if (!panel.isShowing()) {
			return;
		}

		hideSaveToast();

		NuclrThemeScheme themeScheme = context != null ? context.getTheme() : null;
		Color accent = themeColor(themeScheme, "Table.selectionBackground", new Color(0x4C8BFF));
		Color background = blend(textArea.getBackground(), accent, 0.30f);
		Color foreground = themeColor(themeScheme, "Panel.foreground", textArea.getForeground());
		Font labelFont = editorFont(themeScheme != null ? themeScheme.defaultFont() : UIManager.getFont("Label.font"))
				.deriveFont(Font.BOLD);

		var toast = new SaveToast(message, background, foreground, accent, labelFont);
		Dimension size = toast.getPreferredSize();
		Point screen = panel.getLocationOnScreen();
		int x = screen.x + Math.max(12, panel.getWidth() - size.width - 24);
		int y = screen.y + Math.max(12, panel.getHeight() - size.height - 24);

		saveToast = PopupFactory.getSharedInstance().getPopup(panel, toast, x, y);
		saveToast.show();

		saveToastTimer = new Timer(1400, e -> hideSaveToast());
		saveToastTimer.setRepeats(false);
		saveToastTimer.start();
	}

	private void hideSaveToast() {
		if (saveToastTimer != null) {
			saveToastTimer.stop();
			saveToastTimer = null;
		}
		if (saveToast != null) {
			saveToast.hide();
			saveToast = null;
		}
	}

	/** Emits the window title, prefixing a {@code *} marker while there are unsaved changes. */
	private void updateTitle() {
		if (context == null || context.getEventBus() == null || titlePath == null) {
			return;
		}
		String title = (dirty ? "* " : "") + titlePath;
		context.getEventBus().emit("main.window.title", Map.of("title", title), null);
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
				requestClose();
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

	/**
	 * Handles a close request (F3/Escape). When there are unsaved edits the user is
	 * offered Save / Don't Save / Cancel; with no changes the editor closes silently.
	 */
	private void requestClose() {
		if (dirty && textArea.isEditable()) {
			int choice = showUnsavedChangesDialog();
			if (choice == JOptionPane.YES_OPTION) {
				if (!isSaveSuccess(saveWithUserFeedback(false))) {
					return;
				}
			} else if (choice != JOptionPane.NO_OPTION) {
				// Cancel or dialog dismissed: stay in the editor.
				return;
			} else {
				discardOnClose = true;
			}
		}
		emitClose();
	}

	private int showUnsavedChangesDialog() {
		var result = new int[] { JOptionPane.CANCEL_OPTION };
		var save = new JButton("Save");
		var dontSave = new JButton("Don't Save");
		var cancel = new JButton("Cancel");
		var buttons = new JButton[] { save, dontSave, cancel };

		var pane = new JOptionPane("Save changes before closing?", JOptionPane.WARNING_MESSAGE,
				JOptionPane.YES_NO_CANCEL_OPTION, null, buttons, save);

		save.addActionListener(e -> {
			result[0] = JOptionPane.YES_OPTION;
			pane.setValue(save);
		});
		dontSave.addActionListener(e -> {
			result[0] = JOptionPane.NO_OPTION;
			pane.setValue(dontSave);
		});
		cancel.addActionListener(e -> {
			result[0] = JOptionPane.CANCEL_OPTION;
			pane.setValue(cancel);
		});

		JDialog dialog = pane.createDialog(panel, "Unsaved changes");
		installDialogButtonTraversal(dialog, buttons);
		SwingUtilities.invokeLater(() -> focusDialogButton(save));
		dialog.setVisible(true);
		dialog.dispose();

		return result[0];
	}

	private static void installDialogButtonTraversal(JDialog dialog, JButton[] buttons) {
		for (int i = 0; i < buttons.length; i++) {
			JButton button = buttons[i];
			int previous = (i + buttons.length - 1) % buttons.length;
			int next = (i + 1) % buttons.length;

			bindFocusMove(button, "LEFT", buttons[previous]);
			bindFocusMove(button, "UP", buttons[previous]);
			bindFocusMove(button, "RIGHT", buttons[next]);
			bindFocusMove(button, "DOWN", buttons[next]);
			button.addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent e) {
					button.getRootPane().setDefaultButton(button);
				}
			});
		}

		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
				"cancel");
		dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				buttons[buttons.length - 1].doClick();
			}
		});
	}

	private static void bindFocusMove(JButton source, String keyStroke, JButton target) {
		String actionKey = "focus." + keyStroke.toLowerCase();
		source.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), actionKey);
		source.getActionMap().put(actionKey, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				focusDialogButton(target);
			}
		});
	}

	private static void focusDialogButton(JButton button) {
		button.requestFocusInWindow();
		if (button.getRootPane() != null) {
			button.getRootPane().setDefaultButton(button);
		}
	}

	private void emitClose() {
		if (context != null && context.getEventBus() != null) {
			context.getEventBus().emit(CLOSE_FULLSCREEN_ACTION);
		}
	}

	private void registerPrimaryShortcut() {
		var primaryAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onPrimaryKey();
			}
		};
		var f2 = KeyStroke.getKeyStroke("F2");
		var ctrlS = KeyStroke.getKeyStroke("ctrl S");
		bindAction(panel, f2, SAVE_ACTION, primaryAction);
		bindAction(scroll, f2, SAVE_ACTION, primaryAction);
		bindAction(textArea, f2, SAVE_ACTION, primaryAction);
		if (isEditable()) {
			bindAction(panel, ctrlS, SAVE_ACTION, primaryAction);
			bindAction(scroll, ctrlS, SAVE_ACTION, primaryAction);
			bindAction(textArea, ctrlS, SAVE_ACTION, primaryAction);
		}
	}

	/**
	 * Behaviour of the F2 key. In the editor this saves the file; the read-only
	 * viewer overrides it to toggle word wrap instead.
	 */
	protected void onPrimaryKey() {
		saveWithUserFeedback();
	}

	/**
	 * Default word-wrap state for a freshly opened file. The editor always wraps
	 * (there is no wrap-off mode); the viewer starts unwrapped and toggles via F2.
	 */
	protected boolean wrapByDefault() {
		return true;
	}

	protected void toggleWrap() {
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
		// Use Commander's configured UI font (JetBrains Mono at the user's font size,
		// stored in UIManager "defaultFont"). Falling back to monospaced only when the
		// look-and-feel has no default font.
		return baseFont != null ? baseFont : new Font(Font.MONOSPACED, Font.PLAIN, 13);
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

		if (!isFocused() && currentResource == null) {
			return;
		}

		if (SAVE_ACTION.equals(type) && isEditable()) {
			saveWithUserFeedback();
			return;
		}

		if (REQUEST_CLOSE_ACTION.equals(type)) {
			requestClose();
			return;
		}

		if (TOGGLE_WRAP_ACTION.equals(type)) {
			toggleWrap();
		}

	}

	@Override
	public void act(BaseNuclrPlugin other, String actionType, List<NuclrResource> selectedResources,
			NuclrResource focusedResource, Map<String, Object> data, NuclrPluginCallback callback) {

		if (!isFocused() && currentResource == null) {
			return;
		}

		if (SAVE_ACTION.equals(actionType) && isEditable()) {
			saveWithUserFeedback();
			return;
		}

		if (REQUEST_CLOSE_ACTION.equals(actionType)) {
			requestClose();
			return;
		}

		if (TOGGLE_WRAP_ACTION.equals(actionType)) {
			toggleWrap();
		}

	}

	@Override
	public Role role() {
		return Role.Editor;
	}

	private enum SaveResult {
		SAVED,
		UNCHANGED,
		CANCELLED,
		FAILED,
		UNAVAILABLE
	}

	private record FileStamp(FileTime lastModifiedTime, long size) {
	}

	private static final class SaveToast extends JPanel {
		private static final int ARC = 16;
		private final Color background;
		private final Color foreground;
		private final Color accent;

		SaveToast(String message, Color background, Color foreground, Color accent, Font font) {
			this.background = background;
			this.foreground = foreground;
			this.accent = accent;
			setOpaque(false);
			setLayout(new BorderLayout(10, 0));
			setBorder(new EmptyBorder(10, 14, 10, 16));

			var label = new JLabel(message);
			label.setForeground(foreground);
			label.setFont(font);

			add(new SaveMark(accent, foreground), BorderLayout.WEST);
			add(label, BorderLayout.CENTER);
		}

		@Override
		public Dimension getPreferredSize() {
			Dimension size = super.getPreferredSize();
			return new Dimension(Math.max(118, size.width), Math.max(44, size.height));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(background);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
			g2.setColor(accent);
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
			g2.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 36));
			g2.drawLine(14, getHeight() - 1, getWidth() - 15, getHeight() - 1);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	private static final class SaveMark extends JComponent {
		private final Color accent;
		private final Color foreground;

		SaveMark(Color accent, Color foreground) {
			this.accent = accent;
			this.foreground = foreground;
			setPreferredSize(new Dimension(22, 22));
			setMinimumSize(new Dimension(22, 22));
			setMaximumSize(new Dimension(22, 22));
			setBorder(BorderFactory.createEmptyBorder());
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int size = Math.min(getWidth(), getHeight()) - 2;
			int x = (getWidth() - size) / 2;
			int y = (getHeight() - size) / 2;

			g2.setColor(accent);
			g2.fillOval(x, y, size, size);
			g2.setColor(foreground);
			g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawLine(x + 6, y + 11, x + 9, y + 14);
			g2.drawLine(x + 9, y + 14, x + 15, y + 7);
			g2.dispose();
		}
	}

}
