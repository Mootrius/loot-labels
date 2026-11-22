package com.lootlabels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class LootLabelsOverlay extends Overlay
{
	// Same values RuneLite's Ground Items plugin uses
	private static final int OFFSET_Z = 20; // world units above the top of the item pile, so the camera angle scales it
	private static final int STRING_GAP = 15; // pixels between stacked plain labels
	private static final int PAD_X = 4; // space between the text and the left/right edge of a box
	private static final int PAD_Y = 2; // space between the text and the top/bottom edge of a box
	private static final int ICON_GAP = 3; // space between a rank icon and the text
	private static final float DEFAULT_BORDER_WIDTH = 1.5f; // the border thickness the padding was sized for
	private static final int PIE_DIAMETER = 10; // the despawn pie
	private static final int DESPAWN_GAP = 4; // space between the label and its despawn pie or timer
	private static final double SECONDS_PER_TICK = 0.6;
	private static final long SNAP_NANOS = 1_200_000_000L; // a timer this far off is reset rather than nudged
	private static final int RED_TICKS = 50; // 30 seconds
	private static final float OUTLINE_WIDTH = 0.75f; // how far the outline font accent sticks out of the letters


	private Font font;
	private LootLabelsConfig.FontSize fontSize;
	private LootLabelsConfig.FontWeight fontWeight;
	private LootLabelsConfig.FontStyle fontStyle;

	private final Client client;
	private final LootLabelsPlugin plugin;
	private final LootLabelsConfig config;
	private final LabelGroupManager groups;

	@Inject
	private LootLabelsOverlay(Client client, LootLabelsPlugin plugin, LootLabelsConfig config,
		LabelGroupManager groups)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.groups = groups;
		setPosition(OverlayPosition.DYNAMIC);
		// Above the scene and overheads, below interfaces, and last within its layer so other overlays
		// (including the player's) can't draw over the labels
		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final int plane = client.getTopLevelWorldView().getPlane();
		final Font originalFont = graphics.getFont();
		graphics.setFont(labelFont());
		final LootLabelsConfig.FontAccent accent = config.fontAccent();
		final Color textColor = config.textColor();
		// The border width is kept between 0 and 2 in tenths, since the settings panel doesn't limit a decimal field
		final float borderWidth = Math.max(0, Math.min(20, Math.round(config.borderThickness() * 10))) / 10f;
		final int borderAlpha = percentToAlpha(config.borderOpacity());
		final int fillAlpha = percentToAlpha(config.backgroundOpacity());
		final boolean showWorldSpawns = config.showWorldSpawns();
		final boolean showOthersDrops = config.showOthersDrops();
		// Holding the hotkey shows more: unlabelled items as plain text, and values and despawn timers
		final boolean held = plugin.isHotkeyHeld();
		final boolean revealNonLabeled = held && config.hotkeyShowNonLabeled();
		final boolean showValue = config.showValue() || (held && config.hotkeyShowValues());
		final boolean showDespawn = config.showDespawn() || (held && config.hotkeyShowDespawn());
		final LootLabelsConfig.DespawnType despawnType = config.despawnType();
		final boolean despawnColors = config.despawnStyle() == LootLabelsConfig.DespawnStyle.COLORS;
		final int tick = client.getTickCount();

		for (Map.Entry<Tile, List<GroundLabel>> entry : plugin.getLabels().entrySet())
		{
			final Tile tile = entry.getKey();
			if (tile.getPlane() != plane)
			{
				continue;
			}

			final LocalPoint location = tile.getLocalLocation();
			if (location == null)
			{
				continue;
			}

			// Anchor just above the top of the item(s) lying on the tile
			final ItemLayer itemLayer = tile.getItemLayer();
			final int zOffset = (itemLayer == null ? 0 : itemLayer.getHeight()) + OFFSET_Z;

			// Several items on one tile are stacked upwards, oldest at the bottom
			float stacked = 0;
			for (GroundLabel label : combine(visible(entry.getValue(), showWorldSpawns, showOthersDrops)))
			{
				// items hidden by the label groups don't take up space
				LabelStyle style = groups.styleFor(label, LootLabelsPlugin.SHOW_UNGROUPED);
				if (style == null && revealNonLabeled)
				{
					style = LabelStyle.PLAIN; // text only, no border or background
				}
				if (style == null)
				{
					continue;
				}

				final String text = label.getText(config, showValue);
				final Point point = Perspective.getCanvasTextLocation(client, graphics, location, text, zOffset);
				if (point == null)
				{
					continue;
				}

				final FontMetrics metrics = graphics.getFontMetrics();
				final Despawn despawn = showDespawn ? despawnOf(label, tick, despawnColors, textColor) : null;
				final boolean pie = despawn != null && despawnType == LootLabelsConfig.DespawnType.PIE;
				final String timer = despawn == null || pie ? ""
					: (despawnType == LootLabelsConfig.DespawnType.TICKS
						? String.valueOf(despawn.remaining)
						: String.format(Locale.ROOT, "%.1f", secondsLeft(label, despawn)));

				final int textWidth = metrics.stringWidth(text);
				final BufferedImage icon = style.isBoxed() ? style.getIcon() : null;
				final int iconSpace = icon == null ? 0 : icon.getWidth() + ICON_GAP;
				final int contentWidth = iconSpace + textWidth;

				// The icon and text are centered on the item together, the icon on the left. A despawn pie or timer goes
				// to the right of all that, outside the box.
				final int left = point.getX() + textWidth / 2 - contentWidth / 2;
				final float y = point.getY() - stacked;
				// The padding is sized for the default border; a thicker or thinner one grows or shrinks the box outward,
				// so the space between the text and the border doesn't change with the thickness
				final float grow = style.isBoxed() ? borderWidth - DEFAULT_BORDER_WIDTH : 0;
				if (style.isBoxed())
				{
					final float height = metrics.getAscent() + metrics.getDescent() + PAD_Y * 2 + grow * 2;
					final float top = y - metrics.getAscent() - PAD_Y - grow;
					drawBox(graphics, style, left - PAD_X - grow, top, contentWidth + PAD_X * 2 + grow * 2, height,
						borderWidth, borderAlpha, fillAlpha);
					if (icon != null)
					{
						graphics.drawImage(icon, left, Math.round(top + (height - icon.getHeight()) / 2f), null);
					}

					// Boxes touch, each keeping its own border
					stacked += height;
				}
				else
				{
					stacked += STRING_GAP;
				}

				final int x = left + iconSpace;
				drawText(graphics, x, Math.round(y), text, accent, textColor);

				if (despawn != null)
				{
					final int right = Math.round(left + contentWidth + (style.isBoxed() ? PAD_X + grow : 0) + DESPAWN_GAP);
					if (pie)
					{
						drawPie(graphics, right + PIE_DIAMETER / 2f, y - metrics.getAscent() * 0.4f, despawn.progress,
							despawn.color, accent);
					}
					else
					{
						drawText(graphics, right, Math.round(y), timer, accent, despawn.color);
					}
				}
			}
		}

		graphics.setFont(originalFont);
		return null;
	}

	/** The label font for the current settings, kept until a setting changes. */
	private Font labelFont()
	{
		final LootLabelsConfig.FontSize size = config.fontSize();
		final LootLabelsConfig.FontWeight weight = config.fontWeight();
		final LootLabelsConfig.FontStyle style = config.fontStyle();
		if (font == null || size != fontSize || weight != fontWeight || style != fontStyle)
		{
			// Small is the RuneScape Small font, large is the regular RuneScape font. Only the regular font has a real
			// bold face; for Small, bold (and italic for both) are added as style flags and Java synthesizes them.
			final boolean bold = weight == LootLabelsConfig.FontWeight.BOLD;
			final Font base;
			if (size == LootLabelsConfig.FontSize.SMALL)
			{
				base = FontManager.getRunescapeSmallFont();
			}
			else
			{
				base = bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont();
			}

			int flags = base.getStyle();
			if (bold)
			{
				flags |= Font.BOLD;
			}
			if (style == LootLabelsConfig.FontStyle.ITALICIZED)
			{
				flags |= Font.ITALIC;
			}
			font = base.deriveFont(flags, size.points);
			fontSize = size;
			fontWeight = weight;
			fontStyle = style;
		}
		return font;
	}

	/**
	 * How long an item has left, or null when that isn't known (items that never despawn, or ones that already
	 * should have). The color follows who can see the item: green while only you can, yellow once others can, and
	 * red for the last 30 seconds.
	 */
	private static Despawn despawnOf(GroundLabel label, int tick, boolean colors, Color plain)
	{
		final TileItem item = label.getItem();
		final int remaining = item.getDespawnTime() - tick;
		if (remaining <= 0)
		{
			return null;
		}

		final int total = Math.max(item.getDespawnTime() - label.getSpawnTick(), remaining);
		final float progress = remaining / (float) total;
		final Color color;
		if (!colors)
		{
			color = plain;
		}
		else if (remaining <= RED_TICKS)
		{
			color = Color.RED;
		}
		else if (item.isPrivate() || tick < item.getVisibleTime())
		{
			color = Color.GREEN;
		}
		else
		{
			color = Color.YELLOW;
		}
		return new Despawn(remaining, progress, color);
	}

	/**
	 * Seconds until the item despawns, counting down smoothly. Game ticks don't arrive at exactly 0.6 second
	 * intervals, so counting from each tick makes the number jump. Instead each item keeps an expected despawn time,
	 * which every new tick only nudges toward what that tick says.
	 */
	private double secondsLeft(GroundLabel label, Despawn despawn)
	{
		final GroundLabel target = label.target();
		final int tickCount = plugin.getLastTickCount();
		final long tickNanos = plugin.getLastTickNanos();
		if (tickNanos != 0 && target.getSyncedTick() != tickCount)
		{
			final long estimate = tickNanos
				+ (long) ((label.getItem().getDespawnTime() - tickCount) * SECONDS_PER_TICK * 1e9);
			final long current = target.getDeadlineNanos();
			final boolean snap = current == 0 || Math.abs(estimate - current) > SNAP_NANOS;
			target.setDeadlineNanos(snap ? estimate : current + (estimate - current) / 4);
			target.setSyncedTick(tickCount);
		}

		if (target.getDeadlineNanos() == 0)
		{
			return despawn.remaining * SECONDS_PER_TICK;
		}
		return Math.max(0, (target.getDeadlineNanos() - System.nanoTime()) / 1e9);
	}

	private static final class Despawn
	{
		private final int remaining; // game ticks
		private final float progress; // 1 when new, 0 when it despawns
		private final Color color;

		private Despawn(int remaining, float progress, Color color)
		{
			this.remaining = remaining;
			this.progress = progress;
			this.color = color;
		}
	}

	/** A small circle that drains clockwise as the item runs out of time. */
	private static void drawPie(Graphics2D graphics, float centerX, float centerY, float progress, Color color,
		LootLabelsConfig.FontAccent accent)
	{
		final Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		final Stroke stroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final float radius = PIE_DIAMETER / 2f;
		final Arc2D.Float wedge = new Arc2D.Float(centerX - radius, centerY - radius, PIE_DIAMETER, PIE_DIAMETER, 90,
			progress * 360, Arc2D.PIE);
		final Ellipse2D.Float ring = new Ellipse2D.Float(centerX - radius, centerY - radius, PIE_DIAMETER, PIE_DIAMETER);

		// The same accent as the text: a black copy one pixel down and right, or a black edge around the pie
		graphics.setColor(Color.BLACK);
		if (accent == LootLabelsConfig.FontAccent.SHADOW)
		{
			graphics.translate(1, 1);
			graphics.fill(wedge);
			graphics.setStroke(new BasicStroke(1f));
			graphics.draw(ring);
			graphics.translate(-1, -1);
		}
		else if (accent == LootLabelsConfig.FontAccent.OUTLINE)
		{
			graphics.setStroke(new BasicStroke(1f + OUTLINE_WIDTH * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.draw(ring);
			graphics.draw(wedge);
		}

		graphics.setColor(color);
		graphics.fill(wedge);
		graphics.setStroke(new BasicStroke(1f));
		graphics.draw(ring);

		graphics.setStroke(stroke);
		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/** Draws the text with its accent (a drop shadow, an outline, or nothing) behind it. */
	private static void drawText(Graphics2D graphics, int x, int y, String text, LootLabelsConfig.FontAccent accent,
		Color color)
	{
		graphics.setColor(Color.BLACK);
		if (accent == LootLabelsConfig.FontAccent.SHADOW)
		{
			graphics.drawString(text, x + 1, y + 1);
		}
		else if (accent == LootLabelsConfig.FontAccent.OUTLINE)
		{
			// Strokes the shape of the letters. A stroke is centered on the edge, so one twice as wide sticks out by
			// OUTLINE_WIDTH; the other half is hidden under the text. (Offset copies of the text can only move whole pixels.)
			final Shape letters = graphics.getFont().createGlyphVector(graphics.getFontRenderContext(), text)
				.getOutline(x, y);
			final Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			final Stroke stroke = graphics.getStroke();
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setStroke(new BasicStroke(OUTLINE_WIDTH * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.draw(letters);
			graphics.setStroke(stroke);
			if (antialiasing != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
			}
		}

		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	/** Leaves out world spawns (items nobody dropped) and other players' drops when those are turned off. */
	static List<GroundLabel> visible(List<GroundLabel> labels, boolean worldSpawns, boolean othersDrops)
	{
		if (worldSpawns && othersDrops)
		{
			return labels;
		}

		final List<GroundLabel> shown = new ArrayList<>(labels.size());
		for (GroundLabel label : labels)
		{
			final int ownership = label.getItem().getOwnership();
			if ((ownership == TileItem.OWNERSHIP_NONE && !worldSpawns)
				|| (ownership == TileItem.OWNERSHIP_OTHER && !othersDrops))
			{
				continue;
			}
			shown.add(label);
		}
		return shown;
	}

	/** Several of the same item lying on one tile become a single "Name xN" label, in the position of the first. */
	static List<GroundLabel> combine(List<GroundLabel> labels)
	{
		if (labels.size() < 2)
		{
			return labels;
		}

		final Map<Integer, List<GroundLabel>> byItem = new LinkedHashMap<>();
		for (GroundLabel label : labels)
		{
			byItem.computeIfAbsent(label.getItem().getId(), id -> new ArrayList<>(1)).add(label);
		}

		if (byItem.size() == labels.size())
		{
			return labels;
		}

		final List<GroundLabel> combined = new ArrayList<>(byItem.size());
		for (List<GroundLabel> same : byItem.values())
		{
			combined.add(same.size() == 1 ? same.get(0) : GroundLabel.merged(same));
		}
		return combined;
	}

	/** An opaque border around a see-through fill of the same color. */
	private static int percentToAlpha(int percent)
	{
		return Math.max(0, Math.min(100, percent)) * 255 / 100;
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private static void drawBox(Graphics2D graphics, LabelStyle style, float x, float y, float width, float height,
		float borderWidth, int borderAlpha, int fillAlpha)
	{
		final Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		final Stroke stroke = graphics.getStroke();

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(withAlpha(style.getFill(), fillAlpha));
		graphics.fill(new Rectangle2D.Float(x, y, width, height));

		// Stroke is centered on the line, so inset by half its width to keep the outer edge on the box
		if (borderWidth > 0)
		{
			final float inset = borderWidth / 2;
			graphics.setColor(withAlpha(style.getBorder(), borderAlpha));
			graphics.setStroke(new BasicStroke(borderWidth));
			graphics.draw(new Rectangle2D.Float(x + inset, y + inset, width - borderWidth, height - borderWidth));
		}

		graphics.setStroke(stroke);
		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}
}
