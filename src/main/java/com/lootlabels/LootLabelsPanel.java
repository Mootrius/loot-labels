package com.lootlabels;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

/**
 * The sidebar: a list of collapsible group cards. Everything is drawn in grays, and all editing (create,
 * rename) happens inline, so there are no dialogs. The only popup is the small dark edit menu.
 */
@Singleton
class LootLabelsPanel extends PluginPanel
{
	private static final int ROW_HEIGHT = 20;
	private static final int GROUP_GAP = 3; // space between group cards
	private static final int ITEM_ROW_HEIGHT = 18;
	private static final int CONDITION_ROW_HEIGHT = 16;
	private static final int CONDITION_FIELD_WIDTH = 72;
	private static final int CONDITION_ICON_SIZE = 12; // the four condition icons on an item's row
	private static final int ICON_COLUMNS = 8; // rank icons per row in the icon picker
	private static final int ICON_CELL = 24; // size of one icon in the picker

	private final LabelGroupManager groups;
	private final ColorPickerManager colorPickerManager;
	private final JPanel list = new JPanel();

	private boolean creating;

	// The export box (groups ticked to export) and the import box (what was found on the clipboard, what is ticked)
	private boolean exporting;
	private final Set<LabelGroup> exportPicks = new HashSet<>();
	private boolean importing;
	private List<LabelGroup> importFound = new ArrayList<>();
	private final Set<LabelGroup> importPicks = new HashSet<>();
	private String importError;
	private LabelGroup renaming;
	private LabelGroup renamingItemGroup;
	private ItemEntry renamingEntry;
	private final List<JComponent> groupCards = new ArrayList<>();

	// When the icon menu last closed with the mouse over the icon button, so that click closes it rather than reopening it
	private long iconMenuClosedOverButton;

	// Saves what is typed in the open rule editor, if there is one (clicking a rule icon doesn't take focus from it)
	private Runnable commitEditor;

	// Groups whose "add an item" field is showing
	private final Set<LabelGroup> addOpen = new HashSet<>();

	// The rename field that is open (if any), how to save it, and the listener that saves it when you click elsewhere
	private JTextField activeEditor;
	private Runnable activeEditorSave;
	private AWTEventListener outsideClick;
	private boolean rebuildAfterRelease;
	private JTextField focusField;

	// The item (and condition) whose editor row is open under its row, if any
	private LabelGroup editGroup;
	private ItemEntry editEntry;
	private ItemConditions.Type editType;

