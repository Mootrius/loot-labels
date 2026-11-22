package com.lootlabels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * A row of connected options where one can be picked, drawn in grays. The labels "=", ">", "<", ">=" and "<=" are
 * drawn as symbols (so they don't depend on the font having them); any other label is drawn as text.
 */
class SegmentedControl extends JComponent
{
	private static final int HEIGHT = 16;
	private static final int SYMBOL_WIDTH = 17;
	private static final int TEXT_PADDING = 10;
	private static final float SYMBOL_SCALE = 0.7f; // symbols are designed for a 20 px tall control
	private static final Color OUTLINE = new Color(82, 82, 82);
	private static final Color SELECTED_FILL = new Color(72, 72, 72);
	private static final Color HOVER_FILL = new Color(44, 44, 44);

	private final String[] labels;
	private final int[] widths;
	private final boolean allowDeselect;
	private final IntConsumer onSelect;
	private int selected;
	private int hovered = -1;

	/**
	 * @param selected the index that starts out picked, or -1 for none
	 * @param allowDeselect whether pressing the picked option again clears the choice (reported as -1)
	 * @param onSelect told the index that was picked
	 */
	SegmentedControl(String[] labels, int selected, boolean allowDeselect, IntConsumer onSelect)
	{
		this.labels = labels;
		this.selected = selected;
		this.allowDeselect = allowDeselect;
		this.onSelect = onSelect;

		final FontMetrics metrics = getFontMetrics(DarkUi.editorFont());
		widths = new int[labels.length];
		int total = 0;
		for (int i = 0; i < labels.length; i++)
		{
			widths[i] = isSymbol(labels[i]) ? SYMBOL_WIDTH : metrics.stringWidth(labels[i]) + TEXT_PADDING;
			total += widths[i];
		}

		final Dimension size = new Dimension(total, HEIGHT);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFont(DarkUi.editorFont());

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				final int index = indexAt(e.getX());
				if (index < 0)
				{
					return;
				}

				if (index == SegmentedControl.this.selected)
				{
					if (!SegmentedControl.this.allowDeselect)
					{
						return;
					}
					SegmentedControl.this.selected = -1;
					repaint();
					SegmentedControl.this.onSelect.accept(-1);
					return;
				}

				SegmentedControl.this.selected = index;
				repaint();
				SegmentedControl.this.onSelect.accept(index);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hovered = -1;
				repaint();
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				final int index = indexAt(e.getX());
				if (index != hovered)
				{
					hovered = index;
					repaint();
				}
			}
		});
	}

	private static boolean isSymbol(String label)
	{
		return label.equals("=") || label.equals(">") || label.equals("<") || label.equals(">=") || label.equals("<=");
	}

	private int indexAt(int x)
	{
		int left = 0;
		for (int i = 0; i < widths.length; i++)
		{
			if (x >= left && x < left + widths[i])
			{
				return i;
			}
			left += widths[i];
		}
		return -1;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		final int h = getHeight();
		int left = 0;
		for (int i = 0; i < labels.length; i++)
		{
			final boolean isSelected = i == selected;
			if (isSelected || i == hovered)
			{
				g2.setColor(isSelected ? SELECTED_FILL : HOVER_FILL);
				g2.fill(new Rectangle2D.Float(left, 0, widths[i], h));
			}

			final Color color = isSelected ? DarkUi.TEXT_BRIGHT : (i == hovered ? DarkUi.TEXT : DarkUi.TEXT_DIM);
			g2.setColor(color);
			if (isSymbol(labels[i]))
			{
				final Graphics2D symbol = (Graphics2D) g2.create();
				symbol.translate(left + widths[i] / 2f, h / 2f);
				symbol.scale(SYMBOL_SCALE, SYMBOL_SCALE);
				drawSymbol(symbol, labels[i], 0, 0);
				symbol.dispose();
			}
			else
			{
				g2.setFont(getFont());
				final FontMetrics metrics = g2.getFontMetrics();
				g2.drawString(labels[i], left + (widths[i] - metrics.stringWidth(labels[i])) / 2f,
					(h - metrics.getHeight()) / 2f + metrics.getAscent());
			}
			left += widths[i];
		}

		g2.setStroke(new BasicStroke(1f));
		g2.setColor(OUTLINE);
		for (int i = 1; i < labels.length; i++)
		{
			int dividerX = 0;
			for (int j = 0; j < i; j++)
			{
				dividerX += widths[j];
			}
			g2.draw(new Line2D.Float(dividerX, 0, dividerX, h));
		}
		g2.draw(new Rectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, h - 1f));
		g2.dispose();
	}

	/** Draws = > < >= or <= centered on (cx, cy). */
	private static void drawSymbol(Graphics2D g2, String symbol, float cx, float cy)
	{
		g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (symbol.equals("="))
		{
			g2.draw(new Line2D.Float(cx - 4, cy - 2, cx + 4, cy - 2));
			g2.draw(new Line2D.Float(cx - 4, cy + 2, cx + 4, cy + 2));
			return;
		}

		final boolean right = symbol.startsWith(">");
		final boolean underlined = symbol.length() == 2;
		final float dx = right ? 1 : -1; // which way the chevron points
		final float top = underlined ? -5.5f : -4f;
		final float bottom = underlined ? 1.5f : 4f;
		final float tipY = (top + bottom) / 2;

		final Path2D chevron = new Path2D.Float();
		chevron.moveTo(cx - 3 * dx, cy + top);
		chevron.lineTo(cx + 3 * dx, cy + tipY);
		chevron.lineTo(cx - 3 * dx, cy + bottom);
		g2.draw(chevron);

		if (underlined)
		{
			g2.draw(new Line2D.Float(cx - 3.5f, cy + 5f, cx + 3.5f, cy + 5f));
		}
	}
}
