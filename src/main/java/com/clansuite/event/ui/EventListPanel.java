package com.clansuite.event.ui;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The clan's calendar: what is on now, what is coming, and what has been.
 * <p>
 * Ordered by when rather than by kind, because that is the question people open this to answer. The
 * category is a coloured edge on each card instead of a heading, so the three groups stay in date
 * order and a glance still tells a raid night from a skilling week.
 * <p>
 * Drafts only reach whoever runs the events — the service does not send them to anyone else — so a
 * half-finished event can sit here for a week without the clan asking what it is.
 */
public class EventListPanel extends JPanel
{
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

	public EventListPanel(
		List<ClanEvent> events,
		boolean canManage,
		Runnable onCreate,
		Consumer<ClanEvent> onOpen,
		Runnable onRefresh,
		Runnable onBossOfTheWeek)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		long now = System.currentTimeMillis();

		List<ClanEvent> live = new ArrayList<>();
		List<ClanEvent> coming = new ArrayList<>();
		List<ClanEvent> past = new ArrayList<>();

		for (ClanEvent event : events)
		{
			if (event.isRunning(now))
			{
				live.add(event);
			}
			else if (!event.hasEnded(now))
			{
				coming.add(event);
			}
			else
			{
				past.add(event);
			}
		}

		add(Cards.title("Events"));
		add(Cards.gap(4));
		add(Cards.muted(canManage
			? "Your clan's calendar. Drafts are only visible to you and the rest of the staff."
			: "Your clan's calendar."));

		if (canManage)
		{
			add(Cards.gap(10));
			JButton create = Cards.button("Create event");
			create.addActionListener(event -> onCreate.run());
			add(inRow(create));
		}

		add(Cards.gap(14));

		if (events.isEmpty())
		{
			add(Cards.sectionLabel("NOTHING ON"));
			add(Cards.gap(4));
			add(Cards.muted(canManage
				? "No events yet. Create one and it stays a draft until you publish it."
				: "Nothing on the calendar. Your clan's staff will put events here."));
		}
		else
		{
			// Coming first, then what is on now above it — the two questions in the order they are
			// asked. Anything finished is at the bottom, where it can be scrolled to.
			section("ON NOW", live, onOpen);
			section("COMING UP", coming, onOpen);
			section("FINISHED", past, onOpen);
		}

		add(Cards.gap(14));

		JButton refresh = Cards.button("Refresh");
		refresh.addActionListener(event -> onRefresh.run());
		add(inRow(refresh));

		add(Cards.gap(14));
		add(Cards.sectionLabel("BOSS OF THE WEEK"));
		add(Cards.gap(4));
		add(Cards.muted("The competitions this plugin started with, which run on their own codes."));
		add(Cards.gap(6));

		JButton botw = Cards.button("Open challenges");
		botw.addActionListener(event -> onBossOfTheWeek.run());
		add(inRow(botw));
	}

	private void section(String heading, List<ClanEvent> events, Consumer<ClanEvent> onOpen)
	{
		if (events.isEmpty())
		{
			return;
		}

		add(Cards.sectionLabel(heading + " — " + events.size()));

		for (ClanEvent event : events)
		{
			add(Cards.gap(4));
			add(eventCard(event, onOpen));
		}

		add(Cards.gap(12));
	}

	private JPanel eventCard(ClanEvent event, Consumer<ClanEvent> onOpen)
	{
		// Cancelled events keep their category colour but say so in the corner; a called-off raid night
		// is still a raid night in the calendar.
		JPanel card = Cards.paddedAccentCard(event.category().getColour());
		card.setLayout(new BorderLayout(4, 0));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		text.add(Cards.headlineInRow(event.getName()));

		JLabel when = new JLabel(written(event));
		when.setFont(Theme.body());
		when.setForeground(Theme.TEXT_MUTED);
		when.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(when);

		// The template alone, coloured by its category. Showing both said "Social · Social" on half the
		// rows and ran off the edge on the other half.
		JLabel kind = new JLabel(event.template().getLabel());
		kind.setFont(Theme.body());
		kind.setForeground(event.category().getColour());
		kind.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(kind);

		if (event.isDraft() || event.isCancelled())
		{
			JLabel state = new JLabel(event.isDraft() ? "DRAFT" : "CANCELLED");
			state.setFont(Theme.body());
			state.setForeground(event.isDraft() ? Theme.GOLD : Theme.CAPPED);
			state.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(state);
		}

		card.add(text, BorderLayout.CENTER);

		JButton open = Cards.button("Open");
		open.addActionListener(pressed -> onOpen.accept(event));

		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(open, BorderLayout.NORTH);
		card.add(holder, BorderLayout.EAST);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** When it runs, in the timezone whoever made it chose rather than the reader's. */
	private static String written(ClanEvent event)
	{
		try
		{
			ZoneId zone = ZoneId.of(event.getTimezone());
			return Instant.ofEpochMilli(event.getStartsAt()).atZone(zone).format(WHEN);
		}
		catch (RuntimeException e)
		{
			// A timezone the machine has never heard of should not blank the whole card.
			return Instant.ofEpochMilli(event.getStartsAt()).atZone(ZoneId.systemDefault()).format(WHEN);
		}
	}

	private JPanel inRow(Component... parts)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		for (Component part : parts)
		{
			row.add(part);
		}

		return row;
	}
}
