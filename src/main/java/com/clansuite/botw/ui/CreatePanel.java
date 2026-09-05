package com.clansuite.botw.ui;

import com.clansuite.botw.data.BossDrops;
import com.clansuite.botw.data.Challenge;
import com.clansuite.botw.data.DropRule;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Setting up a challenge.
 * <p>
 * The list of drops fills itself in from the chosen boss, because typing out sixteen items and their
 * point values is the sort of chore that stops people running events at all. Everything about it is
 * then editable — the inferred list gets the pets and the visages right and lets the odd bolt tip
 * through, so an X on every row matters more than the guess being perfect.
 */
public class CreatePanel extends JPanel
{
	private static final DateTimeFormatter ENTERED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/** Offered first because they are where the clan is. Any zone can still be typed. */
	private static final String[] COMMON_ZONES = {
		"Australia/Brisbane", "Australia/Sydney", "Australia/Perth",
		"Europe/London", "America/New_York", "America/Los_Angeles", "UTC"
	};

	private final BossDrops bossDrops;
	private final ItemManager itemManager;

	private final JTextField name = new JTextField();
	private final JTextField startsAt = new JTextField();
	private final JTextField endsAt = new JTextField();
	private final JComboBox<String> timezone = Cards.comboBox(COMMON_ZONES);

	private final JTextField bossSearch = new JTextField();
	private final JComboBox<BossDrops.Boss> bossPicker = Cards.comboBox(new BossDrops.Boss[0]);

	private final JSpinner kcPer = new JSpinner(new SpinnerNumberModel(10, 1, 10000, 1));
	private final JSpinner kcPoints = new JSpinner(new SpinnerNumberModel(1, 0, 10000, 1));

	/** The drop list being built, in the order it will be shown. Keyed by name, which is what scores. */
	private final Map<String, DropRule> drops = new LinkedHashMap<>();
	private final JPanel dropList = new JPanel();

	private final JPanel chosenBoss = new JPanel();
	private final JTextField itemSearch = new JTextField();
	private final JPanel itemResults = new JPanel();

	private final Consumer<Challenge> onCreate;
	private final Runnable onCancel;

	/** Set when changing an existing challenge rather than making one. */
	private Challenge editing;

	public CreatePanel(
		BossDrops bossDrops, ItemManager itemManager, Consumer<Challenge> onCreate, Runnable onCancel)
	{
		this(bossDrops, itemManager, onCreate, onCancel, null);
	}

	/**
	 * @param editing an existing challenge to change, or null to make a new one. The same form either
	 *                way — the fields are identical, and a second panel would be one more thing to keep
	 *                in step with this one.
	 */
	public CreatePanel(
		BossDrops bossDrops, ItemManager itemManager, Consumer<Challenge> onCreate, Runnable onCancel,
		Challenge editing)
	{
		this.bossDrops = bossDrops;
		this.itemManager = itemManager;
		this.onCreate = onCreate;
		this.onCancel = onCancel;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);

		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(Theme.BACKGROUND);
		form.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		JLabel heading = new JLabel(editing == null ? "NEW CHALLENGE" : "EDIT CHALLENGE");
		heading.setFont(Theme.figure(18f));
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(heading);
		form.add(Cards.gap(10));

		form.add(Cards.field("Challenge name", field(name)));
		form.add(Cards.gap(8));

		// Written out rather than picked from a calendar: a text field is one line of code and one
		// line of instruction, and a date picker in a 225px sidebar is neither.
		form.add(Cards.field("Starts (yyyy-mm-dd hh:mm)", field(startsAt)));
		form.add(Cards.gap(4));
		form.add(Cards.field("Ends (yyyy-mm-dd hh:mm)", field(endsAt)));
		form.add(Cards.gap(4));
		form.add(Cards.field("Timezone", timezone));
		form.add(Cards.gap(8));

		form.add(Cards.field("Find a boss", field(bossSearch)));
		form.add(Cards.gap(4));
		form.add(bossPicker);
		form.add(Cards.gap(6));

