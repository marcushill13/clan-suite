package com.clansuite.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * Shared building blocks, so the panels look like one plugin rather than several.
 * <p>
 * Carried over from GearForge along with the lessons in it: a centred child in a vertical BoxLayout
 * indents everything below it, and text inside a row has to be narrower than the panel or it pushes
 * whatever sits beside it off the edge.
 */
public final class Cards
{
	/** Left accent strip width on a card, in pixels. */
	private static final int ACCENT = 3;

	/** Red rather than the brand orange, so a warning does not read as ordinary highlighting. */
	private static final Color WARNING_COLOR = new Color(220, 90, 70);

	/**
	 * Secondary text. RuneLite's MEDIUM_GRAY_COLOR on the panel background is grey-on-grey and was
	 * reported as hard to read; this keeps the hierarchy while staying legible.
	 */
	private static final Color MUTED_TEXT = Theme.TEXT_MUTED;

	/**
	 * Rows sit inside a scroll pane, and a trailing value would otherwise be clipped by the scrollbar.
	 */
	public static final int SCROLLBAR_ALLOWANCE = 12;

	private Cards()
	{
	}

	/**
	 * A padded block with a subtle darker background — the unit the panel is composed from.
	 */
	public static JPanel card()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	/**
	 * A card with a coloured strip down the left, used for the headline result.
	 */
	public static JPanel accentCard(Color accent)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(Theme.CARD);
		wrapper.setBorder(BorderFactory.createMatteBorder(0, ACCENT, 0, 0, accent));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		return wrapper;
	}

	/**
	 * Section label — small, uppercase, muted. Used to break the panel into scannable groups.
	 */
	public static JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	/**
	 * The one number that matters, rendered large.
	 */
	public static JLabel headline(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		label.setForeground(Theme.GOLD);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A setup or section title — larger than body text so a list of setups scans by name.
	 */
	public static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 13f));
		label.setForeground(Theme.TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A prominent warning block — a coloured strip and bold text, for things the player needs to act
	 * on rather than merely notice.
	 */
	public static JPanel warning(String text)
	{
		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setBackground(Theme.CARD);
		inner.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JLabel label = new JLabel("<html><body style='width:140px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(WARNING_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		inner.add(label);

		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createMatteBorder(0, ACCENT, 0, 0, WARNING_COLOR));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(inner, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/**
	 * The secondary text colour, for callers that need it on a component this class does not build.
	 */
	public static Color mutedColor()
	{
		return MUTED_TEXT;
	}

	/**
	 * Says something went wrong, where the player is already looking. A dialog would take focus off the
	 * game for something as ordinary as a mistyped date.
	 */
	public static void warn(java.awt.Component parent, String message)
	{
		javax.swing.JOptionPane.showMessageDialog(
			parent, message, "Boss of the Week", javax.swing.JOptionPane.INFORMATION_MESSAGE);
	}

	public static JLabel body(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Theme.TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Muted, wrapped text for reasons and caveats.
	 */
	/**
	 * Muted text sized to sit inside a row that already has an icon on one side and a value on the
	 * other. The full-width version forces the row wider than the panel, which pushes the value off the
	 * edge entirely.
	 */
	public static JLabel mutedInRow(String text)
	{
		JLabel label = new JLabel("<html><body style='width:100px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static JLabel muted(String text)
	{
		JLabel label = new JLabel("<html><body style='width:145px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A labelled form field stacked vertically, which reads better than side-by-side at this width.
	 */
	public static JPanel field(String label, JComponent input)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Theme.BACKGROUND);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel caption = sectionLabel(label);
		panel.add(caption);

		input.setAlignmentX(Component.LEFT_ALIGNMENT);
		input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height));
		panel.add(input);

		return panel;
	}

	/**
	 * A vertical spacer that is left-aligned like everything else.
	 * <p>
	 * {@link Box#createVerticalStrut} defaults to centre alignment, and a Y_AXIS BoxLayout containing
	 * a mix of alignments indents its children instead of filling the panel — which is what made the
	 * whole sidebar look squashed toward one side.
	 */
	public static Component gap(int height)
	{
		Component strut = Box.createVerticalStrut(height);
		((JComponent) strut).setAlignmentX(Component.LEFT_ALIGNMENT);
		return strut;
	}

	/**
	 * A compact button that matches the sidebar rather than the platform look-and-feel.
	 */
	public static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(Theme.TEXT);
		button.setBackground(Theme.CARD);
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setOpaque(true);
		// Tight horizontal padding: at 225px, three buttons in a row have barely 60px each, and
		// generous padding is what truncated "Off task" into "Off...".
		button.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		return button;
	}

	/**
	 * A titled section that expands when clicked, like the panels in the wiki's DPS calculator.
	 * <p>
	 * Used instead of a dropdown where the options benefit from being seen at once — prayers as a grid
	 * of icons, potions as a scrollable list — rather than hidden one-at-a-time behind a combo box.
	 *
	 * @param content shown when expanded; starts hidden
	 * @param decorate given the header button, so callers can hang an icon on it — the sprite and item
	 *                 image loaders are asynchronous, so the icon cannot simply be passed in
	 */
	public static JPanel expandable(String title, JComponent content, Consumer<JButton> decorate)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(Theme.BACKGROUND);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton header = button(title + "   +");
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setIconTextGap(8);
		// Tall enough that a skill icon sits in the row rather than being clipped by it.
		header.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		header.setPreferredSize(new Dimension(0, 30));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		decorate.accept(header);

		content.setVisible(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);

		header.addActionListener(event ->
		{
			boolean opening = !content.isVisible();
			content.setVisible(opening);
			header.setText(title + (opening ? "   −" : "   +"));
			section.revalidate();
			section.repaint();
		});

		section.add(header);
		section.add(content);
		return section;
	}

	/**
	 * A dropdown styled to match the sidebar. The default Swing combo renders light-on-light here and
	 * stands out badly against RuneLite's dark panels.
	 */
	public static <T> JComboBox<T> comboBox(T[] items)
	{
		JComboBox<T> combo = new JComboBox<>(items);
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setBackground(Theme.CARD);
		combo.setForeground(Theme.TEXT);
		combo.setFocusable(false);
		combo.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		combo.setRenderer(new DarkListRenderer());
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Without a maximum, BoxLayout leaves the combo at its preferred width instead of filling.
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
		return combo;
	}

	/**
	 * Two or more mutually exclusive options as side-by-side buttons — clearer than a dropdown when
	 * there are only a couple of choices, and it shows the alternative without a click.
	 */
	public static JPanel segmented(String[] labels, int selectedIndex, IntConsumer onSelect)
	{
		JPanel row = new JPanel(new GridLayout(1, labels.length, 4, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		ButtonGroup group = new ButtonGroup();

		for (int i = 0; i < labels.length; i++)
		{
			final int index = i;
			JToggleButton option = new JToggleButton(labels[i]);
			option.setFont(FontManager.getRunescapeSmallFont());
			option.setFocusPainted(false);
			option.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
			option.setSelected(i == selectedIndex);
			paintToggle(option);

			option.addItemListener(event ->
			{
				paintToggle(option);
				if (option.isSelected())
				{
					onSelect.accept(index);
				}
			});

			group.add(option);
			row.add(option);
		}

		return row;
	}

	private static void paintToggle(JToggleButton option)
	{
		option.setBackground(option.isSelected()
			? Theme.GOLD
			: Theme.CARD);
		option.setForeground(option.isSelected()
			? Theme.CARD
			: Theme.TEXT);
	}

	/**
	 * Keeps the dropdown popup dark instead of the default white list.
	 */
	private static final class DarkListRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(
			JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			setFont(FontManager.getRunescapeSmallFont());
			setBackground(isSelected ? Theme.GOLD : Theme.CARD);
			setForeground(isSelected ? Theme.CARD : Theme.TEXT);
			setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
			return this;
		}
	}

	/**
	 * Item names come from the game and can contain characters that would break the HTML wrapper.
	 */
	public static String escape(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
