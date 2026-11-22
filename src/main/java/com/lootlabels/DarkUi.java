package com.lootlabels;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Sidebar widgets drawn in grays only. They use the basic Swing UI classes so the look-and-feel can't paint
 * anything light over them.
 */
final class DarkUi
{
	static final Color PANEL = ColorScheme.DARK_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color FIELD = new Color(22, 22, 22);
	static final Color BUTTON = new Color(56, 56, 56);
	static final Color BUTTON_HOVER = new Color(72, 72, 72);
	static final Color BUTTON_PRESSED = new Color(46, 46, 46);
	static final Color MENU = new Color(24, 24, 24);
	static final Color LINE_LIT = new Color(175, 175, 175);
	static final Color LINE = new Color(64, 64, 64);
	static final Color TEXT = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color TEXT_IDLE = new Color(130, 130, 130);
	static final Color TEXT_DIM = ColorScheme.MEDIUM_GRAY_COLOR;
	static final Color TEXT_BRIGHT = new Color(235, 235, 235);

	private DarkUi()
	{
	}

	// ---------------------------------------------------------------- buttons

	/** Strips the look-and-feel off a button: no focus ring, no rollover, no filled area. */
	private static <T extends JButton> T plain(T button)
	{
		button.setUI(new BasicButtonUI());
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setRolloverEnabled(false);
		return button;
	}

