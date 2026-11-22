package com.lootlabels;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** A small on/off switch drawn in grays: white when on, dim gray when off. */
class ToggleSwitch extends JComponent
{
	private static final Color LINE_ON = Color.WHITE;
	private static final Color LINE_OFF = new Color(96, 96, 96);
	private static final Color KNOB_ON = Color.WHITE;

	private boolean selected;
	private boolean hovered;

	ToggleSwitch(boolean selected, Consumer<Boolean> onChange)
	{
		this.selected = selected;

		final Dimension size = new Dimension(30, 16);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hovered = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				ToggleSwitch.this.selected = !ToggleSwitch.this.selected;
				repaint();
				onChange.accept(ToggleSwitch.this.selected);
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Full white when on; when off it is dimmed until hovered, like the other controls
		g2.setComposite(AlphaComposite.SrcOver.derive(selected || hovered ? 1f : 0.6f));

		final int w = getWidth();
		final int h = getHeight();
		final int knob = h - 8;

		// Outline only: the track is never filled. On: bright outline and a filled white knob on the right.
		// Off: dim outline and an outlined knob on the left.
		g2.setStroke(new BasicStroke(1.3f));
		g2.setColor(selected ? LINE_ON : LINE_OFF);
		g2.draw(new RoundRectangle2D.Float(0.65f, 0.65f, w - 1.3f, h - 1.3f, h - 1.3f, h - 1.3f));

		if (selected)
		{
			g2.setColor(KNOB_ON);
			g2.fillOval(w - knob - 4, 4, knob, knob);
		}
		else
		{
			g2.drawOval(4, 4, knob, knob);
		}
		g2.dispose();
	}
}
