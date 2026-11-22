package com.lootlabels;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Lets the user drag one of a column of components up or down to reorder it, and tells apart a drag from a click.
 * Attach one instance to every component that should act as the handle (e.g. a row and the label in it).
 *
 * While dragging, a thin line shows where the component would land. Nothing moves until the mouse is released, at
 * which point the owner is told the new position and is expected to rebuild the column.
 */
final class DragReorder extends MouseAdapter
{
	private static final int THRESHOLD = 4; // pixels the mouse has to move before a press becomes a drag

	private final JComponent container;
	private final List<JComponent> siblings;
	private final int index;
	private final IntConsumer onMove;
	private final Consumer<MouseEvent> onClick;
	private final Consumer<MouseEvent> onPopup;

	private boolean pressed;
	private boolean dragging;
	private int pressY;
	private int[] middles;
	private int target;
	private JPanel marker;

	/**
	 * @param container the panel that directly holds the siblings
	 * @param siblings everything that can be reordered together, top to bottom (including the dragged component)
	 * @param index this component's place in siblings
	 * @param onMove told the place (among all the siblings) the component was dropped at
	 * @param onClick told about a left click that wasn't a drag
	 * @param onPopup told about a right click (or whatever opens a context menu), or null
	 */
	DragReorder(JComponent container, List<JComponent> siblings, int index, IntConsumer onMove,
		Consumer<MouseEvent> onClick, Consumer<MouseEvent> onPopup)
	{
		this.container = container;
		this.siblings = siblings;
		this.index = index;
		this.onMove = onMove;
		this.onClick = onClick;
		this.onPopup = onPopup;
	}

	void attach(Component... components)
	{
		for (Component component : components)
		{
			component.addMouseListener(this);
			component.addMouseMotionListener(this);
		}
	}

	@Override
	public void mousePressed(MouseEvent e)
	{
		if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e))
		{
			if (onPopup != null)
			{
				onPopup.accept(e);
			}
			return;
		}

		if (SwingUtilities.isLeftMouseButton(e))
		{
			pressed = true;
			dragging = false;
			pressY = containerY(e);
		}
	}

	@Override
	public void mouseDragged(MouseEvent e)
	{
		if (!pressed)
		{
			return;
		}

		final int y = containerY(e);
		if (!dragging)
		{
			if (Math.abs(y - pressY) < THRESHOLD)
			{
				return;
			}
			startDragging();
		}

		// The new place is the number of the other siblings that are above the mouse
		int above = 0;
		for (int i = 0; i < middles.length; i++)
		{
			if (i != index && middles[i] < y)
			{
				above++;
			}
		}

		if (marker == null || above != target)
		{
			target = above;
			showMarker();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e)
	{
		if (!pressed)
		{
			return;
		}
		pressed = false;

		if (dragging)
		{
			dragging = false;
			hideMarker();
			container.setCursor(null);
			if (target != index)
			{
				onMove.accept(target);
			}
		}
		else if (e.getComponent().contains(e.getPoint()))
		{
			onClick.accept(e);
		}
	}

	private int containerY(MouseEvent e)
	{
		return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), container).y;
	}

	private void startDragging()
	{
		dragging = true;
		target = index;

		// Positions are taken now, before the marker moves anything
		middles = new int[siblings.size()];
		for (int i = 0; i < middles.length; i++)
		{
			final JComponent sibling = siblings.get(i);
			middles[i] = sibling.getY() + sibling.getHeight() / 2;
		}
		container.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
	}

	private void showMarker()
	{
		hideMarker();

		final List<JComponent> others = new ArrayList<>(siblings);
		others.remove(index);
		if (others.isEmpty())
		{
			return;
		}

		marker = new JPanel();
		marker.setBackground(DarkUi.LINE_LIT);
		marker.setPreferredSize(new Dimension(0, 2));
		marker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
		marker.setAlignmentX(Component.LEFT_ALIGNMENT);

		final int at = target < others.size()
			? container.getComponentZOrder(others.get(target))
			: container.getComponentZOrder(others.get(others.size() - 1)) + 1;
		container.add(marker, at);
		container.revalidate();
		container.repaint();
	}

	private void hideMarker()
	{
		if (marker != null)
		{
			container.remove(marker);
			marker = null;
			container.revalidate();
			container.repaint();
		}
	}
}