	/** Turns the button's foreground to {@code hover} while the mouse is over it, and back to {@code idle} after. */
	private static void tint(JButton button, Color hover, Supplier<Color> idle)
	{
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setForeground(hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setForeground(idle.get());
			}
		});
	}

	static JButton button(String text)
	{
		final JButton button = plain(new JButton(text));
		button.setOpaque(true);
		button.setBackground(BUTTON);
		button.setForeground(TEXT_IDLE);
		button.setFont(FontManager.getRunescapeSmallFont());
		final Border idle = new CompoundBorder(new LineBorder(LINE), new EmptyBorder(2, 8, 2, 8));
		final Border lit = new CompoundBorder(new LineBorder(LINE_LIT), new EmptyBorder(2, 8, 2, 8));
		button.setBorder(idle);
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(BUTTON_HOVER);
				button.setForeground(TEXT_BRIGHT);
				button.setBorder(lit);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(BUTTON);
				button.setForeground(TEXT_IDLE);
				button.setBorder(idle);
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				button.setBackground(BUTTON_PRESSED);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				button.setBackground(button.contains(e.getPoint()) ? BUTTON_HOVER : BUTTON);
			}
		});
		return button;
	}

	/** A button that is just its icon. The icon is drawn in the foreground color: dim, then bright on hover. */
	static JButton iconButton(Icon icon)
	{
		return iconButton(icon, TEXT_BRIGHT);
	}

	static JButton iconButton(Icon icon, Color hover)
	{
		return iconButton(icon, hover, () -> TEXT_DIM);
	}

	/** {@code idle} is asked for each time the mouse leaves, so the resting color can change. */
	static JButton iconButton(Icon icon, Color hover, Supplier<Color> idle)
	{
		final JButton button = plain(new JButton(icon));
		button.setOpaque(false);
		button.setBorderPainted(false);
		button.setForeground(idle.get());
		button.setBorder(new EmptyBorder(0, 4, 0, 4));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tint(button, hover, idle);
		return button;
	}

	/** A row with a checkbox on its left. Clicking anywhere on the row flips it and reports the new state. */
	static JButton checkRow(String text, boolean selected, Consumer<Boolean> onChange)
	{
		final boolean[] on = {selected};
		final JButton row = plain(new JButton(text, checkIcon(selected))
		{
			@Override
			public Dimension getMinimumSize()
			{
				return new Dimension(0, super.getPreferredSize().height);
			}

			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
			}
		});
		row.setOpaque(false);
		row.setBorderPainted(false);
		row.setHorizontalAlignment(SwingConstants.LEFT);
		row.setIconTextGap(6);
		row.setFont(FontManager.getRunescapeSmallFont());
		row.setForeground(TEXT_IDLE);
		row.setBorder(new EmptyBorder(2, 2, 2, 2));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tint(row, TEXT_BRIGHT, () -> TEXT_IDLE);
		row.addActionListener(e ->
		{
			on[0] = !on[0];
			row.setIcon(checkIcon(on[0]));
			onChange.accept(on[0]);
		});
		return row;
	}

	// ------------------------------------------------------------------ hints

	/**
	 * Shows a small dark hint above the mouse pointer after the mouse has rested on the component for 0.6 seconds.
	 * (A per-component delay, so it doesn't change the tooltip behavior of the rest of RuneLite.)
	 */
	static void hoverHint(JComponent component, String text)
	{
		hoverHint(component, text, () -> true);
	}

	/** Like {@link #hoverHint(JComponent, String)}, but only shows when the condition holds at that moment. */
	static void hoverHint(JComponent component, String text, Supplier<Boolean> wanted)
	{
		final JLabel label = new JLabel(text);
		label.setOpaque(true);
		label.setBackground(MENU);
		label.setForeground(TEXT);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(2, 6, 2, 6)));

		final Popup[] shown = new Popup[1];
		final Runnable hide = () ->
		{
			if (shown[0] != null)
			{
				shown[0].hide();
				shown[0] = null;
			}
		};

		final Timer delay = new Timer(600, e ->
		{
			if (!component.isShowing() || shown[0] != null || !wanted.get())
			{
				return;
			}

			final PointerInfo pointer = MouseInfo.getPointerInfo();
			if (pointer == null)
			{
				return;
			}

			// Centered above the mouse pointer, kept on the screen
			final Point at = pointer.getLocation();
			final Dimension size = label.getPreferredSize();
			final Rectangle screen = component.getGraphicsConfiguration().getBounds();
			final int x = Math.max(screen.x, Math.min(at.x - size.width / 2, screen.x + screen.width - size.width));
			final int y = at.y - size.height - 8 < screen.y ? at.y + 24 : at.y - size.height - 8;

			shown[0] = PopupFactory.getSharedInstance().getPopup(component, label, x, y);
			shown[0].show();
		});
		delay.setRepeats(false);

		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				delay.restart();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				delay.stop();
				hide.run();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				delay.stop();
				hide.run();
			}
		});

		// The list is rebuilt now and then; don't leave a hint behind for a button that is gone
		component.addHierarchyListener(e ->
		{
			if (!component.isShowing())
			{
				delay.stop();
				hide.run();
			}
		});
	}

	// ------------------------------------------------------------------ icons

	/** Draws an icon. {@code c} is the component it is painted on, for its foreground color. */
	private interface IconPainter
	{
		void paint(Graphics2D g2, Component c);
	}

	/**
	 * A size by size icon. The painter gets antialiased graphics, already moved to the icon's corner, with a 1px
	 * round stroke in the component's foreground color.
	 */
	private static Icon icon(int size, IconPainter painter)
	{
		return new Icon()
		{
			@Override
			public int getIconWidth()
			{
				return size;
			}

			@Override
			public int getIconHeight()
			{
				return size;
			}

			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.setColor(c.getForeground());
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.translate(x, y);
				painter.paint(g2, c);
				g2.dispose();
			}
		};
	}

	/** Straight lines through the given x, y pairs, optionally closed back to the start. */
	private static Path2D path(boolean closed, double... xy)
	{
		final Path2D.Float path = new Path2D.Float();
		path.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2)
		{
			path.lineTo(xy[i], xy[i + 1]);
		}
		if (closed)
		{
			path.closePath();
		}
		return path;
	}

	/** Several outlines as one path, so strokes that overlap don't draw twice as dark where they meet. */
	private static Path2D join(Path2D... parts)
	{
		final Path2D.Float joined = new Path2D.Float();
		for (Path2D part : parts)
		{
			joined.append(part, false);
		}
		return joined;
	}

	private static boolean isDim(Component c)
	{
		return TEXT_DIM.equals(c.getForeground());
	}

	private static AlphaComposite fadeWhenDim(Component c)
	{
		return AlphaComposite.SrcOver.derive(isDim(c) ? 0.65f : 1f);
	}

	/** The same icon drawn as a size by size square. */
	static Icon scaled(Icon source, int size)
	{
		return icon(size, (g2, c) ->
		{
			g2.scale((double) size / source.getIconWidth(), (double) size / source.getIconHeight());
			source.paintIcon(c, g2, 0, 0);
		});
	}

	/** An octothorpe (#), for the quantity condition. */
	static Icon hashIcon()
	{
		return icon(14, (g2, c) ->
		{
			g2.draw(path(false, 5.5, 1.5, 4, 12.5));
			g2.draw(path(false, 10.5, 1.5, 9, 12.5));
			g2.draw(path(false, 2, 5, 12.5, 5));
			g2.draw(path(false, 1.5, 9, 12, 9));
		});
	}

	/** A hand holding up a stack of coins, like the Grand Exchange logo without its circle. */
	static Icon geIcon()
	{
		return icon(14, (g2, c) ->
		{
			g2.draw(new Rectangle2D.Float(5f, 1f, 5f, 8.5f));
			g2.draw(path(false, 5, 3.8, 10, 3.8));
			g2.draw(path(false, 5, 6.6, 10, 6.6));
			g2.draw(path(true, 1, 11.2, 3.6, 9.3, 8.5, 9.3, 10.2, 10.2, 12.8, 10.4, 12, 12.2, 4, 12.8));
		});
	}

	/** A flask above a row of coins, like the High Level Alchemy icon, for the high alchemy condition. */
	static Icon haIcon()
	{
		return icon(14, (g2, c) ->
		{
			g2.draw(join(
				path(false, 5.7, 0.8, 8.3, 0.8),
				path(false, 6.2, 0.8, 6.2, 4.2, 3.4, 9),
				path(false, 7.8, 0.8, 7.8, 4.2, 10.6, 9)));
			g2.draw(new Ellipse2D.Float(0.7f, 9f, 3.9f, 3.9f));
			g2.draw(new Ellipse2D.Float(5.1f, 9f, 3.9f, 3.9f));
			g2.draw(new Ellipse2D.Float(9.5f, 9f, 3.9f, 3.9f));
		});
	}

	/** Stacked layers, for the stackable condition. */
	static Icon stackIcon()
	{
		return icon(14, (g2, c) ->
		{
			g2.draw(path(true, 7, 1.5, 12.8, 4.6, 7, 7.7, 1.2, 4.6));
			g2.draw(path(false, 1.2, 7.2, 7, 10.3, 12.8, 7.2));
			g2.draw(path(false, 1.2, 9.8, 7, 12.9, 12.8, 9.8));
		});
	}

	/** A small rounded square filled with the given color, outlined in the foreground color. */
	static Icon swatchIcon(int rgb)
	{
		return icon(14, (g2, c) ->
		{
			final RoundRectangle2D.Float square = new RoundRectangle2D.Float(1.5f, 1.5f, 11f, 11f, 3f, 3f);
			g2.setComposite(fadeWhenDim(c));
			g2.setColor(new Color(rgb & 0xFFFFFF));
			g2.fill(square);
			g2.setColor(c.getForeground());
			g2.draw(square);
		});
	}

	/** Shows an image (a rank icon) as a button icon. */
	static Icon imageIcon(BufferedImage image)
	{
		return icon(14, (g2, c) ->
		{
			g2.setComposite(fadeWhenDim(c));
			g2.drawImage(image, (14 - image.getWidth()) / 2, (14 - image.getHeight()) / 2, null);
		});
	}

	/** A framed picture with a sun and mountains. */
	static Icon pictureIcon()
	{
		return icon(14, (g2, c) ->
		{
			g2.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 11f, 9f, 2f, 2f));
			g2.draw(new Ellipse2D.Float(4f, 4.5f, 2f, 2f));
			g2.draw(path(false, 2.5, 10, 5.5, 7.5, 7.5, 9.2, 9, 8, 11.5, 10));
		});
	}

	/** An arrow leaving a tray (export), or entering one (import). */
	static Icon transferIcon(boolean export)
	{
		return icon(12, (g2, c) ->
		{
			final double tip = export ? 1 : 8;
			final double tail = export ? 8 : 1;
			final double wing = export ? 3.8 : 5.2;
			g2.draw(path(false, 1.5, 8, 1.5, 11, 10.5, 11, 10.5, 8));
			g2.draw(join(path(false, 6, tail, 6, tip), path(false, 3.2, wing, 6, tip, 8.8, wing)));
		});
	}

	/** A small box, with a tick in it when checked. */
	static Icon checkIcon(boolean checked)
	{
		return icon(12, (g2, c) ->
		{
			g2.draw(new Rectangle2D.Float(0.5f, 0.5f, 11, 11));
			if (checked)
			{
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(path(false, 3, 6.3, 5.2, 8.5, 9, 3.6));
			}
		});
	}

	/** A plus sign, or an x when cross is true. */
	static Icon symbolIcon(boolean cross)
	{
		return icon(12, (g2, c) ->
		{
			g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.translate(6, 6);
			if (cross)
			{
				g2.rotate(Math.PI / 4);
			}
			g2.drawLine(-4, 0, 4, 0);
			g2.drawLine(0, -4, 0, 4);
		});
	}

	/** A small triangle: pointing down when expanded, right when collapsed. */
	static Icon arrowIcon(boolean expanded)
	{
		return icon(10, (g2, c) ->
		{
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
			g2.fillPolygon(expanded
				? new Polygon(new int[]{0, 8, 4}, new int[]{2, 2, 7}, 3)
				: new Polygon(new int[]{2, 2, 7}, new int[]{0, 8, 4}, 3));
		});
	}

	// ----------------------------------------------------------------- fields

	/** The smaller font used inside the condition editor rows. */
	static Font editorFont()
	{
		return FontManager.getRunescapeSmallFont().deriveFont(12f);
	}

	/** Lets a text field take only digits, commas, periods and the letters k, m and b. Anything else is dropped. */
	static void limitToAmounts(JTextField field)
	{
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
				throws BadLocationException
			{
				super.insertString(fb, offset, onlyAmountCharacters(text), attrs);
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
				throws BadLocationException
			{
				super.replace(fb, offset, length, onlyAmountCharacters(text), attrs);
			}
		});
	}

	private static String onlyAmountCharacters(String text)
	{
		if (text == null)
		{
			return null;
		}

		final StringBuilder kept = new StringBuilder();
		for (char c : text.toCharArray())
		{
			final char lower = Character.toLowerCase(c);
			if ((c >= '0' && c <= '9') || c == ',' || c == '.' || lower == 'k' || lower == 'm' || lower == 'b')
			{
				kept.append(lower);
			}
		}
		return kept.toString();
	}

	static JTextField textField()
	{
		final JTextField field = new JTextField();
		field.setUI(new BasicTextFieldUI());
		field.setBackground(FIELD);
		field.setForeground(TEXT_BRIGHT);
		field.setCaretColor(TEXT_BRIGHT);
		field.setSelectionColor(new Color(90, 90, 90));
		field.setSelectedTextColor(Color.WHITE);
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(3, 5, 3, 5)));
		return field;
	}

	// ------------------------------------------------------------------ menus

	static JPopupMenu popupMenu()
	{
		final JPopupMenu menu = new JPopupMenu();
		menu.setUI(new BasicPopupMenuUI());
		menu.setOpaque(true);
		menu.setBackground(MENU);
		menu.setBorder(new LineBorder(LINE));
		return menu;
	}

	/**
	 * A row in a popup menu. It is a plain label rather than a JMenuItem, so clicking it doesn't close the popup
	 * by itself, which lets a row turn into a "SURE?" confirmation.
	 */
	static final class MenuRow extends JLabel
	{
		static final Color DANGER = new Color(214, 74, 74);

		private final Runnable onClick;
		private boolean danger;

		MenuRow(String text, Runnable onClick)
		{
			super(text);
			this.onClick = onClick;

			setOpaque(true);
			setBackground(MENU);
			setForeground(TEXT_IDLE);
			setFont(FontManager.getRunescapeSmallFont());
			setBorder(new EmptyBorder(4, 10, 4, 10));
			setHorizontalAlignment(SwingConstants.CENTER);
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(BUTTON);
					setForeground(danger ? DANGER : TEXT_BRIGHT);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(MENU);
					setForeground(danger ? DANGER : TEXT_IDLE);
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					MenuRow.this.onClick.run();
				}
			});
		}

		void setDanger(boolean danger)
		{
			this.danger = danger;
			setForeground(danger ? DANGER : TEXT_IDLE);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** A card: a dark box with a thin outline, laid out with a BorderLayout. */
	static JPanel card()
	{
		final JPanel card = new JPanel(new BorderLayout());
		card.setBackground(CARD);
		card.setBorder(new CompoundBorder(new LineBorder(new Color(52, 52, 52)), new EmptyBorder(6, 6, 6, 6)));
		return card;
	}

	/** Runs the action when Escape is pressed in the component. */
	static void onEscape(JComponent component, Runnable action)
	{
		component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "lootLabels.cancel");
		component.getActionMap().put("lootLabels.cancel", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				action.run();
			}
		});
	}

	/** A thin gray scrollbar without arrow buttons. */
	static void styleScrollBar(JScrollBar bar)
	{
		bar.setPreferredSize(new Dimension(8, 0));
		bar.setUnitIncrement(16);
		bar.setBackground(PANEL);
		bar.setUI(new BasicScrollBarUI()
		{
			@Override
			protected void configureScrollBarColors()
			{
				super.configureScrollBarColors();
				final Color thumb = new Color(74, 74, 74);
				thumbColor = thumb;
				thumbHighlightColor = thumb;
				thumbDarkShadowColor = thumb;
				thumbLightShadowColor = thumb;
				trackColor = PANEL;
				trackHighlightColor = PANEL;
			}

			@Override
			protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
			{
				// Nothing to scroll: leave the (invisible) track empty
				if (scrollbar.getVisibleAmount() >= scrollbar.getMaximum() - scrollbar.getMinimum())
				{
					return;
				}
				super.paintThumb(g, c, thumbBounds);
			}

			@Override
			protected JButton createDecreaseButton(int orientation)
			{
				return emptyButton();
			}

			@Override
			protected JButton createIncreaseButton(int orientation)
			{
				return emptyButton();
			}

			private JButton emptyButton()
			{
				final JButton button = new JButton();
				button.setPreferredSize(new Dimension(0, 0));
				button.setMinimumSize(new Dimension(0, 0));
				button.setMaximumSize(new Dimension(0, 0));
				return button;
			}
		});
	}
}
