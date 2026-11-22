package com.lootlabels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** The box shared by export and import: a title, a tickable row for each group, and buttons to go ahead or close. */
final class TransferCard
{
	private static final int MAX_ROWS = 10; // groups shown at once; with more, the list scrolls

	private TransferCard()
	{
	}

	/** The groups in {@code shown} that are ticked, in the order shown. */
	static List<LabelGroup> chosen(List<LabelGroup> shown, Set<LabelGroup> picks)
	{
		final List<LabelGroup> chosen = new ArrayList<>();
		for (LabelGroup group : shown)
		{
			if (picks.contains(group))
			{
				chosen.add(group);
			}
		}
		return chosen;
	}

	static String readClipboard()
	{
		try
		{
			final Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			return data instanceof String ? (String) data : null;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	static boolean copyToClipboard(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		}
		catch (IllegalStateException e)
		{
			return false;
		}
	}

	/**
	 * Builds the box. With a {@code message} there is nothing to pick, so it shows the message and only a close
	 * button. Otherwise {@code action} runs when the go button is pressed; if it works, the button says
	 * {@code doneText} for a moment and then the box closes.
	 */
	static JComponent build(String title, List<LabelGroup> shown, Set<LabelGroup> picks, String message,
		String actionText, String doneText, BooleanSupplier action, Runnable close)
	{
		final JPanel card = DarkUi.card();
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel heading = new JLabel(title);
		heading.setForeground(DarkUi.TEXT_BRIGHT);
		heading.setFont(FontManager.getRunescapeSmallFont());
		heading.setBorder(new EmptyBorder(0, 0, 4, 0));
		card.add(heading, BorderLayout.NORTH);

		final TrackingPanel body = new TrackingPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		if (message != null)
		{
			final JLabel note = new JLabel("<html><body style='width:150px'>" + message + "</body></html>");
			note.setForeground(DarkUi.TEXT_DIM);
			note.setFont(FontManager.getRunescapeSmallFont());
			note.setAlignmentX(Component.LEFT_ALIGNMENT);
			body.add(note);
		}
		else
		{
			for (LabelGroup group : shown)
			{
				final JButton row = DarkUi.checkRow(group.getName() + " (" + group.getEntries().size() + ")",
					picks.contains(group), on ->
					{
						if (on)
						{
							picks.add(group);
						}
						else
						{
							picks.remove(group);
						}
					});
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				body.add(row);
			}
		}

		// Tall enough for MAX_ROWS groups; with more than that the list scrolls
		JComponent center = body;
		if (message == null && shown.size() > MAX_ROWS)
		{
			final int rowHeight = body.getComponent(0).getPreferredSize().height;
			final JScrollPane scroll = new JScrollPane(body,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setBorder(null);
			scroll.setViewportBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
			scroll.setPreferredSize(new Dimension(0, rowHeight * MAX_ROWS));
			DarkUi.styleScrollBar(scroll.getVerticalScrollBar());
			center = scroll;
		}
		card.add(center, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new GridLayout(1, message != null ? 1 : 2, 4, 0));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(6, 0, 0, 0));
		final boolean[] done = {false};
		if (message == null)
		{
			final JButton go = DarkUi.button(actionText);
			go.addActionListener(e ->
			{
				if (done[0] || !action.getAsBoolean())
				{
					return;
				}
				done[0] = true;
				go.setText(doneText);
				final Timer timer = new Timer(700, ev ->
				{
					if (card.isShowing())
					{
						close.run();
					}
				});
				timer.setRepeats(false);
				timer.start();
			});
			buttons.add(go);
		}
		final JButton cancel = DarkUi.button(message != null ? "CLOSE" : "CANCEL");
		cancel.addActionListener(e -> close.run());
		buttons.add(cancel);
		card.add(buttons, BorderLayout.SOUTH);
		return card;
	}
}
