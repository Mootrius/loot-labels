package com.lootlabels;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * How a ground item label is drawn. A label that belongs to a group gets a box in the group's color: a border and a
 * fill of that color (their opacity and the border's thickness come from the settings). A label that is in no group is
 * drawn as plain text.
 */
final class LabelStyle
{
	static final LabelStyle PLAIN = new LabelStyle(null, null, null);

	static final int FILL_ALPHA = 0xB7;

	private final Color border;
	private final Color fill;
	private final BufferedImage icon;

	private LabelStyle(Color border, Color fill, BufferedImage icon)
	{
		this.border = border;
		this.fill = fill;
		this.icon = icon;
	}

	/**
	 * @param rgb the group's color as 0xRRGGBB
	 * @param icon drawn left of the text inside the box, or null for none
	 */
	static LabelStyle ofGroup(int rgb, BufferedImage icon)
	{
		return new LabelStyle(new Color(rgb & 0xFFFFFF),
			new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, FILL_ALPHA), icon);
	}

	boolean isBoxed()
	{
		return border != null;
	}

	Color getBorder()
	{
		return border;
	}

	Color getFill()
	{
		return fill;
	}

	BufferedImage getIcon()
	{
		return icon;
	}
}
