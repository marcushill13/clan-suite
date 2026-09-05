package com.clansuite.event.ui;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.EventCategory;
import com.clansuite.event.data.EventPreset;
import com.clansuite.event.data.EventPresets;
import com.clansuite.event.data.EventTemplate;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Setting up an event.
 * <p>
 * The clan's own events are the list: Wintertodt Mass, Forestry Mass, Hide and Seek, the lot. Picking
 * one fills in the category, what it counts and what things are worth, because a Mahogany Homes mass
 * has no kill count and never will, and a Forestry mass is mostly about who finds the whistle first.
 * All of it stays editable — a clan's Wintertodt mass is not everybody's Wintertodt mass.
 * <p>
 * Times are typed the same way and read in the same timezones as a Boss of the Week challenge, so the
 * two screens do not disagree about what "8pm" means.
 * <p>
 * A new event is saved as a draft. Publishing is a separate press once it reads right, so that a
 * half-written event is never on the clan's calendar.
 */
public class CreateEventPanel extends JPanel
{
	private static final DateTimeFormatter ENTERED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/** The same list the challenge screen offers, for the same reason: it is where the clan is. */
	private static final String[] COMMON_ZONES = {
		"Australia/Brisbane", "Australia/Sydney", "Australia/Perth",
		"Europe/London", "America/New_York", "America/Los_Angeles", "UTC"
	};

	private final EventPresets presets;

	private final JTextField search = Theme.textField(new JTextField());
	private final JComboBox<EventPreset> preset = Cards.comboBox(new EventPreset[0]);
	private final JComboBox<EventCategory> category = Cards.comboBox(EventCategory.values());
	private final JTextField name = Theme.textField(new JTextField());
	private final JTextField startsAt = Theme.textField(new JTextField());
	private final JTextField endsAt = Theme.textField(new JTextField());
	private final JComboBox<String> timezone = Cards.comboBox(COMMON_ZONES);

	private final JPanel tracked = new JPanel();

