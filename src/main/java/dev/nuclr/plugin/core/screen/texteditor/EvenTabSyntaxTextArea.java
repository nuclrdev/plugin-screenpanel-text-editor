package dev.nuclr.plugin.core.screen.texteditor;

import java.awt.Graphics;
import java.awt.Shape;

import javax.swing.text.Element;
import javax.swing.text.View;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI;
import org.fife.ui.rsyntaxtextarea.WrappedSyntaxView;
import org.fife.ui.rtextarea.RTextAreaUI;
import org.fife.util.SwingUtils;

/**
 * An {@link RSyntaxTextArea} whose tabs are as wide wrapped as unwrapped.
 *
 * <p>RSyntaxTextArea measures a tab stop as so many spaces when lines are not wrapped, but
 * as so many letter {@code m}s when they are. In a proportional font an {@code m} is about
 * three spaces, so the editor - which wraps by default - indented three times deeper than
 * Quick View showing the same file. Here the wrapped view counts spaces too.
 */
final class EvenTabSyntaxTextArea extends RSyntaxTextArea {

	@Override
	protected RTextAreaUI createRTextAreaUI() {
		return new RSyntaxTextAreaUI(this) {
			@Override
			public View create(Element elem) {
				return EvenTabSyntaxTextArea.this.getLineWrap() ? new SpaceTabWrappedView(elem) : super.create(elem);
			}
		};
	}

	private static final class SpaceTabWrappedView extends WrappedSyntaxView {

		/** Where tab stops are counted from: the left of the text, as the view itself keeps it. */
		private int tabBase;

		SpaceTabWrappedView(Element elem) {
			super(elem);
		}

		@Override
		public void paint(Graphics g, Shape a) {
			tabBase = a.getBounds().x;
			super.paint(g, a);
		}

		@Override
		public float nextTabStop(float x, int tabOffset) {
			var host = getContainer();
			var tabSize = getTabSize() * SwingUtils.charWidth(host.getFontMetrics(host.getFont()), ' ');
			if (tabSize <= 0) {
				return x;
			}
			var tabs = (int) ((x - tabBase) / tabSize);
			return tabBase + (tabs + 1) * tabSize;
		}
	}
}