	@Inject
	LootLabelsPanel(LabelGroupManager groups, ColorPickerManager colorPickerManager)
	{
		super(false);
		this.groups = groups;
		this.colorPickerManager = colorPickerManager;

		setLayout(new BorderLayout());
		setBorder(null);
		setBackground(DarkUi.PANEL);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		final JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setOpaque(false);
		listWrapper.add(list, BorderLayout.NORTH);

		final TrackingPanel content = new TrackingPanel();
		content.setLayout(new BorderLayout(0, 8));
		content.setBackground(DarkUi.PANEL);
		content.setBorder(new EmptyBorder(8, 8, 8, 8));
		content.add(buildHeader(), BorderLayout.NORTH);
		content.add(listWrapper, BorderLayout.CENTER);

		// The scrollbar's space is always kept (the track matches the panel, and the thumb only shows when there
		// is something to scroll), so nothing shifts sideways when it appears or goes away
		final JScrollPane scroll = new JScrollPane(content,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setViewportBorder(null);
		scroll.setOpaque(true);
		scroll.setBackground(DarkUi.PANEL);
		scroll.getViewport().setOpaque(true);
		scroll.getViewport().setBackground(DarkUi.PANEL);
		DarkUi.styleScrollBar(scroll.getVerticalScrollBar());
		add(scroll, BorderLayout.CENTER);
	}

	private JComponent buildHeader()
	{
		final JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);

		final JLabel title = new JLabel("LOOT LABELS");
		title.setForeground(DarkUi.TEXT_BRIGHT);
		title.setFont(FontManager.getRunescapeBoldFont());
		header.add(title, BorderLayout.CENTER);

		final JButton export = DarkUi.iconButton(DarkUi.transferIcon(true));
		DarkUi.hoverHint(export, "Export groups");
		export.addActionListener(e -> startExport());

		final JButton importButton = DarkUi.iconButton(DarkUi.transferIcon(false));
		DarkUi.hoverHint(importButton, "Import groups");
		importButton.addActionListener(e -> startImport());

		final JButton add = DarkUi.button("+ GROUP");
		add.addActionListener(e ->
		{
			// Pressing it again while the create box is open closes it, like CANCEL
			if (creating)
			{
				creating = false;
				rebuild();
				return;
			}
			creating = true;
			exporting = false;
			importing = false;
			renaming = null;
			rebuild();
		});

		final JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
		actions.setOpaque(false);
		actions.add(export);
		actions.add(importButton);
		actions.add(Box.createHorizontalStrut(2));
		actions.add(add);
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private void startExport()
	{
		// Clicking the button again while the box is open closes it
		if (exporting)
		{
			exporting = false;
			rebuild();
			return;
		}
		creating = false;
		importing = false;
		renaming = null;
		exporting = true;
		exportPicks.clear();
		exportPicks.addAll(groups.getGroups());
		rebuild();
	}

	private void startImport()
	{
		if (importing)
		{
			importing = false;
			rebuild();
			return;
		}
		creating = false;
		exporting = false;
		renaming = null;
		importing = true;
		importFound = new ArrayList<>();
		importPicks.clear();
		importError = null;

		final String text = TransferCard.readClipboard();
		if (text == null || text.trim().isEmpty())
		{
			importError = "Your clipboard has no text. Copy a Loot Labels export first.";
		}
		else
		{
			final List<LabelGroup> found = groups.parseImport(text);
			if (found.isEmpty())
			{
				importError = "No Loot Labels groups were found in your clipboard.";
			}
			else
			{
				importFound = found;
				importPicks.addAll(found);
			}
		}
		rebuild();
	}

	private JComponent buildExportCard()
	{
		final List<LabelGroup> all = groups.getGroups();
		return TransferCard.build("EXPORT GROUPS", all, exportPicks,
			all.isEmpty() ? "There are no groups to export." : null, "EXPORT", "COPIED",
			() ->
			{
				final List<LabelGroup> chosen = TransferCard.chosen(all, exportPicks);
				return !chosen.isEmpty() && TransferCard.copyToClipboard(groups.exportJson(chosen));
			},
			() ->
			{
				exporting = false;
				rebuild();
			});
	}

	private JComponent buildImportCard()
	{
		return TransferCard.build("IMPORT GROUPS", importFound, importPicks, importError, "IMPORT", "IMPORTED",
			() ->
			{
				final List<LabelGroup> chosen = TransferCard.chosen(importFound, importPicks);
				if (chosen.isEmpty())
				{
					return false;
				}
				groups.importGroups(chosen);
				return true;
			},
			() ->
			{
				importing = false;
				rebuild();
			});
	}

	/** Rebuilds the whole list from the saved groups. Call on the Swing thread. */
	void rebuild()
	{
		focusField = null;
		activeEditor = null;
		activeEditorSave = null;
		commitEditor = null;
		stopWatchingClicks();
		list.removeAll();
		groupCards.clear();

		if (creating)
		{
			list.add(buildCreateRow());
			list.add(Box.createVerticalStrut(GROUP_GAP));
		}

		if (exporting || importing)
		{
			final JComponent transfer = exporting ? buildExportCard() : buildImportCard();
			transfer.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(transfer);
			list.add(Box.createVerticalStrut(GROUP_GAP));
		}

		final List<LabelGroup> all = groups.getGroups();
		if (all.isEmpty() && !creating && !exporting && !importing)
		{
			final JLabel hint = new JLabel("No groups yet");
			hint.setForeground(DarkUi.TEXT_DIM);
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(hint);
		}

		for (LabelGroup group : all)
		{
			final JComponent card = buildCard(group, groupCards.size());
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			groupCards.add(card);
			list.add(card);
			list.add(Box.createVerticalStrut(GROUP_GAP));
		}

		list.revalidate();
		list.repaint();

		if (activeEditor != null)
		{
			watchClicks(activeEditor, activeEditorSave);
		}

		if (focusField != null)
		{
			final JTextField field = focusField;
			SwingUtilities.invokeLater(field::requestFocusInWindow);
		}
	}

	/**
	 * While a rename field is open, a click anywhere else saves it and closes it. (Clicking something that can't take
	 * the keyboard focus wouldn't take the focus out of the field, so this watches the clicks themselves.) The list
	 * is rebuilt after the mouse is released, so the click lands on the widget the user pressed rather than on one
	 * that was replaced halfway through it.
	 */
	private void watchClicks(JTextField editor, Runnable save)
	{
		outsideClick = event ->
		{
			if (!(event instanceof MouseEvent))
			{
				return;
			}

			final MouseEvent e = (MouseEvent) event;
			if (e.getID() == MouseEvent.MOUSE_PRESSED)
			{
				final Component source = e.getComponent();
				if (source == editor || rebuildAfterRelease)
				{
					return;
				}
				save.run();
				rebuildAfterRelease = true;
			}
			else if (e.getID() == MouseEvent.MOUSE_RELEASED && rebuildAfterRelease)
			{
				rebuildAfterRelease = false;
				SwingUtilities.invokeLater(this::rebuild);
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(outsideClick, AWTEvent.MOUSE_EVENT_MASK);
	}

	private void stopWatchingClicks()
	{
		if (outsideClick != null)
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClick);
			outsideClick = null;
		}
		rebuildAfterRelease = false;
	}

	private JComponent buildCreateRow()
	{
		final JPanel card = DarkUi.card();
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JTextField field = DarkUi.textField();
		final Runnable create = () ->
		{
			final String name = field.getText().trim();
			if (name.isEmpty())
			{
				return;
			}
			groups.addGroup(name);
			creating = false;
			rebuild();
		};
		final Runnable cancel = () ->
		{
			creating = false;
			rebuild();
		};
		field.addActionListener(e -> create.run());
		DarkUi.onEscape(field, cancel);
		focusField = field;

		card.add(field, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(6, 0, 0, 0));
		final JButton ok = DarkUi.button("CREATE");
		ok.addActionListener(e -> create.run());
		final JButton no = DarkUi.button("CANCEL");
		no.addActionListener(e -> cancel.run());
		buttons.add(ok);
		buttons.add(no);
		card.add(buttons, BorderLayout.SOUTH);
		return card;
	}

	private JComponent buildCard(LabelGroup group, int groupIndex)
	{
		final JPanel card = DarkUi.card();
		final boolean expanded = !group.isCollapsed();

		// Header: arrow, name, icon and color buttons, on/off switch
		// Always the same height, so the card doesn't resize while renaming
		final JPanel header = fixedRow(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		final JLabel arrow = new JLabel(DarkUi.arrowIcon(expanded));
		arrow.setForeground(DarkUi.TEXT_DIM);
		header.add(arrow, BorderLayout.WEST);

		final JPanel titles = new JPanel(new BorderLayout(6, 0));
		titles.setOpaque(false);
		JLabel nameLabel = null;
		if (renaming == group)
		{
			final JTextField field = DarkUi.textField();
			field.setBorder(new CompoundBorder(new LineBorder(DarkUi.LINE), new EmptyBorder(0, 4, 0, 4)));
			field.setText(group.getName());
			field.selectAll();
			final Runnable save = () ->
			{
				if (renaming != group)
				{
					return;
				}
				final String name = field.getText().trim();
				if (!name.isEmpty())
				{
					groups.renameGroup(group, name);
				}
				renaming = null;
			};
			field.addActionListener(e ->
			{
				save.run();
				rebuild();
			});
			DarkUi.onEscape(field, () ->
			{
				renaming = null;
				rebuild();
			});
			activeEditor = field;
			activeEditorSave = save;
			focusField = field;
			titles.add(field, BorderLayout.CENTER);
		}
		else
		{
			final JLabel name = new JLabel(group.getName());
			name.setForeground(group.isEnabled() ? DarkUi.TEXT_BRIGHT : DarkUi.TEXT_DIM);
			name.setFont(FontManager.getRunescapeSmallFont());
			// Only the words are clickable for renaming; the empty space next to them folds the group
			titles.add(name, BorderLayout.WEST);
			nameLabel = name;
		}
		header.add(titles, BorderLayout.CENTER);

		final ToggleSwitch toggle = new ToggleSwitch(group.isEnabled(), on ->
		{
			groups.setEnabled(group, on);
			rebuild();
		});
		final JPanel toggleWrap = new JPanel(new GridBagLayout());
		toggleWrap.setOpaque(false);
		toggleWrap.add(toggle);

		// The picture button picks the icon drawn left of the text in this group's labels, and the color swatch
		// picks the color of their box. The picture button shows the chosen icon once there is one; the swatch always
		// shows the current color (black until it is changed).
		final BufferedImage chosenIcon = group.hasChosenIcon() ? RankIcons.image(group.getIcon()) : null;
		final JButton picture = DarkUi.iconButton(
			chosenIcon == null ? DarkUi.pictureIcon() : DarkUi.imageIcon(chosenIcon));
		picture.addActionListener(e ->
		{
			// A click on the button while the menu is open already closed it (a click outside a popup does); don't reopen
			if (System.currentTimeMillis() - iconMenuClosedOverButton < 1000)
			{
				iconMenuClosedOverButton = 0;
				return;
			}
			showIconPicker(group, picture);
		});
		final JButton brush = DarkUi.iconButton(
			DarkUi.swatchIcon(group.getColor()));
		brush.addActionListener(e -> pickColor(group, brush));

		final JPanel buttons = new JPanel();
		buttons.setOpaque(false);
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		buttons.add(centered(picture));
		buttons.add(centered(brush));
		buttons.add(Box.createHorizontalStrut(2));
		buttons.add(toggleWrap);
		header.add(buttons, BorderLayout.EAST);

		// The arrow brightens while the header is hovered
		final MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				arrow.setForeground(DarkUi.TEXT_BRIGHT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				arrow.setForeground(DarkUi.TEXT_DIM);
			}
		};
		header.addMouseListener(hover);
		arrow.addMouseListener(hover);
		titles.addMouseListener(hover);

		// The header is the handle for dragging the group to a new place. A click on the name renames the group,
		// a click anywhere else on the header folds it, and a right click opens the menu.
		if (renaming != group)
		{
			final JLabel clickableName = nameLabel;
			final DragReorder drag = new DragReorder(list, groupCards, groupIndex,
				newIndex -> {
					groups.moveGroup(group, newIndex);
					rebuild();
				},
				e ->
				{
					if (e.getComponent() == clickableName)
					{
						renaming = group;
						renamingItemGroup = null;
						renamingEntry = null;
					}
					else
					{
						groups.setCollapsed(group, !group.isCollapsed());
						if (group.isCollapsed())
						{
							addOpen.remove(group); // the add field closes with the group
							if (editGroup == group)
							{
								// So does an open rule editor, after saving what was typed into it
								if (commitEditor != null)
								{
									commitEditor.run();
								}
								editGroup = null;
								editEntry = null;
								editType = null;
							}
						}
					}
					rebuild();
				},
				e -> showEditMenu(group, e.getComponent(), e.getX(), e.getY()));
			drag.attach(header, arrow, titles);
			if (nameLabel != null)
			{
				drag.attach(nameLabel);
			}
		}

		card.add(header, BorderLayout.NORTH);

		if (expanded)
		{
			card.add(buildBody(group), BorderLayout.CENTER);
		}
		return card;
	}

	private static JComponent centered(JComponent component)
	{
		final JPanel wrap = new JPanel(new GridBagLayout());
		wrap.setOpaque(false);
		wrap.add(component);
		return wrap;
	}

	private void showIconPicker(LabelGroup group, Component anchor)
	{
		final JPopupMenu menu = DarkUi.popupMenu();

		final DarkUi.MenuRow none = new DarkUi.MenuRow("NO ICON", () ->
		{
			menu.setVisible(false);
			groups.setIcon(group, "");
			rebuild();
		});
		menu.add(none);

		final JPanel grid = new JPanel(new GridLayout(0, ICON_COLUMNS, 0, 0));
		grid.setBackground(DarkUi.MENU);
		for (String id : RankIcons.IDS)
		{
			grid.add(new IconCell(id, id.equals(group.getIcon()), () ->
			{
				menu.setVisible(false);
				groups.setIcon(group, id);
				rebuild();
			}));
		}

		final JScrollPane scroll = new JScrollPane(grid,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setViewportBorder(null);
		scroll.setOpaque(true);
		scroll.setBackground(DarkUi.MENU);
		scroll.getViewport().setOpaque(true);
		scroll.getViewport().setBackground(DarkUi.MENU);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setPreferredSize(new Dimension(ICON_COLUMNS * ICON_CELL + 8, ICON_CELL * 8));
		DarkUi.styleScrollBar(scroll.getVerticalScrollBar());
		menu.add(scroll);

		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
				final java.awt.PointerInfo pointer = java.awt.MouseInfo.getPointerInfo();
				if (pointer != null && anchor.isShowing()
					&& new Rectangle(anchor.getLocationOnScreen(), anchor.getSize()).contains(pointer.getLocation()))
				{
					iconMenuClosedOverButton = System.currentTimeMillis();
				}
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
			}
		});