		// Without this the search box still reads as a half-finished search once a boss is picked, and
		// nothing on the screen says which one the challenge is actually about.
		chosenBoss.setLayout(new BoxLayout(chosenBoss, BoxLayout.Y_AXIS));
		chosenBoss.setBackground(Theme.BACKGROUND);
		chosenBoss.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(chosenBoss);
		form.add(Cards.gap(8));

		form.add(Cards.sectionLabel("Kill count"));
		form.add(everyKillsRow());
		form.add(Cards.gap(8));

		form.add(Cards.sectionLabel("Drops"));
		dropList.setLayout(new BoxLayout(dropList, BoxLayout.Y_AXIS));
		dropList.setBackground(Theme.BACKGROUND);
		dropList.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(dropList);
		form.add(Cards.gap(6));

		form.add(Cards.field("Add any item", field(itemSearch)));
		itemResults.setLayout(new BoxLayout(itemResults, BoxLayout.Y_AXIS));
		itemResults.setBackground(Theme.BACKGROUND);
		itemResults.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(itemResults);

		form.add(Cards.gap(10));
		form.add(buttons());

		add(form, BorderLayout.NORTH);

		bossSearch.getDocument().addDocumentListener(onType(this::refillBossPicker));
		itemSearch.getDocument().addDocumentListener(onType(this::refillItemResults));
		bossPicker.addActionListener(event ->
		{
			fillDropsFromBoss();
			showChosenBoss();
		});

		refillBossPicker();

