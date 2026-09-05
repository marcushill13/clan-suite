package com.clansuite.event.ui;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.EventCategory;
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
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Setting up an event, from a template.
 * <p>
 * The template is picked first and fills in everything it can — the category, and what this sort of
 * event usually counts. All of it stays editable, because a clan's raid night is not everybody's raid
 * night. Times are typed the same way and read in the same timezones as a Boss of the Week challenge,
 * so the two screens do not disagree about what "8pm" means.
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

	private final JComboBox<EventTemplate> template = Cards.comboBox(EventTemplate.values());
	private final JComboBox<EventCategory> category = Cards.comboBox(EventCategory.values());
	private final JTextField name = Theme.textField(new JTextField());
	private final JTextField startsAt = Theme.textField(new JTextField());
	private final JTextField endsAt = Theme.textField(new JTextField());
	private final JComboBox<String> timezone = Cards.comboBox(COMMON_ZONES);

	private final JPanel tracked = new JPanel();

	public CreateEventPanel(Consumer<ClanEvent> onCreate, Runnable onCancel)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(Cards.title("Create an event"));
		add(Cards.gap(4));
		add(Cards.muted("It is saved as a draft. Nobody in the clan sees it until you publish it."));
		add(Cards.gap(12));

		add(Cards.field("Template", template));
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

		template.addActionListener(event -> applyTemplate());

		LocalDateTime now = LocalDateTime.now();
		startsAt.setText(now.plusDays(1).withMinute(0).format(ENTERED));
		endsAt.setText(now.plusDays(1).withMinute(0).plusHours(4).format(ENTERED));
		timezone.setSelectedItem(ZoneId.systemDefault().getId());

		applyTemplate();
	}

	/** Fills in what the chosen template knows, leaving anything already typed alone. */
	private void applyTemplate()
	{
		EventTemplate chosen = (EventTemplate) template.getSelectedItem();
		if (chosen == null)
		{
			return;
		}

		category.setSelectedItem(chosen.getCategory());

		if (name.getText().trim().isEmpty() || isATemplateName())
		{
			name.setText(chosen == EventTemplate.CUSTOM ? "" : chosen.getLabel());
		}

		tracked.removeAll();
		tracked.add(Cards.muted(chosen.getBlurb()));

		if (!chosen.getTracks().isEmpty())
		{
			tracked.add(Cards.gap(4));
			tracked.add(Cards.muted("Counts: " + String.join(", ", chosen.getTracks())));
		}

		if (chosen.isBossOfTheWeek())
		{
			tracked.add(Cards.gap(4));
			tracked.add(Cards.muted("This one is run as a Boss of the Week challenge, which is set up "
				+ "on its own screen once the event is saved."));
		}

		tracked.revalidate();
		tracked.repaint();
	}

	/** Whether the name is one this screen filled in, and so may be replaced by another template's. */
	private boolean isATemplateName()
	{
		for (EventTemplate other : EventTemplate.values())
		{
			if (other.getLabel().equals(name.getText().trim()))
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

		EventTemplate chosen = (EventTemplate) template.getSelectedItem();
		EventCategory kind = (EventCategory) category.getSelectedItem();

		ClanEvent event = new ClanEvent();
		event.setName(name.getText().trim());
		event.setCategory((kind == null ? EventCategory.CUSTOM : kind).wire());
		event.setTemplate(EventTemplate.idOf(chosen));
		event.setStartsAt(start);
		event.setEndsAt(end);
		event.setTimezone(zone);
		event.setStatus("draft");
		event.setLeaderboard("points");

		// What it counts, written down from the template so the trackers have something to read when
		// they arrive. Editable later; this is the starting point, not the ruling.
		JsonObject config = new JsonObject();
		JsonArray tracks = new JsonArray();
		if (chosen != null)
		{
			for (String track : chosen.getTracks())
			{
				tracks.add(track);
			}
		}
		config.add("track", tracks);
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
