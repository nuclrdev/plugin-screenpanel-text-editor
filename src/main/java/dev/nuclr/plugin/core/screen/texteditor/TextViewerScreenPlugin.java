package dev.nuclr.plugin.core.screen.texteditor;

import java.util.List;

import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrResource;

public class TextViewerScreenPlugin extends TextEditorScreenPlugin {

	private static final String PLUGIN_ID   = "dev.nuclr.plugin.core.screen.textviewer";
	
	private static final String PLUGIN_NAME = "Text Viewer";

	@Override
	public Role role() {
		return Role.Viewer;
	}

	/** In the read-only viewer, F2 toggles word wrap rather than saving. */
	@Override
	protected void onPrimaryKey() {
		toggleWrap();
	}

	/** The viewer opens unwrapped and lets the user toggle wrap with F2. */
	@Override
	protected boolean wrapByDefault() {
		return false;
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResource resource) {

		var f1 = new NuclrMenuResource("Help", "F1", "plugin.text.editor.help");
		var f2 = new NuclrMenuResource("Wrap", "F2", "plugin.text.editor.wrap");
		var f3 = new NuclrMenuResource("Quit", "F3", "plugin.fullscreen.close");
		var f4 = new NuclrMenuResource("Hex", "F4", "plugin.text.editor.hex");
		var f6 = new NuclrMenuResource("Edit", "F6", "plugin.text.editor.edit");
		var f7 = new NuclrMenuResource("Search", "F7", "plugin.text.editor.search");
		var f10 = new NuclrMenuResource("Quit", "F10", "");

		return List.of(f1, f2, f3, f4, f6, f7, f10);

	}

	@Override
	public boolean isEditable() {
		return false;
	}

	@Override
	public String id() {
		return PLUGIN_ID;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

}