		if (editing == null)
		{
			showChosenBoss();
			prefillTimes();
		}
		else
		{
			this.editing = editing;
			fillFrom(editing);
		}
	}

	/**
	 * Puts an existing challenge back into the form.
	 * <p>
	 * The drop list is taken from the challenge rather than from the boss, because the creator has
	 * already made their choices about it and refilling from the boss would throw away every removal
	 * and every points value they had set.
	 */
	private void fillFrom(Challenge challenge)
	{
		name.setText(challenge.getName());
		timezone.setSelectedItem(challenge.getTimezone());
		startsAt.setText(written(challenge.getStartsAt(), challenge.getTimezone()));
		endsAt.setText(written(challenge.getEndsAt(), challenge.getTimezone()));
		kcPer.setValue(Math.max(1, challenge.getKcPer()));
		kcPoints.setValue(Math.max(0, challenge.getKcPoints()));

		bossSearch.setText(challenge.getBoss());
		refillBossPicker();

		// Selecting the boss would refill the drops from the wiki list and undo the creator's edits, so
		// the stored list is put back afterwards.
		drops.clear();
		for (DropRule rule : challenge.getDrops())
		{
			drops.put(rule.getName(), new DropRule(rule.getName(), rule.getItemId(), rule.getPoints()));
		}

		showChosenBoss();
		rebuildDropList();
	}

	private static String written(long epochMillis, String timezone)
	{
		try
		{
			return java.time.Instant.ofEpochMilli(epochMillis)
				.atZone(ZoneId.of(timezone))
				.toLocalDateTime()
				.format(ENTERED);
		}
		catch (RuntimeException e)
		{
			return LocalDateTime.now().format(ENTERED);
		}
	}

	/** "Every [10] kills is worth [1] points", laid out as a row rather than two labelled fields. */
	private JPanel everyKillsRow()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		row.add(small("Every"));
		row.add(spinner(kcPer));
		row.add(small("kills ="));
		row.add(spinner(kcPoints));
		row.add(small("pts"));

		return row;
	}

	private JPanel buttons()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JButton cancel = Cards.button("Cancel");
		cancel.addActionListener(event -> onCancel.run());

		JButton create = Cards.button(editing == null ? "Create challenge" : "Save changes");
		create.addActionListener(event -> submit());

		row.add(cancel);
		row.add(javax.swing.Box.createHorizontalStrut(4));
		row.add(create);
		return row;
	}

	/**
	 * A sensible week, so the common case is two edits rather than two full timestamps typed out.
	 */
	private void prefillTimes()
	{
		LocalDateTime now = LocalDateTime.now();
		startsAt.setText(now.plusDays(1).withHour(10).withMinute(0).format(ENTERED));
		endsAt.setText(now.plusDays(8).withHour(22).withMinute(0).format(ENTERED));
		timezone.setSelectedItem(ZoneId.systemDefault().getId());
	}

	/**
	 * A plain statement of which boss this challenge is about, under the search.
	 */
	private void showChosenBoss()
	{
		chosenBoss.removeAll();

		BossDrops.Boss boss = (BossDrops.Boss) bossPicker.getSelectedItem();
		if (boss != null)
		{
			JPanel card = new JPanel(new BorderLayout());
			card.setBackground(Theme.CARD);
			card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
			card.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel chosen = new JLabel(boss.getName());
			chosen.setFont(Theme.heading());
			chosen.setForeground(Theme.GOLD);
			card.add(chosen, BorderLayout.CENTER);

			card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
			chosenBoss.add(card);
		}

		chosenBoss.revalidate();
		chosenBoss.repaint();
	}

	private void refillBossPicker()
	{
		DefaultComboBoxModel<BossDrops.Boss> model = new DefaultComboBoxModel<>();
		for (BossDrops.Boss boss : bossDrops.search(bossSearch.getText()))
		{
			model.addElement(boss);
		}

		bossPicker.setModel(model);
		if (model.getSize() > 0)
		{
			bossPicker.setSelectedIndex(0);
		}
	}

	/**
	 * Replaces the drop list with whatever the newly chosen boss is known for.
	 * <p>
	 * Replaces rather than merges: changing the boss means the old list is about a different fight, and
	 * quietly leaving Vorkath's visages on a Zulrah challenge would be worse than losing an edit.
	 */
	private void fillDropsFromBoss()
	{
		BossDrops.Boss boss = (BossDrops.Boss) bossPicker.getSelectedItem();
		drops.clear();

		if (boss != null)
		{
			for (BossDrops.Unique unique : boss.getUniques())
			{
				drops.put(unique.getName(),
					new DropRule(unique.getName(), unique.getItemId(), 0));
			}
		}

		rebuildDropList();
	}

	private void rebuildDropList()
	{
		dropList.removeAll();

		if (drops.isEmpty())
		{
			dropList.add(Cards.muted("No drops on this challenge yet. Pick a boss, or add items below."));
		}

		for (DropRule rule : new ArrayList<>(drops.values()))
		{
			dropList.add(dropRow(rule));
			dropList.add(Cards.gap(2));
		}

		dropList.revalidate();
		dropList.repaint();
	}

	/** An icon, the name, a points box, and an X. */
	private JPanel dropRow(DropRule rule)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		if (rule.getItemId() > 0)
		{
			itemManager.getImage(rule.getItemId()).addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JLabel label = new JLabel("<html><body style='width:72px'>" + rule.getName() + "</body></html>");
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT);
		row.add(label, BorderLayout.CENTER);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setBackground(row.getBackground());

		JSpinner points = new JSpinner(new SpinnerNumberModel(rule.getPoints(), 0, 100000, 1));
		points.setPreferredSize(new Dimension(46, 20));
		points.setMaximumSize(new Dimension(46, 20));
		points.addChangeListener(event -> rule.setPoints((Integer) points.getValue()));
		right.add(points);

		JButton remove = Cards.button("X");
		remove.setToolTipText("Do not count " + rule.getName());
		remove.addActionListener(event ->
		{
			drops.remove(rule.getName());
			rebuildDropList();
		});
		right.add(remove);

		row.add(right, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * Anything at all can be added, not only the boss's own drops. Clans run challenges on all sorts of
	 * things and the inferred list will always miss somebody's idea.
	 */
	private void refillItemResults()
	{
		itemResults.removeAll();

		String query = itemSearch.getText().trim();
		if (query.length() < 3)
		{
			itemResults.revalidate();
			itemResults.repaint();
			return;
		}

		List<ItemPrice> found = itemManager.search(query);
		if (found.isEmpty())
		{
			JLabel none = new JLabel("Nothing matches that.");
			none.setFont(Theme.body());
			none.setForeground(Theme.TEXT_MUTED);
			none.setAlignmentX(Component.LEFT_ALIGNMENT);
			itemResults.add(none);
		}

		int shown = 0;
		for (ItemPrice item : found)
		{
			// Enough to choose from without the panel growing past the screen. The rest are reached by
			// typing more, which is faster than scrolling anyway.
			if (shown++ >= 8)
			{
				break;
			}

			itemResults.add(searchResult(item));
			itemResults.add(Cards.gap(2));
		}

		itemResults.revalidate();
		itemResults.repaint();
	}

	/**
	 * One search result: icon, name, and a row tall enough to hit.
	 * <p>
	 * Was a text button barely taller than its own text, which made the list look broken and gave
	 * nothing to aim at.
	 */
	private JPanel searchResult(ItemPrice item)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		itemManager.getImage(item.getId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JLabel label = new JLabel("<html><body style='width:120px'>" + item.getName() + "</body></html>");
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT);
		row.add(label, BorderLayout.CENTER);

		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event)
			{
				drops.put(item.getName(), new DropRule(item.getName(), item.getId(), 0));
				itemSearch.setText("");
				refillItemResults();
				rebuildDropList();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent event)
			{
				row.setBackground(Theme.CARD_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event)
			{
				row.setBackground(Theme.CARD);
			}
		});

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return row;
	}

	private void submit()
	{
		Challenge challenge = new Challenge();
		challenge.setName(name.getText().trim());

		BossDrops.Boss boss = (BossDrops.Boss) bossPicker.getSelectedItem();
		challenge.setBoss(boss == null ? "" : boss.getName());

		String zone = String.valueOf(timezone.getSelectedItem());
		challenge.setTimezone(zone);

		Long start = parse(startsAt.getText(), zone);
		Long end = parse(endsAt.getText(), zone);

		if (challenge.getName().isEmpty() || challenge.getBoss().isEmpty() || start == null || end == null)
		{
			Cards.warn(this, "A name, a boss and both times are needed. Times look like 2026-08-17 10:00.");
			return;
		}

		if (end <= start)
		{
			Cards.warn(this, "The end time has to be after the start time.");
			return;
		}

		challenge.setStartsAt(start);
		challenge.setEndsAt(end);
		challenge.setKcPer((Integer) kcPer.getValue());
		challenge.setKcPoints((Integer) kcPoints.getValue());
		challenge.setDrops(new ArrayList<>(drops.values()));

		// Carried through so the caller knows which challenge it is saving.
		if (editing != null)
		{
			challenge.setCode(editing.getCode());
		}

		onCreate.accept(challenge);
	}

	/**
	 * A typed time, read in the challenge's own timezone rather than the machine's. Someone in Perth
	 * setting up a Brisbane event means the Brisbane hour.
	 */
	private static Long parse(String text, String timezone)
	{
		try
		{
			LocalDateTime local = LocalDateTime.parse(text.trim(), ENTERED);
			return local.atZone(ZoneId.of(timezone)).toInstant().toEpochMilli();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static JTextField field(JTextField field)
	{
		Theme.textField(field);
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return field;
	}

	private static JSpinner spinner(JSpinner spinner)
	{
		spinner.setPreferredSize(new Dimension(48, 22));
		spinner.setMaximumSize(new Dimension(48, 22));
		return spinner;
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel(" " + text + " ");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Theme.TEXT);
		return label;
	}

	private static DocumentListener onType(Runnable action)
	{
		return new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				action.run();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				action.run();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				action.run();
			}
		};
	}
}
