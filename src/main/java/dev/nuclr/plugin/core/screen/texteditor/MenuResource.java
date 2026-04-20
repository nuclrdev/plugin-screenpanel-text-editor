package dev.nuclr.plugin.core.screen.texteditor;

import dev.nuclr.platform.plugin.NuclrMenuResource;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MenuResource extends NuclrMenuResource {

	protected String eventType;

	@Override
	public String getEventType() {
		return eventType;
	}

	public MenuResource(String name, String keystroke, String eventType) {
		this.setKeyStroke(keystroke);
		this.setName(name);
		this.eventType = eventType;
	}
	
}
