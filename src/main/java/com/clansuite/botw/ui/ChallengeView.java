package com.clansuite.botw.ui;

import com.clansuite.botw.data.Challenge;
import com.clansuite.botw.data.DropRule;
import com.clansuite.botw.data.LeaderboardEntry;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Countdown;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * One challenge, open.
 * <p>
 * The same screen whether you made it or joined it. A creator wants to see the leaderboard as much as
 * anyone else does, and giving them a different view would mean two things to keep in step.
 */
public class ChallengeView extends JPanel
{
	/**
	 * What the creator may do to the leaderboard by hand. Null for everyone else, which is what makes
	 * the controls appear only for them.
	 * <p>
	 * This exists because some of a clan plays on mobile, where no plugin can run and no kill can ever
	 * be counted. Those players do what they have always done — send screenshots — and staff put the
	 * number in, so they end up on the same board as everyone else rather than in a spreadsheet
	 * alongside it.
	 */
	public interface LeaderboardEditor
	{
		void add(String rsn, int points);

		/**
		 * @param changes every row the creator actually altered, name to new total. Passed together
		 *                rather than one at a time so the whole edit is one trip and one redraw; a call
		 *                per row would race several reopens against each other.
		 */
		void setPoints(Map<String, Integer> changes);

		void remove(String rsn);
	}

	private final ItemManager itemManager;
	private final Runnable onBack;

	/** Held so the leaderboard alone can be redrawn when edit mode is switched on and off. */
	private final JPanel leaderboardHolder = new JPanel();

	/** Held for the same reason: both move when points land, and nothing else on the screen does. */
	private final JPanel yourPointsHolder = new JPanel();

	private Challenge challenge;
	private List<LeaderboardEntry> entries = new ArrayList<>();
	private String yourName;
	private LeaderboardEditor editor;
	private boolean editing;

	/** The points boxes while editing, by name, so Save can tell what actually changed. */
	private final Map<String, JTextField> pointsFields = new LinkedHashMap<>();

	public ChallengeView(
		Challenge challenge,
		List<LeaderboardEntry> leaderboard,
		String yourName,
		boolean creator,
		ItemManager itemManager,
		Runnable onBack,
		Runnable onRefresh)
	{
		this(challenge, leaderboard, yourName, creator, itemManager, onBack, onRefresh, null);
	}

	public ChallengeView(
		Challenge challenge,
		List<LeaderboardEntry> leaderboard,
		String yourName,
		boolean creator,
		ItemManager itemManager,
		Runnable onBack,
		Runnable onRefresh,
		JPanel evidence)
	{
		this(challenge, leaderboard, yourName, creator, itemManager, onBack, onRefresh, evidence,
			null, null, null, null, null);
	}

