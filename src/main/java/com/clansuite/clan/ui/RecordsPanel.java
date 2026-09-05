package com.clansuite.clan.ui;

import com.clansuite.clan.data.ClanRecord;
import com.clansuite.clan.data.ClanStatistics;
import com.clansuite.clan.data.PlayerStatistics;
import com.clansuite.event.data.Metric;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * What the clan has done, and what nobody has beaten.
 * <p>
 * The point of this screen is that it outlives an event. A leaderboard is finished the moment the week
 * is, and a clan's memory of "who has never missed one" or "whose was the biggest drop anybody has
 * seen" is otherwise a person with a spreadsheet and a good memory.
 * <p>
 * Only finished events are in any of it. An all-time best that changed every time somebody killed
 * something on a Tuesday would not be worth reading.
 */
public class RecordsPanel extends JPanel
{
	public RecordsPanel(
		List<ClanRecord> records,
		ClanStatistics statistics,
		PlayerStatistics yours,
		Runnable onRefresh,
		Runnable onBack)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(Cards.title("Records"));
		add(Cards.gap(4));
		add(Cards.muted("Everything the clan has finished. Events still running are not counted yet."));

		add(Cards.gap(14));
		add(Cards.sectionLabel(records.isEmpty() ? "NOTHING YET" : "CLAN RECORDS"));

		if (records.isEmpty())
		{
			add(Cards.gap(4));
			add(Cards.muted("Nothing to remember yet. Run an event and it will be here."));
		}

		for (ClanRecord record : records)
		{
			add(Cards.gap(4));
			add(recordCard(record));
		}

		if (yours != null)
		{
			add(Cards.gap(14));
			add(Cards.sectionLabel("YOU"));
			add(Cards.gap(4));
			add(yourCard(yours));
		}

		if (statistics != null)
		{
			add(Cards.gap(14));
			add(Cards.sectionLabel("THE CLAN"));
			add(Cards.gap(4));
			add(clanCard(statistics));

			if (!statistics.getMembers().isEmpty())
			{
				add(Cards.gap(14));
				add(Cards.sectionLabel("MOST POINTS, ALL TIME"));

				for (ClanStatistics.Member member : statistics.getMembers())
				{
					add(Cards.gap(4));
					add(memberRow(member));
				}
			}
		}

		add(Cards.gap(14));

		JButton refresh = Cards.button("Refresh");
		refresh.addActionListener(event -> onRefresh.run());

		JButton back = Cards.button("Back to clan");
		back.addActionListener(event -> onBack.run());

		add(inRow(refresh, back));
	}

	private JPanel recordCard(ClanRecord record)
	{
		JPanel card = Cards.paddedAccentCard(Theme.GOLD);

		card.add(Cards.sectionLabel(record.getTitle()));

		JLabel amount = new JLabel(number(record.getAmount()));
		amount.setFont(Theme.figure(18f));
		amount.setForeground(Theme.GOLD);
		amount.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(amount);

		card.add(Cards.body(record.getRsn()));

		StringBuilder where = new StringBuilder();
		if (record.getDetail() != null && !record.getDetail().isEmpty())
		{
			where.append(record.getDetail());
		}

		if (record.getEvent() != null && !record.getEvent().isEmpty())
		{
			where.append(where.length() > 0 ? " · " : "").append(record.getEvent());
		}

		if (where.length() > 0)
		{
			card.add(Cards.muted(where.toString()));
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel yourCard(PlayerStatistics yours)
	{
		JPanel card = Cards.paddedAccentCard(Theme.LIVE);

		card.add(Cards.headline(yours.getAttended() + " of " + yours.getEventsHeld() + " events"));
		card.add(Cards.body(yours.getPoints() + " points, best " + yours.getBest()));
		card.add(Cards.body(yours.getWon() == 1 ? "1 win" : yours.getWon() + " wins"));

		card.add(Cards.gap(4));
		card.add(Cards.body(yours.getStreak() == 0
			? "No run going — the last one you missed ended it"
			: "On a run of " + yours.getStreak()));
		card.add(Cards.muted("Longest run: " + yours.getLongestStreak()));

		String counted = counts(yours.of(Metric.KILL), yours.of(Metric.EXPERIENCE),
			yours.of(Metric.COMPLETION), yours.of(Metric.LOOT));

		if (!counted.isEmpty())
		{
			card.add(Cards.gap(4));
			card.add(Cards.muted(counted));
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel clanCard(ClanStatistics statistics)
	{
		ClanStatistics.Totals totals = statistics.getClan();
		JPanel card = Cards.paddedAccentCard(Theme.PVM);

		card.add(Cards.headline(totals.getEventsHeld() + (totals.getEventsHeld() == 1
			? " event held" : " events held")));
		card.add(Cards.body(totals.getAttendances() + " turn-ups by " + totals.getPeople() + " people"));
		card.add(Cards.body(totals.getPoints() + " points awarded"));

		String counted = counts(totals.of(Metric.KILL), totals.of(Metric.EXPERIENCE),
			totals.of(Metric.COMPLETION), totals.of(Metric.LOOT));

		if (!counted.isEmpty())
		{
			card.add(Cards.gap(4));
			card.add(Cards.muted(counted));
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel memberRow(ClanStatistics.Member member)
	{
		JPanel card = Cards.paddedAccentCard(member.getWon() > 0 ? Theme.GOLD : Theme.NEUTRAL);
		card.setLayout(new BorderLayout(6, 0));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Cards.headlineInRow(member.getRsn()));
		text.add(Cards.mutedInRow(member.getAttended() + " events · "
			+ (member.getWon() == 1 ? "1 win" : member.getWon() + " wins")));

		JLabel points = new JLabel(number(member.getPoints()));
		points.setFont(Theme.figure(15f));
		points.setForeground(Theme.TEXT);

		card.add(text, BorderLayout.CENTER);
		card.add(points, BorderLayout.EAST);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** The counted things worth saying out loud, and only the ones that are not nought. */
	private static String counts(long kills, long experience, long completions, long loot)
	{
		StringBuilder said = new StringBuilder();

		if (kills > 0)
		{
			said.append(number(kills)).append(" kills");
		}

		if (completions > 0)
		{
			said.append(said.length() > 0 ? " · " : "").append(number(completions)).append(" completions");
		}

		if (experience > 0)
		{
			said.append(said.length() > 0 ? " · " : "").append(number(experience)).append(" xp");
		}

		if (loot > 0)
		{
			said.append(said.length() > 0 ? " · " : "").append(number(loot)).append(" gp");
		}

		return said.toString();
	}

	/**
	 * Numbers as people say them. A drop worth 1,200,000,000 is a 1.2B drop, and nobody reads the
	 * commas.
	 */
	static String number(long amount)
	{
		if (amount >= 1_000_000_000L)
		{
			return trim(amount / 1_000_000_000d) + "B";
		}

		if (amount >= 1_000_000L)
		{
			return trim(amount / 1_000_000d) + "M";
		}

		if (amount >= 10_000L)
		{
			return trim(amount / 1_000d) + "K";
		}

		return String.valueOf(amount);
	}

	private static String trim(double value)
	{
		String written = String.format("%.1f", value);
		return written.endsWith(".0") ? written.substring(0, written.length() - 2) : written;
	}

	private JPanel inRow(Component... parts)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
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
