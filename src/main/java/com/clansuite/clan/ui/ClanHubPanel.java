package com.clansuite.clan.ui;

import com.clansuite.clan.data.Clan;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The hub: every clan that has chosen to be seen, and a way in.
 * <p>
 * The count is on every row for the same reason the game shows it — a clan at five hundred cannot take
 * you however much you both want it to, and finding that out after writing an application is a waste
 * of everybody's evening.
 * <p>
 * A clan that is not listed here is not necessarily closed. Private clans recruit by passing their code
 * around, so the code box at the top reaches them; it is the same box the hub row's button uses
 * underneath.
 */
public class ClanHubPanel extends JPanel
{
	/** Warm amber rather than the alarm red, because full is a fact about a clan, not a fault. */
	private static final Color FULL = Theme.CAPPED;

	private final JTextField search = Theme.textField(new JTextField());
	private final JTextField code = Theme.textField(new JTextField());

	/**
	 * @param clans    what the service returned for the current search
	 * @param onSearch run again with a new query
	 * @param onOpen   look a clan up by code, which also serves clans that are not listed
	 * @param onApply  apply to this clan
	 */
	public ClanHubPanel(
		List<Clan> clans,
		Consumer<String> onSearch,
		Consumer<String> onOpen,
		Consumer<Clan> onApply,
		Runnable onRefresh)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(Cards.title("Clans"));
		add(Cards.gap(4));
		add(Cards.muted("Clans that have listed themselves. Apply and their staff will decide."));
		add(Cards.gap(10));

		add(Cards.field("Search", search));
		add(Cards.gap(6));
		add(row(
			press("Search", () -> onSearch.accept(search.getText())),
			press("Refresh", onRefresh)));

		add(Cards.gap(12));

		add(Cards.field("Have a code?", code));
		add(Cards.gap(6));
		add(row(press("Look up", () ->
		{
			if (code.getText().trim().isEmpty())
			{
				Cards.warn(this, "Paste the clan's code first.");
				return;
			}

			onOpen.accept(code.getText().trim().toUpperCase());
		})));

		add(Cards.gap(14));
		add(Cards.sectionLabel(clans.isEmpty() ? "NOTHING LISTED" : "LISTED CLANS"));

		if (clans.isEmpty())
		{
			add(Cards.gap(4));
			add(Cards.muted("No clan matches that. A clan only appears here if it has chosen to be "
				+ "listed — if you were given a code, use the box above."));
			return;
		}

		for (Clan clan : clans)
		{
			add(Cards.gap(6));
			add(clanRow(clan, onApply));
		}
	}

	private JPanel clanRow(Clan clan, Consumer<Clan> onApply)
	{
		// The strip says what the button would: green if they can take you, amber if they are full,
		// grey if they are not looking. Readable before any of the words are.
		JPanel card = Cards.paddedAccentCard(clan.isFull()
			? FULL
			: clan.isApplicationsOpen() ? Theme.LIVE : Theme.NEUTRAL);
		card.setLayout(new BorderLayout(6, 0));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		text.add(Cards.headlineInRow(clan.getName()));

		if (!clan.getTagline().isEmpty())
		{
			text.add(Cards.mutedInRow(clan.getTagline()));
		}

		JLabel count = new JLabel(clan.membership());
		count.setFont(Theme.body());
		count.setForeground(clan.isFull() ? FULL : Theme.TEXT_MUTED);
		count.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(count);

		card.add(text, BorderLayout.CENTER);
		card.add(applyButton(clan, onApply), BorderLayout.EAST);

		return card;
	}

	/**
	 * The one control on a row, and what it says is the whole story: a clan that is full or has closed
	 * its doors says so instead of offering a button that would be refused.
	 */
	private Component applyButton(Clan clan, Consumer<Clan> onApply)
	{
		if (clan.isFull())
		{
			return badge("FULL", FULL);
		}

		if (!clan.isApplicationsOpen())
		{
			return badge("CLOSED", Theme.TEXT_MUTED);
		}

		JButton apply = Cards.button("Apply");
		apply.addActionListener(event -> onApply.accept(clan));

		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(apply, BorderLayout.NORTH);
		return holder;
	}

	private Component badge(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(Theme.body());
		label.setForeground(colour);
		label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
		return label;
	}

	private JButton press(String label, Runnable onClick)
	{
		JButton button = Cards.button(label);
		button.addActionListener(event -> onClick.run());
		return button;
	}

	private JPanel row(Component... parts)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				row.add(javax.swing.Box.createHorizontalStrut(6));
			}

			row.add(parts[i]);
		}

		return row;
	}
}