	/**
	 * @param onEdit   offered only to the creator, and only when this client holds the token
	 * @param onDelete same
	 * @param editor   same again — null for a participant, which is what hides the controls
	 */
	public ChallengeView(
		Challenge challenge,
		List<LeaderboardEntry> leaderboard,
		String yourName,
		boolean creator,
		ItemManager itemManager,
		Runnable onBack,
		Runnable onRefresh,
		JPanel evidence,
		Runnable onEdit,
		Runnable onDelete,
		LeaderboardEditor editor,
		Runnable onPreview,
		Runnable onEndPreview)
	{
		this.itemManager = itemManager;
		this.onBack = onBack;
		this.challenge = challenge;
		this.entries = leaderboard;
		this.yourName = yourName;
		this.editor = editor;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		body.add(backRow(onRefresh));
		body.add(Cards.gap(6));

		body.add(Cards.title(challenge.getName()));
		body.add(Cards.gap(2));
		body.add(Cards.body(challenge.getBoss()));
		body.add(Cards.gap(6));

		// The countdown is the thing everyone opens this for, so it goes at the top and it is loud.
		long now = System.currentTimeMillis();
		JLabel countdown = new JLabel(Countdown.describe(challenge, now));
		countdown.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 15f));
		// Green while it is actually running, so "live now" is readable at a glance rather than being
		// the same colour as everything else.
		countdown.setForeground(challenge.isRunning(now) ? Theme.LIVE : Theme.GOLD);
		countdown.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(countdown);

		body.add(Cards.gap(2));
		body.add(Cards.muted(Countdown.at(challenge.getStartsAt(), challenge.getTimezone())
			+ "  to  " + Countdown.at(challenge.getEndsAt(), challenge.getTimezone())));

		body.add(Cards.gap(8));
		body.add(codeRow(challenge, creator));

		if (onEndPreview != null)
		{
			body.add(Cards.gap(6));
			body.add(previewBanner(onEndPreview));
		}

		if (onEdit != null || onDelete != null || onPreview != null)
		{
			body.add(Cards.gap(6));
			body.add(creatorControls(challenge, onEdit, onDelete, onPreview));
		}

		body.add(Cards.gap(10));
		body.add(Cards.sectionLabel("Points"));
		body.add(pointsList(challenge));

		body.add(Cards.gap(10));
		body.add(Cards.sectionLabel("Leaderboard"));

		leaderboardHolder.setLayout(new BoxLayout(leaderboardHolder, BoxLayout.Y_AXIS));
		leaderboardHolder.setBackground(Theme.BACKGROUND);
		leaderboardHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		renderLeaderboard();
		body.add(leaderboardHolder);

		body.add(Cards.gap(10));
		body.add(Cards.sectionLabel("Your points"));

		yourPointsHolder.setLayout(new BoxLayout(yourPointsHolder, BoxLayout.Y_AXIS));
		yourPointsHolder.setBackground(Theme.BACKGROUND);
		yourPointsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		renderYourPoints();
		body.add(yourPointsHolder);

		// Only the creator gets this, and only when there is a token to fetch it with.
		if (evidence != null)
		{
			body.add(Cards.gap(12));
			body.add(Cards.sectionLabel("Evidence"));
			body.add(Cards.gap(2));
			body.add(Cards.muted("A screenshot of every scoring drop, sent automatically."));
			body.add(Cards.gap(4));
			body.add(evidence);
		}

		add(body, BorderLayout.NORTH);
	}

	private JPanel backRow(Runnable onRefresh)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JButton back = Cards.button("← All challenges");
		back.addActionListener(event -> onBack.run());
		row.add(back);

		row.add(javax.swing.Box.createHorizontalStrut(4));

		JButton refresh = Cards.button("Refresh");
		refresh.addActionListener(event -> onRefresh.run());
		row.add(refresh);

		return row;
	}

	/**
	 * The code, shown large. It is what the creator has to paste into Discord, and what everyone else
	 * has to read back, so it is the one thing on here worth making easy to copy by eye.
	 */
	private JPanel codeRow(Challenge challenge, boolean creator)
	{
		JPanel card = Cards.card();

		card.add(Cards.sectionLabel(creator ? "Your challenge code" : "Challenge code"));

		JLabel code = new JLabel(challenge.getCode());
		code.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f));
		code.setForeground(Theme.GOLD);
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(code);

		if (creator)
		{
			card.add(Cards.gap(2));
			card.add(Cards.muted("Share this so people can join."));
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/**
	 * Editing and deleting, for whoever made it.
	 * <p>
	 * Deleting asks first and says what goes with it. A challenge takes the events and the screenshots
	 * down with it, and that is not something to discover afterwards.
	 */
	/**
	 * Says loudly that this is not the real thing, and gets back out.
	 * <p>
	 * Without it the preview is indistinguishable from having lost your creator rights, which is a
	 * frightening thing to see on a challenge you are running.
	 */
	private JPanel previewBanner(Runnable onEndPreview)
	{
		JPanel card = Cards.card();

		JLabel heading = new JLabel("VIEWING AS A PLAYER");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(heading);

		card.add(Cards.gap(2));
		card.add(Cards.muted("This is what everyone who joined sees. Nothing here is changed for them."));
		card.add(Cards.gap(4));

		JButton back = Cards.button("Back to your dashboard");
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event -> onEndPreview.run());
		card.add(back);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel creatorControls(
		Challenge challenge, Runnable onEdit, Runnable onDelete, Runnable onPreview)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		if (onPreview != null)
		{
			// Whoever runs a challenge holds its creator token, so they can never reach the plain
			// participant screen on their own account — they see the dashboard even after joining. This
			// shows them what the rest of the clan is looking at.
			JButton preview = Cards.button("View as player");
			preview.setToolTipText("See this challenge the way everyone who joined sees it");
			preview.addActionListener(event -> onPreview.run());
			row.add(preview);
			row.add(javax.swing.Box.createHorizontalStrut(4));
		}

		if (onEdit != null)
		{
			JButton edit = Cards.button("Edit");
			edit.addActionListener(event -> onEdit.run());
			row.add(edit);
			row.add(javax.swing.Box.createHorizontalStrut(4));
		}

		if (onDelete != null)
		{
			JButton delete = Cards.button("Delete");
			delete.setToolTipText("Removes the challenge, its scores and its screenshots for everyone");
			delete.addActionListener(event ->
			{
				int answer = javax.swing.JOptionPane.showConfirmDialog(
					this,
					"Delete \"" + challenge.getName() + "\"?" + System.lineSeparator()
						+ System.lineSeparator()
						+ "This removes it for everyone who joined, along with every score and every "
						+ "screenshot. It cannot be undone.",
					"Boss of the Week",
					javax.swing.JOptionPane.YES_NO_OPTION);

				if (answer == javax.swing.JOptionPane.YES_OPTION)
				{
					onDelete.run();
				}
			});

			row.add(delete);
		}

		return row;
	}

	private JPanel pointsList(Challenge challenge)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.BACKGROUND);
		list.setAlignmentX(Component.LEFT_ALIGNMENT);

		list.add(row(null, "Every " + challenge.getKcPer() + " kills",
			challenge.getKcPoints() + " pts", Theme.TEXT));

		for (DropRule drop : challenge.getDrops())
		{
			list.add(Cards.gap(2));
			list.add(row(drop.getItemId(), drop.getName(),
				drop.getPoints() + " pts", Theme.TEXT));
		}

		if (challenge.getDrops().isEmpty())
		{
			list.add(Cards.gap(2));
			list.add(Cards.muted("No drops on this one — kill count only."));
		}

		return list;
	}

	public String getChallengeCode()
	{
		return challenge.getCode();
	}

	/**
	 * Takes a fresh leaderboard without rebuilding the screen.
	 * <p>
	 * Called when this client's kills reach the service, which is a minute or so after they happen.
	 * Before this, points landed on the server and the open screen went on showing whatever it had been
	 * built with — you killed something, the evidence appeared, and your score did not move until you
	 * pressed Refresh. The numbers were never wrong, only the picture of them.
	 * <p>
	 * Only the two parts that can have changed are redrawn, rather than the whole screen, so this does
	 * not throw away where you had scrolled to or shut the evidence folder you were reading.
	 */
	public void update(List<LeaderboardEntry> fresh)
	{
		// Never mid-edit. The creator has half-typed numbers in those boxes, and replacing the rows
		// under them would lose what they had entered.
		if (editing)
		{
			return;
		}

		entries = fresh;
		renderLeaderboard();
		renderYourPoints();
	}

	/** Whether the creator is part way through changing scores, in which case leave the screen alone. */
	public boolean isEditingLeaderboard()
	{
		return editing;
	}

	private void renderYourPoints()
	{
		yourPointsHolder.removeAll();
		yourPointsHolder.add(yourPoints(entries, yourName, challenge));
		yourPointsHolder.revalidate();
		yourPointsHolder.repaint();
	}

	/** Fills {@link #leaderboardHolder}, in whichever mode it is currently in. */
	private void renderLeaderboard()
	{
		leaderboardHolder.removeAll();
		pointsFields.clear();

		if (editor != null)
		{
			leaderboardHolder.add(editorControls());
			leaderboardHolder.add(Cards.gap(4));
		}

		if (entries.isEmpty())
		{
			leaderboardHolder.add(Cards.muted("Nobody has scored yet."));
		}

		int place = 1;
		for (LeaderboardEntry entry : entries)
		{
			leaderboardHolder.add(row(entry, place++));
			leaderboardHolder.add(Cards.gap(2));
		}

		if (editing)
		{
			leaderboardHolder.add(Cards.gap(4));
			leaderboardHolder.add(saveRow());
		}

		leaderboardHolder.revalidate();
		leaderboardHolder.repaint();
	}

	private JPanel row(LeaderboardEntry entry, int place)
	{
		boolean you = entry.getRsn().equalsIgnoreCase(yourName == null ? "" : yourName);

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel position = new JLabel(place + ".");
		position.setFont(FontManager.getRunescapeSmallFont());
		position.setForeground(Theme.TEXT_MUTED);
		row.add(position, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(entry.getRsn());
		name.setFont(FontManager.getRunescapeBoldFont());
		// Your own row is highlighted, because on a fifty-person leaderboard finding yourself is
		// the first thing anyone does.
		name.setForeground(you ? Theme.GOLD : Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		if (entry.isManual())
		{
			// Said plainly on the row. These points were typed in by staff from a screenshot, and a
			// leaderboard that hid that would be claiming to have counted something it never saw.
			JLabel tag = new JLabel("Manual / Mobile");
			tag.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			tag.setForeground(Theme.TEXT_MUTED);
			tag.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(tag);
		}
		else
		{
			text.add(Cards.mutedInRow(entry.getKills() + " kills, " + entry.getDrops() + " drops"));
		}

		row.add(text, BorderLayout.CENTER);
		row.add(editing ? pointsEditor(entry) : pointsLabel(entry), BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JLabel pointsLabel(LeaderboardEntry entry)
	{
		JLabel points = new JLabel(String.valueOf(entry.getPoints()));
		points.setFont(FontManager.getRunescapeBoldFont());
		points.setForeground(Theme.GOLD);
		return points;
	}

	/** The points box, and an X for anyone who was added by hand. */
	private JPanel pointsEditor(LeaderboardEntry entry)
	{
		JPanel side = new JPanel(new BorderLayout(2, 0));
		side.setBackground(Theme.CARD);

		JTextField field = Theme.textField(new JTextField(String.valueOf(entry.getPoints())));
		field.setPreferredSize(new Dimension(46, 20));
		field.setHorizontalAlignment(JTextField.RIGHT);
		pointsFields.put(entry.getRsn(), field);
		side.add(field, BorderLayout.CENTER);

		// Only for hand-added rows. Removing someone who is playing would throw away kills their own
		// client reported, and the way off the board for them is to leave the challenge.
		if (entry.isManual())
		{
			JButton remove = Cards.button("✕");
			remove.setPreferredSize(new Dimension(22, 20));
			remove.setToolTipText("Remove " + entry.getRsn());
			remove.addActionListener(event ->
			{
				if (confirmed("Remove " + entry.getRsn() + " from the leaderboard?"))
				{
					editor.remove(entry.getRsn());
				}
			});
			side.add(remove, BorderLayout.EAST);
		}

		return side;
	}

	/** Add and Edit, above the board, for the creator only. */
	private JPanel editorControls()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton add = Cards.button("+ Add player");
		add.setToolTipText("Add someone who cannot run the plugin, such as a mobile player");
		add.addActionListener(event -> promptForPlayer());
		row.add(add, BorderLayout.CENTER);

		JButton edit = Cards.button(editing ? "Cancel" : "Edit points");
		edit.addActionListener(event ->
		{
			editing = !editing;
			renderLeaderboard();
		});
		row.add(edit, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel saveRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton save = Cards.button("Save changes");
		save.addActionListener(event -> saveEdits());
		row.add(save, BorderLayout.CENTER);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * Sends only the rows whose number actually changed.
	 * <p>
	 * Saving all of them would be a request per player and would write an adjustment onto people the
	 * creator never touched, which is exactly the kind of thing that turns up a fortnight later as
	 * "why is everyone's score frozen".
	 */
	private void saveEdits()
	{
		Map<String, Integer> changes = new LinkedHashMap<>();

		for (LeaderboardEntry entry : entries)
		{
			JTextField field = pointsFields.get(entry.getRsn());
			if (field == null)
			{
				continue;
			}

			Integer wanted = parsePoints(field.getText());
			if (wanted == null)
			{
				// Nothing is sent when one box is wrong. Saving the good rows and stopping at the bad
				// one would leave the creator guessing which half went through.
				Cards.warn(this, "\"" + field.getText().trim() + "\" is not a number of points.");
				return;
			}

			if (wanted != entry.getPoints())
			{
				changes.put(entry.getRsn(), wanted);
			}
		}

		editing = false;

		if (changes.isEmpty())
		{
			// Nobody was changed, so nothing is sent — but the board still has to come out of edit mode,
			// which the reopen would otherwise have done.
			renderLeaderboard();
			return;
		}

		editor.setPoints(changes);
	}

	/** Null when it is not a whole number. Negatives are allowed: docking points is a real thing. */
	static Integer parsePoints(String text)
	{
		try
		{
			return Integer.valueOf(text.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * The name box for adding someone by hand, with their points alongside it — staff are entering
	 * both off the same screenshot, and asking for the name first and the score afterwards would be
	 * two dialogs for one thought.
	 */
	private void promptForPlayer()
	{
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

		JTextField name = new JTextField(14);
		JTextField points = new JTextField("0", 14);

		form.add(new JLabel("RuneScape name"));
		form.add(name);
		form.add(new JLabel(" "));
		form.add(new JLabel("Points"));
		form.add(points);

		SwingUtilities.invokeLater(name::requestFocusInWindow);

		int choice = JOptionPane.showConfirmDialog(
			this, form, "Add a player", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}

		String rsn = name.getText().trim();
		if (rsn.isEmpty())
		{
			Cards.warn(this, "A name is needed.");
			return;
		}

		Integer starting = parsePoints(points.getText());
		if (starting == null)
		{
			Cards.warn(this, "\"" + points.getText().trim() + "\" is not a number of points.");
			return;
		}

		editor.add(rsn, starting);
	}

	private boolean confirmed(String question)
	{
		return JOptionPane.showConfirmDialog(
			this, question, "Boss of the Week", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}

	/**
	 * Your own total, and what it is made of. Shown even at zero, because "0 points" is an answer and a
	 * blank space is not.
	 */
	private JPanel yourPoints(List<LeaderboardEntry> leaderboard, String yourName, Challenge challenge)
	{
		JPanel card = Cards.card();

		LeaderboardEntry you = null;
		for (LeaderboardEntry entry : leaderboard)
		{
			if (entry.getRsn().equalsIgnoreCase(yourName == null ? "" : yourName))
			{
				you = entry;
				break;
			}
		}

		int points = you == null ? 0 : you.getPoints();
		JLabel total = new JLabel(points + (points == 1 ? " point" : " points"));
		total.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		total.setForeground(Theme.GOLD);
		total.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(total);

		// Somebody the creator entered by hand has no kills and no drops but does have points, and
		// telling them to go and kill something under a score of ten would read as a broken screen.
		boolean nothingYet = you == null
			|| (you.getKills() == 0 && you.getDrops() == 0 && you.getAdjustment() == 0);

		if (nothingYet)
		{
			card.add(Cards.gap(2));
			card.add(Cards.muted(challenge.isRunning(System.currentTimeMillis())
				? "Go and kill something."
				: "Nothing counted yet."));
		}
		else
		{
			card.add(Cards.gap(2));
			int killPoints = challenge.getKcPer() > 0
				? you.getKills() / challenge.getKcPer() * challenge.getKcPoints()
				: 0;

			// Taken off before the drops are worked out. This line used to be the whole remainder, so
			// anything the creator had added showed up as though a drop had been worth it — a 30 point
			// drop reading as 50 because twenty had been granted by hand.
			int adjustment = you.getAdjustment();

			card.add(Cards.muted(you.getKills() + " kills — " + killPoints + " pts"));
			card.add(Cards.muted(you.getDrops() + " counted drops — "
				+ (points - killPoints - adjustment) + " pts"));

			if (adjustment != 0)
			{
				card.add(Cards.muted("Set by the creator — "
					+ (adjustment > 0 ? "+" : "") + adjustment + " pts"));
			}
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** An optional icon, a label, and a value on the right. */
	private JPanel row(Integer itemId, String label, String value, Color colour)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (itemId != null && itemId > 0)
		{
			JLabel icon = new JLabel();
			itemManager.getImage(itemId).addTo(icon);
			row.add(icon, BorderLayout.WEST);
		}

		JLabel name = new JLabel("<html><body style='width:105px'>" + label + "</body></html>");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(colour);
		row.add(name, BorderLayout.CENTER);

		JLabel points = new JLabel(value);
		points.setFont(FontManager.getRunescapeSmallFont());
		points.setForeground(Theme.GOLD);
		row.add(points, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}
}
