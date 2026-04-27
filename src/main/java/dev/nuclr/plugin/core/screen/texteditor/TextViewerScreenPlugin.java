package dev.nuclr.plugin.core.screen.texteditor;

import java.util.List;

import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;

public class TextViewerScreenPlugin extends TextEditorScreenPlugin {

	private static final String PLUGIN_ID   = "dev.nuclr.plugin.core.screen.textviewer";
	private static final String PLUGIN_NAME = "Text Viewer";

	@Override
	public NuclrPluginRole role() {
		return NuclrPluginRole.FullScreenViewer;
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResourcePath resource) {

		var f1 = new MenuResource("Help", "F1", "plugin.text.editor.help");
		var f2 = new MenuResource("Wrap", "F2", "plugin.text.editor.wrap");
		var f3 = new MenuResource("Quit", "F3", "plugin.fullscreen.close");
		var f4 = new MenuResource("Hex", "F4", "plugin.text.editor.hex");
		var f6 = new MenuResource("Edit", "F6", "plugin.text.editor.edit");
		var f7 = new MenuResource("Search", "F7", "plugin.text.editor.search");
		var f10 = new MenuResource("Quit", "F10", "");

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