	public CreateEventPanel(EventPresets presets, Consumer<ClanEvent> onCreate, Runnable onCancel)
	{
		this.presets = presets;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(Cards.title("Create an event"));
		add(Cards.gap(4));
		add(Cards.muted("It is saved as a draft. Nobody in the clan sees it until you publish it."));
		add(Cards.gap(12));

		add(Cards.field("Find an event", search));
		add(Cards.gap(6));
		add(Cards.field("Event", preset));
		add(Cards.gap(4));

		tracked.setLayout(new BoxLayout(tracked, BoxLayout.Y_AXIS));
		tracked.setBackground(Theme.BACKGROUND);
		tracked.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(tracked);

		add(Cards.gap(10));
		add(Cards.field("Name", name));
		add(Cards.gap(8));
		add(Cards.field("Category", category));

		add(Cards.gap(10));
		add(Cards.field("Starts", startsAt));
		add(Cards.gap(8));
		add(Cards.field("Ends", endsAt));
		add(Cards.gap(4));
		add(Cards.muted("As yyyy-MM-dd HH:mm, in the timezone below."));
		add(Cards.gap(8));
		add(Cards.field("Timezone", timezone));

		add(Cards.gap(14));

		JButton create = Cards.button("Save as draft");
		create.addActionListener(event -> submit(onCreate));

		JButton cancel = Cards.button("Back");
		cancel.addActionListener(event -> onCancel.run());

		add(inRow(create, cancel));

		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent event)
			{
				refilter();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent event)
			{
				refilter();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent event)
			{
				refilter();
			}
		});

		preset.addActionListener(event -> applyTemplate());
		refilter();

		LocalDateTime now = LocalDateTime.now();
		startsAt.setText(now.plusDays(1).withMinute(0).format(ENTERED));
		endsAt.setText(now.plusDays(1).withMinute(0).plusHours(4).format(ENTERED));
		timezone.setSelectedItem(ZoneId.systemDefault().getId());

		applyTemplate();
	}

	/** Narrows the list as somebody types. Sixty-odd events is too many to scroll past every time. */
	private void refilter()
	{
		List<EventPreset> matches = presets.search(search.getText());

		preset.removeAllItems();
		for (EventPreset match : matches)
		{
			preset.addItem(match);
		}

		if (!matches.isEmpty())
		{
			preset.setSelectedIndex(0);
		}
	}

	/**
	 * Fills the form in from the chosen event, leaving a name somebody has typed themselves alone.
	 */
	private void applyTemplate()
	{
		EventPreset chosen = (EventPreset) preset.getSelectedItem();
		if (chosen == null)
		{
			return;
		}

		category.setSelectedItem(chosen.category());

		if (name.getText().trim().isEmpty() || isAPresetName())
		{
			name.setText(chosen.getName());
		}

		tracked.removeAll();
		tracked.add(Cards.muted(chosen.getTrack().isEmpty()
			? "Counts nothing on its own — decide what it should below, or mark people off by hand."
			: "Counts: " + String.join(", ", chosen.getTrack())));

		int bounties = chosen.bounties();
		if (bounties > 0)
		{
			tracked.add(Cards.gap(4));
			tracked.add(Cards.muted(bounties == 1
				? "One bounty: points to whoever gets there first, and nobody else."
				: bounties + " bounties: points to whoever gets there first, and nobody else."));
		}

		if (chosen.getNote() != null && !chosen.getNote().isEmpty())
		{
			tracked.add(Cards.gap(4));
			tracked.add(Cards.muted(chosen.getNote()));
		}

		tracked.revalidate();
		tracked.repaint();
	}

	/** Whether the name is one this screen filled in, and so may be replaced by another event's. */
	private boolean isAPresetName()
	{
		for (EventPreset other : presets.all())
		{
			if (other.getName().equals(name.getText().trim()))
			{
				return true;
			}
		}

		return false;
	}

	private void submit(Consumer<ClanEvent> onCreate)
	{
		if (name.getText().trim().isEmpty())
		{
			Cards.warn(this, "Give the event a name.");
			return;
		}

		String zone = String.valueOf(timezone.getSelectedItem());
		Long start = parse(startsAt.getText(), zone);
		Long end = parse(endsAt.getText(), zone);

		if (start == null || end == null)
		{
			Cards.warn(this, "Times go in as yyyy-MM-dd HH:mm — for example "
				+ LocalDateTime.now().format(ENTERED) + ".");
			return;
		}

		if (end <= start)
		{
			Cards.warn(this, "The end has to be after the start.");
			return;
		}

		EventPreset chosen = (EventPreset) preset.getSelectedItem();
		EventCategory kind = (EventCategory) category.getSelectedItem();

		ClanEvent event = new ClanEvent();
		event.setName(name.getText().trim());
		event.setCategory((kind == null ? EventCategory.CUSTOM : kind).wire());
		event.setTemplate(chosen == null ? EventTemplate.idOf(EventTemplate.CUSTOM) : chosen.getId());
		event.setStartsAt(start);
		event.setEndsAt(end);
		event.setTimezone(zone);
		event.setStatus("draft");
		event.setLeaderboard("points");

		// What it counts and what things are worth, taken from the event that was picked. The rules go
		// with it: a Forestry mass without its bounties is not the event anybody meant to run.
		JsonObject config = new JsonObject();
		JsonArray tracks = new JsonArray();

		if (chosen != null)
		{
			for (String track : chosen.getTrack())
			{
				tracks.add(track);
			}
		}

		config.add("track", tracks);
		config.add("points", chosen == null ? new JsonArray() : chosen.getRules());
		event.setConfig(config);

		onCreate.accept(event);
	}

	/**
	 * A typed time, read in the event's own timezone rather than the machine's. Somebody in Perth
	 * setting up a Brisbane raid night means the Brisbane hour.
	 */
	private static Long parse(String text, String timezone)
	{
		try
		{
			return LocalDateTime.parse(text.trim(), ENTERED)
				.atZone(ZoneId.of(timezone))
				.toInstant()
				.toEpochMilli();
		}
		catch (RuntimeException e)
		{
			return null;
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
