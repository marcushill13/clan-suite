package com.clansuite.event.ui;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.EventParticipant;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One event: when it is, who is in it, and what they have.
 * <p>
 * The board is the same shape every event will use, whatever it ends up counting — a name and a
 * number. What fills the number in is the tracking work; until it does, the number is whatever the
 * staff have given by hand, which is exactly how a hide and seek will always work.
 */
public class EventView extends JPanel
{
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

	public interface Actions
	{
		void join();

		void leave();

		/** @param attended null to leave the tick alone; adjustment null to leave the score alone */
		void mark(String rsn, Boolean attended, Integer adjustment);

		void setStatus(String status);

		void back();
	}

	public EventView(
		ClanEvent event,
		List<EventParticipant> participants,
		String yourRsn,
		boolean canManage,
		boolean canVerify,
		Actions actions)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(header(event));

		boolean signedUp = false;
		for (EventParticipant participant : participants)
		{
			signedUp |= participant.getRsn().equalsIgnoreCase(yourRsn == null ? "" : yourRsn);
		}

		final boolean taking = signedUp;

		long now = System.currentTimeMillis();
		boolean open = !event.isDraft() && !event.isCancelled() && !event.hasEnded(now);

		if (open || taking)
		{
			add(Cards.gap(10));

			JButton take = Cards.button(taking ? "Leave event" : "Take part");
			take.addActionListener(pressed ->
			{
				if (taking)
				{
					actions.leave();
				}
				else
				{
					actions.join();
				}
			});

			add(inRow(take));
		}

		add(Cards.gap(14));
		add(Cards.sectionLabel(participants.isEmpty()
			? "NOBODY YET"
			: "TAKING PART — " + participants.size()));

		if (participants.isEmpty())
		{
			add(Cards.gap(4));
			add(Cards.muted(open
				? "Nobody has signed up. Take part and you will be on the board."
				: "Nobody took part in this one."));
		}

		for (EventParticipant participant : participants)
		{
			add(Cards.gap(4));
			add(participantRow(event, participant, yourRsn, canVerify, actions));
		}

		if (canManage)
		{
			add(Cards.gap(14));
			add(Cards.sectionLabel("RUNNING THIS EVENT"));

			if (event.isDraft())
			{
				add(Cards.gap(4));
				add(Cards.muted("A draft is only visible to you and the rest of the staff."));
				add(Cards.gap(6));

				JButton publish = Cards.button("Publish");
				publish.addActionListener(pressed -> actions.setStatus("published"));
				add(inRow(publish));
			}
			else if (!event.isCancelled())
			{
				add(Cards.gap(6));
				JButton cancel = Cards.button("Cancel event");
				cancel.addActionListener(pressed -> actions.setStatus("cancelled"));
				add(inRow(cancel));
			}
			else
			{
				add(Cards.gap(4));
				add(Cards.muted("Cancelled, and kept on the calendar so people can see it was."));
			}
		}

		add(Cards.gap(14));

		JButton back = Cards.button("Back to events");
		back.addActionListener(pressed -> actions.back());
		add(inRow(back));
	}

	private JPanel header(ClanEvent event)
	{
		JPanel card = Cards.accentCard(event.category().getColour());
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		card.add(Cards.title(event.getName()));
		card.add(Cards.gap(2));

		JLabel kind = new JLabel(event.template().getLabel()
			+ (event.isDraft() ? " · draft" : event.isCancelled() ? " · cancelled" : ""));
		kind.setFont(Theme.body());
		kind.setForeground(event.category().getColour());
		kind.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(kind);

		card.add(Cards.gap(8));
		card.add(Cards.body(written(event.getStartsAt(), event.getTimezone())));
		card.add(Cards.body("to " + written(event.getEndsAt(), event.getTimezone())));

		card.add(Cards.gap(8));
		card.add(Cards.sectionLabel("EVENT CODE"));

		JLabel code = new JLabel(event.getCode());
		code.setFont(Theme.figure(18f));
		code.setForeground(Theme.GOLD);
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(code);

		if (event.getConfig() != null && event.getConfig().has("track"))
		{
			card.add(Cards.gap(8));
			card.add(Cards.sectionLabel("COUNTS"));
			card.add(Cards.muted(event.getConfig().getAsJsonArray("track").toString()
				.replace("[", "").replace("]", "").replace("\"", "")));
			card.add(Cards.muted("Counted by hand for now — the trackers that watch for these are "
				+ "being built one at a time."));
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel participantRow(
		ClanEvent event, EventParticipant participant, String yourRsn, boolean canVerify, Actions actions)
	{
		JPanel card = Cards.paddedAccentCard(
			participant.isAttended() ? Theme.LIVE : Theme.NEUTRAL);
		card.setLayout(new BorderLayout(6, 0));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		boolean you = participant.getRsn().equalsIgnoreCase(yourRsn == null ? "" : yourRsn);
		text.add(Cards.headlineInRow(participant.getRsn() + (you ? " (you)" : "")));

		StringBuilder detail = new StringBuilder(participant.getPoints() + " points");
		if (participant.isAttended())
		{
			detail.append(" · marked present");
		}

		text.add(Cards.mutedInRow(detail.toString()));
		card.add(text, BorderLayout.CENTER);

		if (canVerify)
		{
			JButton tick = Cards.button(participant.isAttended() ? "Untick" : "Mark present");
			tick.addActionListener(pressed ->
				actions.mark(participant.getRsn(), !participant.isAttended(), null));

			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(tick, BorderLayout.NORTH);
			card.add(holder, BorderLayout.EAST);
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private static String written(long at, String timezone)
	{
		try
		{
			return Instant.ofEpochMilli(at).atZone(ZoneId.of(timezone)).format(WHEN);
		}
		catch (RuntimeException e)
		{
			return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(WHEN);
		}
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
				row.add(Box.createHorizontalStrut(6));
			}

			row.add(parts[i]);
		}

		return row;
	}
}