		// Centered across the sidebar, just under the button
		final int x = Math.max(0, (getWidth() - menu.getPreferredSize().width) / 2);
		final int y = SwingUtilities.convertPoint(anchor, 0, anchor.getHeight(), this).y;
		menu.show(this, x, y);
	}

	private void pickColor(LabelGroup group, Component anchor)
	{
		// No alpha slider: the fill's transparency is fixed, only the color is chosen
		final RuneliteColorPicker picker = colorPickerManager.create(anchor, new Color(group.getColor()),
			group.getName() + " color", true);
		final int before = group.getColor();
		final boolean[] touched = new boolean[1];
		picker.setOnColorChange(color ->
		{
			// Labels follow the picker as it moves; the choice is saved when it is closed
			touched[0] = true;
			groups.previewColor(group, color.getRGB());
		});
		picker.setOnClose(color ->
		{
			groups.setColor(group, color.getRGB(), touched[0] || (color.getRGB() & 0xFFFFFF) != before);
			rebuild();
		});
		placeOverSidebar(picker, anchor);
		picker.setVisible(true);
	}

	/**
	 * Puts the picker's right edge on the sidebar's right edge, so most of it covers the sidebar, level with the
	 * button that was clicked and kept inside the RuneLite window.
	 */
	private void placeOverSidebar(RuneliteColorPicker picker, Component anchor)
	{
		if (!isShowing() || !anchor.isShowing())
		{
			picker.setLocationRelativeTo(picker.getOwner());
			return;
		}

		final java.awt.Point sidebar = getLocationOnScreen();
		final java.awt.Point button = anchor.getLocationOnScreen();
		final Window owner = picker.getOwner();

		int x = sidebar.x + getWidth() - picker.getWidth();
		int y = button.y - picker.getHeight() / 2;
		if (owner != null)
		{
			final Rectangle bounds = owner.getBounds();
			x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - picker.getWidth()));
			y = Math.max(bounds.y, Math.min(y, bounds.y + bounds.height - picker.getHeight()));
		}
		picker.setLocation(x, y);
	}

	private void showEditMenu(LabelGroup group, Component anchor, int x, int y)
	{
		final JPopupMenu menu = DarkUi.popupMenu();

		// DELETE asks "SURE?" in the same spot; the second press does it
		final DarkUi.MenuRow[] armed = new DarkUi.MenuRow[1];
		menu.add(confirmRow(menu, "DELETE", armed, () -> groups.removeGroup(group)));

		// Moving the mouse off the menu closes it. The pointer is checked on a short timer, because a popup doesn't
		// reliably report the mouse leaving it.
		final javax.swing.Timer watcher = new javax.swing.Timer(40, e ->
		{
			if (!menu.isShowing())
			{
				return;
			}
			final java.awt.PointerInfo pointer = java.awt.MouseInfo.getPointerInfo();
			if (pointer == null)
			{
				return;
			}
			final Rectangle bounds = new Rectangle(menu.getLocationOnScreen(), menu.getSize());
			bounds.grow(3, 3);
			if (!bounds.contains(pointer.getLocation()))
			{
				menu.setVisible(false);
			}
		});
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				watcher.start();
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
				watcher.stop();
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
				watcher.stop();
			}
		});

		menu.show(anchor, x, y);
	}

	private DarkUi.MenuRow confirmRow(JPopupMenu menu, String label, DarkUi.MenuRow[] armed, Runnable action)
	{
		final DarkUi.MenuRow[] self = new DarkUi.MenuRow[1];
		self[0] = new DarkUi.MenuRow(label, () ->
		{
			if (armed[0] == self[0])
			{
				menu.setVisible(false);
				action.run();
				rebuild();
				return;
			}

			if (armed[0] != null)
			{
				armed[0].setText(armed[0].getName());
				armed[0].setDanger(false);
			}
			armed[0] = self[0];
			self[0].setText("SURE?");
			self[0].setDanger(true);
		});
		self[0].setName(label);

		// Moving off an option that is asking "SURE?" puts it back
		self[0].addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				if (armed[0] == self[0])
				{
					self[0].setText(label);
					self[0].setDanger(false);
					armed[0] = null;
				}
			}
		});
		return self[0];
	}

	private boolean isEditing(LabelGroup group, ItemEntry entry, ItemConditions.Type type)
	{
		return editType == type && editGroup == group && entry == editEntry;
	}

	/**
	 * Adds an item's row, and under it the editor for the condition that is open, if there is one. The row has the
	 * name, an icon for each condition (dim until one is set) and the remove button.
	 */
	private void addItemRows(JPanel body, LabelGroup group, ItemEntry item, List<JComponent> itemRows)
	{
		final int itemIndex = itemRows.size();
		final ItemConditions conditions = item.getConditions();

		final JPanel row = fixedRow(new BorderLayout(6, 0), ITEM_ROW_HEIGHT);
		row.setOpaque(false);

		final boolean renamingThis = renamingItemGroup == group && renamingEntry == item;
		JLabel name = null;
		if (renamingThis)
		{
			final JTextField field = DarkUi.textField();
			field.setBorder(new CompoundBorder(new LineBorder(DarkUi.LINE), new EmptyBorder(0, 5, 0, 5)));
			field.setText(item.getName());
			field.selectAll();
			final Runnable save = () ->
			{
				if (renamingItemGroup != group || renamingEntry == null)
				{
					return;
				}
				groups.renameItem(group, item, field.getText());
				renamingItemGroup = null;
				renamingEntry = null;
				editType = null;
			};
			field.addActionListener(e ->
			{
				save.run();
				rebuild();
			});
			DarkUi.onEscape(field, () ->
			{
				renamingItemGroup = null;
				renamingEntry = null;
				rebuild();
			});
			activeEditor = field;
			activeEditorSave = save;
			focusField = field;
			row.add(field, BorderLayout.CENTER);
		}
		else
		{
			name = new JLabel(item.getName());
			// Lines up with where typed text starts in the add-item field: 1px outline + 5px padding
			name.setBorder(new EmptyBorder(0, 6, 0, 0));
			name.setForeground(DarkUi.TEXT);
			name.setFont(FontManager.getRunescapeSmallFont());
			row.add(name, BorderLayout.CENTER);

			// A name too long for the row is cut off, so show all of it when it is hovered
			final JLabel shownName = name;
			DarkUi.hoverHint(shownName, item.getName(), () -> shownName.getPreferredSize().width > shownName.getWidth());
		}

		final JPanel buttons = new JPanel();
		buttons.setOpaque(false);
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		final Map<ItemConditions.Type, boolean[]> active = new EnumMap<>(ItemConditions.Type.class);
		final Map<ItemConditions.Type, JButton> conditionButtons = new EnumMap<>(ItemConditions.Type.class);
		for (ItemConditions.Type type : ItemConditions.Type.values())
		{
			final boolean[] on = {conditions != null && conditions.has(type)};
			final boolean editing = isEditing(group, item, type);
			final JButton button = DarkUi.iconButton(DarkUi.scaled(conditionIcon(type), CONDITION_ICON_SIZE), Color.WHITE,
				() -> editing ? Color.WHITE : (on[0] ? DarkUi.TEXT_BRIGHT : DarkUi.TEXT_DIM));
			DarkUi.hoverHint(button, conditionName(type));
			if (editing)
			{
				// Highlighted while its editor row is open: white icon on a gray, outlined square
				button.setOpaque(true);
				button.setBackground(DarkUi.BUTTON);
				button.setBorderPainted(true);
				button.setBorder(new CompoundBorder(new LineBorder(DarkUi.LINE_LIT), new EmptyBorder(0, 2, 0, 2)));
			}
			else
			{
				button.setBorder(new EmptyBorder(0, 3, 0, 3));
			}
			button.addActionListener(e ->
			{
				// What was typed into the open editor is saved first, so clicking its icon makes the rule
				if (commitEditor != null)
				{
					commitEditor.run();
				}
				if (isEditing(group, item, type))
				{
					editType = null;
				}
				else
				{
					editGroup = group;
					editEntry = item;
					editType = type;
				}
				rebuild();
			});
			active.put(type, on);
			conditionButtons.put(type, button);
			buttons.add(centered(button));
		}

		final JButton remove = DarkUi.iconButton(DarkUi.symbolIcon(true), DarkUi.MenuRow.DANGER);
		remove.addActionListener(e ->
		{
			if (editGroup == group && editEntry == item)
			{
				editType = null;
			}
			groups.removeItem(group, item);
			rebuild();
		});
		buttons.add(centered(remove));
		row.add(buttons, BorderLayout.EAST);

		body.add(left(row));
		body.add(Box.createVerticalStrut(2));
		itemRows.add(row);

		// The row is the handle for dragging the item to a new place; a click on the name renames it
		if (!renamingThis)
		{
			final JLabel clickableName = name;
			final DragReorder drag = new DragReorder(body, itemRows, itemIndex,
				newIndex -> {
					groups.moveItem(group, item, newIndex);
					rebuild();
				},
				e ->
				{
					if (e.getComponent() == clickableName)
					{
						renamingItemGroup = group;
						renamingEntry = item;
						renaming = null;
						rebuild();
					}
				},
				null);
			drag.attach(row, name);
		}

		if (editGroup == group && editEntry == item && editType != null)
		{
			final ItemConditions.Type type = editType;
			final boolean[] on = active.get(type);
			final JButton button = conditionButtons.get(type);
			body.add(left(buildConditionEditor(group, item, type, now ->
			{
				// Update the icon in place; rebuilding here would swallow a click on whatever was pressed
				on[0] = now;
				button.setForeground(Color.WHITE); // it is highlighted while its editor is open
			})));
			body.add(Box.createVerticalStrut(2));
		}
	}

	private static String conditionName(ItemConditions.Type type)
	{
		switch (type)
		{
			case QUANTITY:
				return "Quantity";
			case GE_VALUE:
				return "GE value";
			case HA_VALUE:
				return "HA value";
			default:
				return "Stackable";
		}
	}

	private static Icon conditionIcon(ItemConditions.Type type)
	{
		switch (type)
		{
			case QUANTITY:
				return DarkUi.hashIcon();
			case GE_VALUE:
				return DarkUi.geIcon();
			case HA_VALUE:
				return DarkUi.haIcon();
			default:
				return DarkUi.stackIcon();
		}
	}

	/**
	 * The temporary row under an item for setting one of its conditions: a choice of comparison and a number for
	 * quantity and the two values, or "stackable" / "not-stackable" for stacking.
	 *
	 * @param ruleChanged told whether the item now has this condition
	 */
	private JComponent buildConditionEditor(LabelGroup group, ItemEntry item, ItemConditions.Type type,
		Consumer<Boolean> ruleChanged)
	{
		final ItemConditions conditions = item.getConditions();
		// Everything in the row is centered
		final JPanel row = fixedRow(new FlowLayout(FlowLayout.CENTER, 4, 0), CONDITION_ROW_HEIGHT);
		row.setOpaque(false);

		if (type == ItemConditions.Type.STACKABLE)
		{
			final Boolean current = conditions == null ? null : conditions.getStackable();
			final int selected = current == null ? -1 : (current ? 0 : 1);
			row.add(new SegmentedControl(new String[]{"STACKABLE", "NON-STACKABLE"}, selected, true, index ->
			{
				if (index < 0)
				{
					groups.clearCondition(item, type);
				}
				else
				{
					groups.setStackable(item, index == 0);
				}
				ruleChanged.accept(index >= 0);
			}));
			return row;
		}

		final ItemConditions.Op[] ops = ItemConditions.Op.values();
		final ItemConditions.Comparison existing = conditions == null ? null : conditions.getComparison(type);
		final ItemConditions.Op[] op = {existing == null ? ItemConditions.Op.GE : existing.getOp()};

		final JTextField field = DarkUi.textField();
		field.setBorder(new CompoundBorder(new LineBorder(DarkUi.LINE), new EmptyBorder(0, 4, 0, 4)));
		field.setFont(DarkUi.editorFont());
		field.setHorizontalAlignment(JTextField.CENTER);
		DarkUi.limitToAmounts(field);
		final Dimension fieldSize = new Dimension(CONDITION_FIELD_WIDTH, CONDITION_ROW_HEIGHT);
		field.setPreferredSize(fieldSize);
		field.setMinimumSize(fieldSize);
		field.setMaximumSize(fieldSize);
		if (existing != null)
		{
			field.setText(ItemConditions.formatAmount(existing.getValue()));
		}

		// Saves what is in the field: nothing clears the condition, a number sets it, anything else is put back
		final Runnable commit = () ->
		{
			final String text = field.getText().trim();
			if (text.isEmpty())
			{
				groups.clearCondition(item, type);
				ruleChanged.accept(false);
				return;
			}

			final OptionalLong value = ItemConditions.parseAmount(text);
			if (value.isPresent())
			{
				groups.setComparison(item, type, op[0], value.getAsLong());
				field.setText(ItemConditions.formatAmount(value.getAsLong()));
				ruleChanged.accept(true);
				return;
			}

			final ItemConditions now = item.getConditions();
			final ItemConditions.Comparison saved = now == null ? null : now.getComparison(type);
			field.setText(saved == null ? "" : ItemConditions.formatAmount(saved.getValue()));
		};

		commitEditor = commit;

		field.addActionListener(e ->
		{
			commit.run();
			editType = null;
			rebuild();
		});
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
		if (existing == null)
		{
			focusField = field;
		}

		final String[] symbols = new String[ops.length];
		int selected = 0;
		for (int i = 0; i < ops.length; i++)
		{
			symbols[i] = ops[i].symbol();
			if (ops[i] == op[0])
			{
				selected = i;
			}
		}
		row.add(new SegmentedControl(symbols, selected, false, index ->
		{
			op[0] = ops[index];
			commit.run();
		}));
		row.add(field);
		return row;
	}

	private JComponent buildBody(LabelGroup group)
	{
		final JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(8, 0, 0, 0));

		// Items
		final List<ItemEntry> items = group.getEntries();
		if (items.isEmpty())
		{
			body.add(left(dimLabel("No items yet")));
			body.add(Box.createVerticalStrut(6));
		}
		final List<JComponent> itemRows = new ArrayList<>();
		for (ItemEntry item : new ArrayList<>(items))
		{
			addItemRows(body, group, item, itemRows);
		}

		// Add an item: a centered "+ ITEM" button that turns into the add field (and back) when clicked
		body.add(Box.createVerticalStrut(3));
		// Both states use one row height, so the group doesn't resize when one replaces the other
		final int addRowHeight = Math.max(ROW_HEIGHT, DarkUi.button("+ ITEM").getPreferredSize().height);
		if (addOpen.contains(group))
		{
			final JPanel addRow = fixedRow(new BorderLayout(4, 0), addRowHeight);
			addRow.setOpaque(false);
			final JTextField field = DarkUi.textField();
			field.setBorder(new CompoundBorder(new LineBorder(DarkUi.LINE), new EmptyBorder(0, 5, 0, 5)));
			final Runnable add = () ->
			{
				if (groups.addItem(group, field.getText()))
				{
					rebuild();
				}
			};
			final Runnable close = () ->
			{
				addOpen.remove(group);
				rebuild();
			};
			field.addActionListener(e -> add.run());
			DarkUi.onEscape(field, close);
			final JButton addButton = DarkUi.iconButton(DarkUi.symbolIcon(false));
			addButton.addActionListener(e -> add.run());
			final JButton closeButton = DarkUi.iconButton(DarkUi.symbolIcon(true), DarkUi.MenuRow.DANGER);
			closeButton.addActionListener(e -> close.run());
			final JPanel buttons = new JPanel();
			buttons.setOpaque(false);
			buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
			buttons.add(centered(addButton));
			buttons.add(centered(closeButton));
			addRow.add(field, BorderLayout.CENTER);
			addRow.add(buttons, BorderLayout.EAST);
			body.add(left(addRow));
			if (focusField == null && group == lastAdded)
			{
				focusField = field;
			}
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusGained(FocusEvent e)
				{
					lastAdded = group;
				}
			});
		}
		else
		{
			final JButton addItem = DarkUi.button("+ ITEM");
			addItem.addActionListener(e ->
			{
				addOpen.add(group);
				lastAdded = group; // so the field gets the cursor when it shows
				rebuild();
			});
			// Same height as the "+ GROUP" button, centered
			final JPanel toggleRow = fixedRow(new GridBagLayout(), addRowHeight);
			toggleRow.setOpaque(false);
			toggleRow.add(addItem);
			body.add(left(toggleRow));
		}
		return body;
	}

	/** The group whose "add item" field last had focus, so typing several items in a row keeps the cursor there. */
	private LabelGroup lastAdded;

	/** A row that is always ROW_HEIGHT tall, so rows with a button, a text field or a switch line up. */
	private static JPanel fixedRow(LayoutManager layout)
	{
		return fixedRow(layout, ROW_HEIGHT);
	}

	private static JPanel fixedRow(LayoutManager layout, int height)
	{
		return new JPanel(layout)
		{
			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(super.getPreferredSize().width, height);
			}

			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, height);
			}
		};
	}

	private static JLabel dimLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(DarkUi.TEXT_DIM);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static JComponent left(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	/** One rank icon in the icon picker. Dim until hovered, outlined if it is the one in use. */
	private static final class IconCell extends JLabel
	{
		IconCell(String id, boolean current, Runnable onClick)
		{
			super(new ImageIcon(RankIcons.image(id)));
			setOpaque(true);
			setBackground(DarkUi.MENU);
			setHorizontalAlignment(SwingConstants.CENTER);
			setBorder(new LineBorder(current ? DarkUi.LINE_LIT : DarkUi.MENU));
			setPreferredSize(new Dimension(ICON_CELL, ICON_CELL));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(DarkUi.BUTTON);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(DarkUi.MENU);
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					onClick.run();
				}
			});
		}
	}
}
