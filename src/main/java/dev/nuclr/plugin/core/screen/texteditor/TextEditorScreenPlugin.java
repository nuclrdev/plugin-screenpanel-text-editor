package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.DocumentRange;
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
	private static final String FIND_ACTION = "plugin.text.editor.search";

	private static final Map<String, String> EXTENSION_TO_SYNTAX = Map.ofEntries(
			Map.entry("java", SyntaxConstants.SYNTAX_STYLE_JAVA),
			Map.entry("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("mjs", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("tsx", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("json", SyntaxConstants.SYNTAX_STYLE_JSON), 
			Map.entry("webmanifest", SyntaxConstants.SYNTAX_STYLE_JSON),
			Map.entry("xml", SyntaxConstants.SYNTAX_STYLE_XML),
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
	private JDialog searchDialog;
	private JTextField searchField;
	private JRadioButton searchTextMode;
	private JRadioButton searchHexMode;
	private JCheckBox searchCaseSensitive;
	private JCheckBox searchRegex;
	private JCheckBox searchWholeWords;
	private JCheckBox searchFuzzy;
	private JLabel searchStatus;

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
		registerFindShortcut();
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

	/**
	 * The largest resource with no local file this editor will open.
	 *
	 * <p>Reading one means a network fetch on the event dispatch thread, so the ceiling is set
	 * where a text file stops being something anyone edits by hand.
	 */
	private static final long MAX_REMOTE_BYTES = 16L * 1024 * 1024;

	@Override
	public JComponent panel() {
		return panel;
	}

	/**
	 * Whether this editor can open {@code resource}.
	 *
	 * <p>A resource with no local file — an object in a bucket, an entry in a remote listing — is
	 * opened read-only rather than turned away: {@link #isEditable()} withholds saving, since the
	 * resource API can read a resource but not write one back.
	 *
	 * <p>Unlike quick view, this runs only when the user actually asks to open something, so it can
	 * afford to fetch the content to decide.
	 *
	 * @param resource the resource to open
	 * @return {@code true} when it can be shown here
	 */
	@Override
	public boolean supports(NuclrResource resource) {

		if (resource == null) {
			return false;
		}

		try {
			
			if (resource.isFolder() || false == resource.isReadable()) {
				return false;
			}

			// A resource with no local file is fetched over the network, and both this probe and
			// the open that follows run on the event dispatch thread. A local file that big has
			// always been allowed to open slowly; a remote one would hang the window instead.
			if (resource.getPath() == null && resource.getLength() > MAX_REMOTE_BYTES) {
				log.info("Not opening {} in the text editor: {} bytes exceeds the {} byte remote limit",
						resource.getName(), resource.getLength(), MAX_REMOTE_BYTES);
				return false;
			}
			
			var st = System.currentTimeMillis();
			var supported = TextFileDetector.isTextFile(resource);
			var et = System.currentTimeMillis();
			
			log.info("TextFileDetector result for {}: {} ({} ms)", resource.getName(), supported, (et - st));
			
			return supported;
			
		} catch (Exception ex) {
			return false;
		}
		
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResource resource) {
		var f2 = new NuclrMenuResource("Save", "F2", SAVE_ACTION);
		var f3 = new NuclrMenuResource("Quit", "F3", REQUEST_CLOSE_ACTION);
		var f7 = new NuclrMenuResource("Search", "F7", FIND_ACTION);

		// A resource with no local file cannot be written back, so it is not offered.
		if (resource != null && resource.getPath() == null) {
			return List.of(f3, f7);
		}

		return List.of(f2, f3, f7);
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
		
		Path path = resource.getPath();

		String filename = displayName(resource);

		String content;
		
		boolean editable = isEditable();
		
		try {
			// Read through the resource, not its path: a remote resource has no local file, and
			// the temp file the detector may have staged is about to be deleted.
			content = readContent(resource);
		} catch (Exception ex) {
			content = "Error reading file: " + ex.getMessage();
			editable = false;
		}

		setText(filename, content);
		textArea.setEditable(editable);
		textArea.setLineWrap(wrapByDefault());
		textArea.setWrapStyleWord(wrapByDefault());
		textArea.setCaretPosition(0);
		dirty = false;
		lastKnownFileStamp = readFileStampQuietly(path);

		titlePath = path != null ? path.toString() : filename;
		updateTitle();

		return true;
	}

	/**
	 * Whether the open resource can be written back.
	 *
	 * <p>{@link NuclrResource} offers a way to read a resource but not to write one, so a resource
	 * with no local file is shown read-only. Saving it would need an upload API this plugin does
	 * not have.
	 *
	 * @return {@code true} when edits can be saved
	 */
	public boolean isEditable() {
		return currentResource == null || currentResource.getPath() != null;
	}

	/**
	 * The whole resource, strictly decoded as UTF-8.
	 *
	 * <p>Strict on purpose, the way {@code Files.readString} is: a file that is not UTF-8 must
	 * fail to open rather than load full of replacement characters, which a later save would
	 * write back over the original bytes.
	 */
	private static String readContent(NuclrResource resource) throws Exception {
		byte[] bytes;
		try (var in = resource.openInputStream()) {
			bytes = in.readAllBytes();
		}
		return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
	}

	/** The name to show in the title bar: the resource's own, or its file name. */
	private static String displayName(NuclrResource resource) {
		if (resource.getName() != null && !resource.getName().isBlank()) {
			return resource.getName();
		}
		Path path = resource.getPath();
		if (path == null) {
			return "";
		}
		return path.getFileName() != null ? path.getFileName().toString() : path.toString();
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
		closeSearchDialog();
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
	public boolean isMessageSupported(String type) {
		return (isEditable() && SAVE_ACTION.equals(type))
				|| REQUEST_CLOSE_ACTION.equals(type)
				|| FIND_ACTION.equals(type)
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
		clearSearchHighlight();
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

	private void registerFindShortcut() {
		var findAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showSearchDialog();
			}
		};
		var f7 = KeyStroke.getKeyStroke("F7");
		var ctrlF = KeyStroke.getKeyStroke("ctrl F");
		bindAction(panel, f7, FIND_ACTION, findAction);
		bindAction(scroll, f7, FIND_ACTION, findAction);
		bindAction(textArea, f7, FIND_ACTION, findAction);
		bindAction(panel, ctrlF, FIND_ACTION, findAction);
		bindAction(scroll, ctrlF, FIND_ACTION, findAction);
		bindAction(textArea, ctrlF, FIND_ACTION, findAction);
	}

	private void showSearchDialog() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::showSearchDialog);
			return;
		}
		if (currentResource == null) {
			return;
		}
		if (searchDialog == null || !searchDialog.isDisplayable()) {
			createSearchDialog();
		}

		seedSearchFieldFromSelection();
		setSearchStatus(" ", false);
		updateSearchControlState();

		if (!searchDialog.isShowing()) {
			searchDialog.pack();
			searchDialog.setLocationRelativeTo(panel);
			searchDialog.setVisible(true);
		}
		searchDialog.toFront();
		SwingUtilities.invokeLater(() -> {
			searchField.requestFocusInWindow();
			searchField.selectAll();
		});
	}

	private void createSearchDialog() {
		Window owner = SwingUtilities.getWindowAncestor(panel);
		searchDialog = new JDialog(owner, "Search", Dialog.ModalityType.MODELESS);
		searchDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

		searchField = new JTextField(34);
		searchTextMode = new JRadioButton("Text", true);
		searchHexMode = new JRadioButton("Hex");
		var modes = new ButtonGroup();
		modes.add(searchTextMode);
		modes.add(searchHexMode);

		searchCaseSensitive = new JCheckBox("Case sensitive");
		searchRegex = new JCheckBox("Regular expressions");
		searchWholeWords = new JCheckBox("Whole words");
		searchFuzzy = new JCheckBox("Fuzzy search");
		searchStatus = new JLabel(" ");

		var previous = new JButton("Find previous");
		var next = new JButton("Find next");
		var cancel = new JButton("Cancel");

		previous.addActionListener(e -> runSearch(false));
		next.addActionListener(e -> runSearch(true));
		cancel.addActionListener(e -> hideSearchDialog());
		searchRegex.addActionListener(e -> {
			if (searchRegex.isSelected()) {
				searchFuzzy.setSelected(false);
			}
			updateSearchControlState();
		});
		searchFuzzy.addActionListener(e -> {
			if (searchFuzzy.isSelected()) {
				searchRegex.setSelected(false);
			}
			updateSearchControlState();
		});
		searchTextMode.addActionListener(e -> updateSearchControlState());
		searchHexMode.addActionListener(e -> updateSearchControlState());

		var main = new JPanel(new GridBagLayout());
		main.setBorder(new EmptyBorder(12, 12, 8, 12));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.WEST;

		gbc.gridx = 0;
		gbc.gridy = 0;
		main.add(new JLabel("Search for"), gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		main.add(searchField, gbc);

		var modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		modePanel.add(searchTextMode);
		modePanel.add(searchHexMode);
		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		main.add(modePanel, gbc);

		var options = new JPanel(new GridBagLayout());
		GridBagConstraints ogbc = new GridBagConstraints();
		ogbc.insets = new Insets(3, 0, 3, 24);
		ogbc.anchor = GridBagConstraints.WEST;
		ogbc.gridx = 0;
		ogbc.gridy = 0;
		options.add(searchCaseSensitive, ogbc);
		ogbc.gridx = 1;
		options.add(searchRegex, ogbc);
		ogbc.gridx = 0;
		ogbc.gridy = 1;
		options.add(searchWholeWords, ogbc);
		ogbc.gridy = 2;
		options.add(searchFuzzy, ogbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 3;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		main.add(options, gbc);

		gbc.gridy = 2;
		main.add(searchStatus, gbc);

		var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
		buttons.add(previous);
		buttons.add(next);
		buttons.add(cancel);

		var root = new JPanel(new BorderLayout());
		root.add(main, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);
		searchDialog.setContentPane(root);
		searchDialog.getRootPane().setDefaultButton(next);
		installSearchDialogShortcuts(previous, next, cancel);
		updateSearchControlState();
	}

	private void installSearchDialogShortcuts(JButton previous, JButton next, JButton cancel) {
		var input = searchDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		var actions = searchDialog.getRootPane().getActionMap();

		input.put(KeyStroke.getKeyStroke("shift ENTER"), "findPrevious");
		actions.put("findPrevious", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				previous.doClick();
			}
		});

		input.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
		actions.put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				cancel.doClick();
			}
		});

		input.put(KeyStroke.getKeyStroke("F7"), "findNext");
		actions.put("findNext", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				next.doClick();
			}
		});
	}

	private void seedSearchFieldFromSelection() {
		if (searchField == null) {
			return;
		}
		String selected = textArea.getSelectedText();
		if (selected != null && !selected.isBlank() && selected.indexOf('\n') < 0 && selected.length() <= 200) {
			searchField.setText(selected);
			searchTextMode.setSelected(true);
		}
	}

	private void updateSearchControlState() {
		if (searchTextMode == null) {
			return;
		}
		boolean textMode = searchTextMode.isSelected();
		searchCaseSensitive.setEnabled(textMode);
		searchRegex.setEnabled(textMode);
		searchWholeWords.setEnabled(textMode);
		searchFuzzy.setEnabled(textMode);
	}

	private void runSearch(boolean forward) {
		SearchOptions options = new SearchOptions(searchHexMode.isSelected(), searchCaseSensitive.isSelected(),
				searchRegex.isEnabled() && searchRegex.isSelected(),
				searchWholeWords.isEnabled() && searchWholeWords.isSelected(),
				searchFuzzy.isEnabled() && searchFuzzy.isSelected());
		String query = searchField.getText();

		try {
			SearchMatch match = options.hex()
					? findHexMatch(query, forward)
					: findTextMatch(query, forward, options);
			if (match == null) {
				clearSearchHighlight();
				setSearchStatus("Not found", true);
				return;
			}
			selectSearchMatch(match);
			setSearchStatus((match.wrapped() ? "Wrapped, " : "") + lineColumnStatus(match.start()), false);
		} catch (IllegalArgumentException ex) {
			setSearchStatus(ex.getMessage(), true);
		}
	}

	private SearchMatch findTextMatch(String query, boolean forward, SearchOptions options) {
		if (query == null || query.isEmpty()) {
			throw new IllegalArgumentException("Enter text to search for.");
		}
		String text = textArea.getText();
		if (text.isEmpty()) {
			return null;
		}
		if (options.regex()) {
			return findRegexMatch(text, query, forward, options);
		}
		if (options.fuzzy()) {
			return findFuzzyMatch(text, query, forward, options);
		}
		return findLiteralMatch(text, query, forward, options);
	}

	private SearchMatch findLiteralMatch(String text, String query, boolean forward, SearchOptions options) {
		String haystack = options.caseSensitive() ? text : text.toLowerCase(Locale.ROOT);
		String needle = options.caseSensitive() ? query : query.toLowerCase(Locale.ROOT);
		if (needle.isEmpty() || needle.length() > haystack.length()) {
			return null;
		}

		if (forward) {
			int from = Math.min(textArea.getSelectionEnd(), haystack.length());
			SearchMatch match = findLiteralForward(text, haystack, needle, from, options.wholeWords(), false);
			return match != null ? match : findLiteralForward(text, haystack, needle, 0, options.wholeWords(), true);
		}

		int from = Math.max(0, textArea.getSelectionStart() - 1);
		SearchMatch match = findLiteralPrevious(text, haystack, needle, from, options.wholeWords(), false);
		return match != null
				? match
				: findLiteralPrevious(text, haystack, needle, haystack.length() - needle.length(), options.wholeWords(),
						true);
	}

	private static SearchMatch findLiteralForward(String text, String haystack, String needle, int from,
			boolean wholeWords, boolean wrapped) {
		int index = haystack.indexOf(needle, Math.max(0, from));
		while (index >= 0) {
			int end = index + needle.length();
			if (!wholeWords || isWholeWordMatch(text, index, end)) {
				return new SearchMatch(index, end, wrapped);
			}
			index = haystack.indexOf(needle, index + 1);
		}
		return null;
	}

	private static SearchMatch findLiteralPrevious(String text, String haystack, String needle, int from,
			boolean wholeWords, boolean wrapped) {
		int startFrom = Math.min(Math.max(0, from), haystack.length() - needle.length());
		int index = haystack.lastIndexOf(needle, startFrom);
		while (index >= 0) {
			int end = index + needle.length();
			if (!wholeWords || isWholeWordMatch(text, index, end)) {
				return new SearchMatch(index, end, wrapped);
			}
			index = haystack.lastIndexOf(needle, index - 1);
		}
		return null;
	}

	private SearchMatch findRegexMatch(String text, String query, boolean forward, SearchOptions options) {
		int flags = Pattern.MULTILINE;
		if (!options.caseSensitive()) {
			flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		}
		Pattern pattern = Pattern.compile(query, flags);

		if (forward) {
			int from = Math.min(textArea.getSelectionEnd(), text.length());
			SearchMatch match = findRegexForward(text, pattern, from, options.wholeWords(), false);
			return match != null ? match : findRegexForward(text, pattern, 0, options.wholeWords(), true);
		}

		int from = Math.max(0, textArea.getSelectionStart());
		SearchMatch match = findRegexPrevious(text, pattern, from, options.wholeWords(), false);
		return match != null ? match : findRegexPrevious(text, pattern, text.length(), options.wholeWords(), true);
	}

	private static SearchMatch findRegexForward(String text, Pattern pattern, int from, boolean wholeWords,
			boolean wrapped) {
		Matcher matcher = pattern.matcher(text);
		int searchFrom = Math.max(0, Math.min(from, text.length()));
		while (matcher.find(searchFrom)) {
			if (matcher.start() == matcher.end()) {
				searchFrom = matcher.end() + 1;
				if (searchFrom > text.length()) {
					return null;
				}
				continue;
			}
			if (!wholeWords || isWholeWordMatch(text, matcher.start(), matcher.end())) {
				return new SearchMatch(matcher.start(), matcher.end(), wrapped);
			}
			searchFrom = matcher.start() + 1;
		}
		return null;
	}

	private static SearchMatch findRegexPrevious(String text, Pattern pattern, int fromExclusive, boolean wholeWords,
			boolean wrapped) {
		Matcher matcher = pattern.matcher(text);
		SearchMatch previous = null;
		int limit = Math.max(0, Math.min(fromExclusive, text.length()));
		while (matcher.find()) {
			if (matcher.start() >= limit) {
				break;
			}
			if (matcher.start() == matcher.end()) {
				continue;
			}
			if (!wholeWords || isWholeWordMatch(text, matcher.start(), matcher.end())) {
				previous = new SearchMatch(matcher.start(), matcher.end(), wrapped);
			}
		}
		return previous;
	}

	private SearchMatch findFuzzyMatch(String text, String query, boolean forward, SearchOptions options) {
		if (query.isEmpty()) {
			return null;
		}

		if (forward) {
			int from = Math.min(textArea.getSelectionEnd(), text.length());
			SearchMatch match = findFuzzyForward(text, query, from, options, false);
			return match != null ? match : findFuzzyForward(text, query, 0, options, true);
		}

		int from = Math.max(0, textArea.getSelectionStart());
		SearchMatch match = findFuzzyPrevious(text, query, from, options, false);
		return match != null ? match : findFuzzyPrevious(text, query, text.length(), options, true);
	}

	private static SearchMatch findFuzzyForward(String text, String query, int from, SearchOptions options,
			boolean wrapped) {
		for (int start = Math.max(0, from); start < text.length(); start++) {
			SearchMatch match = fuzzyAt(text, query, start, options, wrapped);
			if (match != null) {
				return match;
			}
		}
		return null;
	}

	private static SearchMatch findFuzzyPrevious(String text, String query, int fromExclusive, SearchOptions options,
			boolean wrapped) {
		SearchMatch previous = null;
		int start = 0;
		int limit = Math.max(0, Math.min(fromExclusive, text.length()));
		while (start < limit) {
			SearchMatch match = fuzzyAt(text, query, start, options, wrapped);
			if (match == null) {
				start++;
				continue;
			}
			if (match.start() >= limit) {
				break;
			}
			previous = match;
			start = match.start() + 1;
		}
		return previous;
	}

	private static SearchMatch fuzzyAt(String text, String query, int start, SearchOptions options, boolean wrapped) {
		int q = 0;
		int first = -1;
		for (int i = start; i < text.length(); i++) {
			if (sameSearchChar(text.charAt(i), query.charAt(q), options.caseSensitive())) {
				if (first < 0) {
					first = i;
				}
				q++;
				if (q == query.length()) {
					int end = i + 1;
					if (!options.wholeWords() || isWholeWordMatch(text, first, end)) {
						return new SearchMatch(first, end, wrapped);
					}
					return null;
				}
			}
		}
		return null;
	}

	private SearchMatch findHexMatch(String query, boolean forward) {
		byte[] needle = parseHexQuery(query);
		String text = textArea.getText();
		ByteTextIndex index = buildByteTextIndex(text);
		if (needle.length > index.bytes().length) {
			return null;
		}

		if (forward) {
			int from = byteOffsetForChar(index, textArea.getSelectionEnd());
			int byteStart = indexOf(index.bytes(), needle, from);
			SearchMatch match = byteMatchToTextMatch(index, byteStart, needle.length, false);
			return match != null ? match : byteMatchToTextMatch(index, indexOf(index.bytes(), needle, 0), needle.length,
					true);
		}

		int from = byteOffsetForChar(index, textArea.getSelectionStart()) - 1;
		int byteStart = lastIndexOf(index.bytes(), needle, from);
		SearchMatch match = byteMatchToTextMatch(index, byteStart, needle.length, false);
		return match != null
				? match
				: byteMatchToTextMatch(index, lastIndexOf(index.bytes(), needle, index.bytes().length - needle.length),
						needle.length, true);
	}

	private static byte[] parseHexQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Enter hex bytes to search for.");
		}
		String normalized = query.replaceAll("(?i)0x", "")
				.replaceAll("(?i)\\\\x", "")
				.replaceAll("[\\s,;:_-]+", "");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Enter hex bytes to search for.");
		}
		if ((normalized.length() & 1) != 0) {
			throw new IllegalArgumentException("Hex search needs pairs of digits.");
		}
		if (!normalized.matches("[0-9a-fA-F]+")) {
			throw new IllegalArgumentException("Hex search can only contain 0-9 and A-F.");
		}

		byte[] bytes = new byte[normalized.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
		}
		return bytes;
	}

	private static ByteTextIndex buildByteTextIndex(String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		int[] charToByte = new int[text.length() + 1];
		int[] byteToChar = new int[bytes.length + 1];
		int bytePos = 0;

		for (int charIndex = 0; charIndex < text.length();) {
			int codePoint = text.codePointAt(charIndex);
			int nextChar = charIndex + Character.charCount(codePoint);
			int byteLength = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;

			for (int i = charIndex; i < nextChar; i++) {
				charToByte[i] = bytePos;
			}
			for (int i = 0; i < byteLength && bytePos + i < byteToChar.length; i++) {
				byteToChar[bytePos + i] = charIndex;
			}

			bytePos += byteLength;
			if (bytePos < byteToChar.length) {
				byteToChar[bytePos] = nextChar;
			}
			charToByte[nextChar] = Math.min(bytePos, bytes.length);
			charIndex = nextChar;
		}
		charToByte[text.length()] = bytes.length;
		byteToChar[bytes.length] = text.length();
		return new ByteTextIndex(bytes, charToByte, byteToChar);
	}

	private static int byteOffsetForChar(ByteTextIndex index, int charIndex) {
		int clipped = Math.max(0, Math.min(charIndex, index.charToByte().length - 1));
		return index.charToByte()[clipped];
	}

	private static SearchMatch byteMatchToTextMatch(ByteTextIndex index, int byteStart, int byteLength,
			boolean wrapped) {
		if (byteStart < 0) {
			return null;
		}
		int byteEnd = Math.min(index.bytes().length, byteStart + byteLength);
		int start = index.byteToChar()[Math.max(0, Math.min(byteStart, index.byteToChar().length - 1))];
		int end = index.byteToChar()[Math.max(0, Math.min(byteEnd, index.byteToChar().length - 1))];
		if (end <= start) {
			end = Math.min(index.charToByte().length - 1, start + 1);
		}
		return new SearchMatch(start, end, wrapped);
	}

	private static int indexOf(byte[] haystack, byte[] needle, int from) {
		if (needle.length == 0 || haystack.length < needle.length) {
			return -1;
		}
		int limit = haystack.length - needle.length;
		for (int i = Math.max(0, from); i <= limit; i++) {
			if (bytesMatchAt(haystack, needle, i)) {
				return i;
			}
		}
		return -1;
	}

	private static int lastIndexOf(byte[] haystack, byte[] needle, int from) {
		if (needle.length == 0 || haystack.length < needle.length) {
			return -1;
		}
		int start = Math.min(Math.max(0, from), haystack.length - needle.length);
		for (int i = start; i >= 0; i--) {
			if (bytesMatchAt(haystack, needle, i)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean bytesMatchAt(byte[] haystack, byte[] needle, int start) {
		for (int i = 0; i < needle.length; i++) {
			if (haystack[start + i] != needle[i]) {
				return false;
			}
		}
		return true;
	}

	private void selectSearchMatch(SearchMatch match) {
		textArea.select(match.start(), match.end());
		highlightSearchMatch(match);
		textArea.getCaret().setSelectionVisible(false);
		try {
			var rect = textArea.modelToView2D(match.start());
			if (rect != null) {
				textArea.scrollRectToVisible(rect.getBounds());
			}
		} catch (BadLocationException ignored) {
		}
	}

	private void highlightSearchMatch(SearchMatch match) {
		clearSearchHighlight();
		textArea.setMarkAllHighlightColor(searchMatchColor());
		textArea.markAll(List.of(new DocumentRange(match.start(), match.end())));
	}

	private void clearSearchHighlight() {
		textArea.clearMarkAllHighlights();
	}

	private Color searchMatchColor() {
		Color selection = textArea.getSelectionColor();
		Color background = textArea.getBackground();
		return blend(background, selection != null ? selection : new Color(0x4C8BFF), 0.82f);
	}

	private String lineColumnStatus(int offset) {
		try {
			int line = textArea.getLineOfOffset(offset);
			int column = offset - textArea.getLineStartOffset(line);
			return "Line " + (line + 1) + ", column " + (column + 1);
		} catch (BadLocationException e) {
			return "Found";
		}
	}

	private void setSearchStatus(String message, boolean error) {
		if (searchStatus == null) {
			return;
		}
		searchStatus.setText(message);
		searchStatus.setForeground(error ? new Color(0xE05252)
				: themeColor(context != null ? context.getTheme() : null, "Label.foreground", textArea.getForeground()));
	}

	private void hideSearchDialog() {
		if (searchDialog != null) {
			searchDialog.setVisible(false);
		}
		textArea.getCaret().setSelectionVisible(true);
		textArea.requestFocusInWindow();
	}

	private void closeSearchDialog() {
		if (searchDialog != null) {
			searchDialog.setVisible(false);
			searchDialog.dispose();
			searchDialog = null;
			searchField = null;
			searchTextMode = null;
			searchHexMode = null;
			searchCaseSensitive = null;
			searchRegex = null;
			searchWholeWords = null;
			searchFuzzy = null;
			searchStatus = null;
		}
		clearSearchHighlight();
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

		if (FIND_ACTION.equals(type)) {
			showSearchDialog();
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

		if (FIND_ACTION.equals(actionType)) {
			showSearchDialog();
			return;
		}

		if (TOGGLE_WRAP_ACTION.equals(actionType)) {
			toggleWrap();
		}

	}


	private static boolean isWholeWordMatch(String text, int start, int end) {
		return isWordBoundary(text, start - 1) && isWordBoundary(text, end);
	}

	private static boolean isWordBoundary(String text, int index) {
		return index < 0 || index >= text.length() || !isWordChar(text.charAt(index));
	}

	private static boolean isWordChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '_';
	}

	private static boolean sameSearchChar(char a, char b, boolean caseSensitive) {
		if (caseSensitive) {
			return a == b;
		}
		return Character.toLowerCase(a) == Character.toLowerCase(b);
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

	private record SearchOptions(boolean hex, boolean caseSensitive, boolean regex, boolean wholeWords, boolean fuzzy) {
	}

	private record SearchMatch(int start, int end, boolean wrapped) {
	}

	private record ByteTextIndex(byte[] bytes, int[] charToByte, int[] byteToChar) {
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
