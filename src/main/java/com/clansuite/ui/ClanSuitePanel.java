package com.clansuite.ui;

import com.clansuite.ClanSuiteConfig;
import com.clansuite.botw.data.BossDrops;
import com.clansuite.botw.data.Challenge;
import com.clansuite.botw.net.BotwApi;
import com.clansuite.botw.track.ChallengeStore;
import com.clansuite.botw.track.EventSender;
import com.clansuite.botw.ui.ChallengeView;
import com.clansuite.botw.ui.CreatePanel;
import com.clansuite.botw.ui.EvidencePanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar.
 * <p>
 * Three screens behind one panel: the list of challenges this account is in, the form for making one,
 * and one challenge open. A sidebar is too narrow to show more than one at a time, and tabs across the
 * top would spend a quarter of the width saying which of three things you are looking at.
 * <p>
 * Every call to the service happens on the executor and comes back through the EDT. A request on the
 * client thread freezes the game, and a request on the EDT freezes the panel while it waits.
 */
@Singleton
public class ClanSuitePanel extends PluginPanel
{
	private final ChallengeStore challenges;
	private final BossDrops bossDrops;
	private final BotwApi api;
	private final ClanSuiteConfig config;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executor;
	private final EventSender sender;

	/** The logged-in name, which is who points are reported as. Null until logged in. */
	private Supplier<String> playerName = () -> null;

	private final JPanel content = new JPanel();

	@Inject
	private ClanSuitePanel(
		ChallengeStore challenges,
		BossDrops bossDrops,
		BotwApi api,
		ClanSuiteConfig config,
		ItemManager itemManager,
		ScheduledExecutorService executor,
		EventSender sender)
	{
		super(false);

		this.challenges = challenges;
		this.bossDrops = bossDrops;
		this.api = api;
		this.config = config;
		this.itemManager = itemManager;
		this.executor = executor;
		this.sender = sender;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);
		setOpaque(true);

		// PluginPanel pads itself, and that padding paints in whatever the panel's background is —
		// which is where the pale frame around everything came from. Removed here and put back inside
		// the scroll pane, so the dark goes right to the edge.
		setBorder(BorderFactory.createEmptyBorder());

		content.setLayout(new BorderLayout());
		content.setBackground(Theme.BACKGROUND);
		content.setOpaque(true);
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JScrollPane scroll = new JScrollPane(
			content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setBackground(Theme.BACKGROUND);
		scroll.setOpaque(true);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setBackground(Theme.BACKGROUND);
		scroll.getViewport().setBackground(Theme.BACKGROUND);
		scroll.getViewport().setOpaque(true);
		add(scroll, BorderLayout.CENTER);

		showList();
	}

	public void setPlayerName(Supplier<String> playerName)
	{
		this.playerName = playerName;
	}

	/**
	 * Called once this client's points have reached the service, so what is on screen catches up
	 * without anyone pressing anything.
	 * <p>
	 * Both screens that show points have to be handled, which is the bug this was written for: only the
	 * list was, so with a challenge open you could kill something, watch the screenshot appear in the
	 * evidence, and see your score sit still until you pressed Refresh.
	 * <p>
	 * The forms are still left alone. Rebuilding a half-filled one under someone's hands would throw
	 * away what they had typed.
	 */
	public void onPointsSent()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (content.getComponentCount() == 0)
			{
				return;
			}

			Component screen = content.getComponent(0);

			if (screen instanceof ListView)
			{
				showList();
				return;
			}

			if (!(screen instanceof ChallengeView))
			{
				return;
			}

			ChallengeView view = (ChallengeView) screen;
			if (view.isEditingLeaderboard())
			{
				return;
			}

			String code = view.getChallengeCode();

			executor.execute(() ->
			{
				BotwApi.Result<BotwApi.Snapshot> result = api.read(config.serverUrl(), code);
				if (!result.ok())
				{
					// Silent on purpose. This runs on a timer nobody asked for, and a warning box every
					// minute the connection hiccups would be worse than a leaderboard a minute behind.
					return;
				}

				SwingUtilities.invokeLater(() ->
				{
					// Still the same screen? A minute is long enough to have gone somewhere else.
					if (content.getComponentCount() > 0 && content.getComponent(0) == view)
					{
						view.update(result.getValue().getLeaderboard());
					}
				});
			});
		});
	}

	/**
	 * Redraws the list of challenges, if that is what is on screen.
	 * <p>
	 * Used after logging in, when the saved challenges become readable for the first time.
	 */
	public void refreshList()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (content.getComponentCount() > 0 && content.getComponent(0) instanceof ListView)
			{
				showList();
			}
		});
	}

	/**
	 * Re-reads the saved challenges, then brings each one up to date from the service.
	 * <p>
	 * The local half is what the button is really for. Challenges are saved against the logged-in
	 * account, so before login there is nothing to read and the list is empty; this is how it fills in
	 * without the player having to leave the screen and come back.
	 * <p>
	 * The service is then asked about each one, because the creator may have renamed it, moved its
	 * dates or changed what things are worth since it was last looked at. That half is done off the
	 * EDT, and the list is drawn once without waiting for it, so the button always feels immediate.
	 */
	private void reloadList()
	{
		challenges.load();
		showList();

		List<String> codes = new ArrayList<>();
		for (ChallengeStore.Membership membership : challenges.all())
		{
			codes.add(membership.challenge.getCode());
		}

		if (codes.isEmpty())
		{
			return;
		}

		executor.execute(() ->
		{
			List<Challenge> fresh = new ArrayList<>();
			for (String code : codes)
			{
				BotwApi.Result<BotwApi.Snapshot> result = api.read(config.serverUrl(), code);

				// A challenge that cannot be reached is left exactly as it was. A moment without a
				// network is not evidence that a challenge is gone, and dropping it would take the
				// player's tokens with it.
				if (result.ok() && result.getValue().getChallenge() != null)
				{
					fresh.add(result.getValue().getChallenge());
				}
			}

			SwingUtilities.invokeLater(() ->
			{
				for (Challenge challenge : fresh)
				{
					// Tokens survive this: a read carries the challenge, never the secrets.
					challenges.put(challenge, null, null);
				}

				refreshList();
			});
		});
	}

	private void show(JPanel screen)
	{
		content.removeAll();
		content.add(screen, BorderLayout.NORTH);
		content.revalidate();
		content.repaint();
	}

	/** Marker so {@link #refreshList()} can tell which screen is up without tracking state. */
	private static class ListView extends JPanel
	{
	}

	private void showList()
	{
		ListView list = new ListView();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.BACKGROUND);

		JLabel heading = new JLabel("BOSS OF THE WEEK");
		heading.setFont(Theme.title());
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		list.add(heading);

		JLabel strapline = new JLabel("Clan challenges, counted for you");
		strapline.setFont(Theme.body());
		strapline.setForeground(Theme.TEXT_MUTED);
		strapline.setAlignmentX(Component.LEFT_ALIGNMENT);
		list.add(strapline);

		list.add(Cards.gap(12));

		list.add(new TileButton("Create a challenge", "Pick a boss and set the points", this::showCreate));
		list.add(Cards.gap(8));
		list.add(new TileButton("Join a challenge", "Enter a code from your clan", this::showJoin));

		list.add(Cards.gap(14));

		// The heading and its reload are outside the empty check on purpose. An empty list is the case
		// that most needs reloading — before logging in there is nothing saved to read — and a button
		// that appears only once it is no longer needed would be no use at all.
		list.add(listHeader());

		List<ChallengeStore.Membership> mine = new ArrayList<>(challenges.all());
		if (mine.isEmpty())
		{
			list.add(Cards.gap(4));
			list.add(muted("Nothing yet. Make a challenge, or join one with a code. "
				+ "If you have joined one already, log in and press Reload."));
		}
		else
		{
			for (ChallengeStore.Membership membership : mine)
			{
				list.add(Cards.gap(4));
				list.add(challengeCard(membership));
			}
		}

		show(list);
	}

	/**
	 * Joining, once the tile has been pressed. A code box that is only there when it is wanted, rather
	 * than a field sitting on the front screen for the one time in twenty it gets used.
	 */
	private void showJoin()
	{
		JPanel screen = new JPanel();
		screen.setLayout(new BoxLayout(screen, BoxLayout.Y_AXIS));
		screen.setBackground(Theme.BACKGROUND);

		JLabel heading = new JLabel("JOIN A CHALLENGE");
		heading.setFont(Theme.figure(18f));
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		screen.add(heading);

		screen.add(Cards.gap(10));
		screen.add(muted("Paste the code the challenge's creator gave you."));
		screen.add(Cards.gap(8));

		JTextField code = Theme.textField(new JTextField());
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		code.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		code.setFont(Theme.figure(16f));
		code.setHorizontalAlignment(JTextField.CENTER);
		screen.add(code);

		screen.add(Cards.gap(10));
		screen.add(new TileButton("Join", null, () -> join(code.getText().trim().toUpperCase())));

		screen.add(Cards.gap(8));
		JButton back = Cards.button("← Back");
		back.addActionListener(event -> showList());
		screen.add(back);

		show(screen);

		// The code box is the only thing on this screen, so put the cursor in it.
		SwingUtilities.invokeLater(code::requestFocusInWindow);
	}

	/**
	 * "Your challenges", with the reload beside it rather than below, so it costs no vertical room in a
	 * sidebar that has none to spare.
	 */
	private JPanel listHeader()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.add(sectionLabel("Your challenges"), BorderLayout.WEST);

		JButton reload = Cards.button("Reload");
		reload.setToolTipText("Re-read your saved challenges and check them against the server");
		reload.addActionListener(event -> reloadList());
		row.add(reload, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT_MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JLabel muted(String text)
	{
		JLabel label = new JLabel("<html><body style='width:165px'>" + text + "</body></html>");
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT_MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JPanel challengeCard(ChallengeStore.Membership membership)
	{
		Challenge challenge = membership.challenge;

		JPanel card = new JPanel(new BorderLayout(4, 0));
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(card.getBackground());

		JLabel name = new JLabel(challenge.getName());
		name.setFont(Theme.heading());
		name.setForeground(Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		text.add(Cards.mutedInRow(challenge.getBoss()));
		text.add(Cards.mutedInRow(Countdown.describe(challenge, System.currentTimeMillis())));

		// Which side of the challenge this account is on, said on the card rather than only inside.
		// Being both is normal now: you make a challenge, then join it on the account you play.
		String role;
		if (membership.isCreator() && membership.isParticipant())
		{
			role = "CREATOR · JOINED";
		}
		else if (membership.isCreator())
		{
			role = "CREATOR · NOT JOINED";
		}
		else
		{
			role = "PARTICIPANT";
		}

		JLabel tag = new JLabel(role);
		tag.setFont(Theme.body());
		tag.setForeground(membership.isCreator() ? Theme.GOLD : Theme.TEXT_MUTED);
		tag.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(tag);

		card.add(text, BorderLayout.CENTER);

		JButton open = Cards.button("Open");
		open.addActionListener(event -> openChallenge(challenge.getCode()));
		card.add(open, BorderLayout.EAST);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private void showCreate()
	{
		show(new CreatePanel(bossDrops, itemManager, this::create, this::showList));
	}

	private void showEdit(Challenge challenge)
	{
		show(new CreatePanel(bossDrops, itemManager, this::saveEdit,
			() -> openChallenge(challenge.getCode()), challenge));
	}

	private void saveEdit(Challenge challenge)
	{
		String token = challenges.creatorTokenFor(challenge.getCode());
		if (token == null)
		{
			Cards.warn(this, "Only the creator can change this challenge.");
			return;
		}

		busy("Saving…");
		executor.execute(() ->
		{
			BotwApi.Result<BotwApi.Snapshot> result =
				api.update(config.serverUrl(), challenge, token);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					Cards.warn(this, result.getError());
				}
				else
				{
					challenges.put(result.getValue().getChallenge(), null, null);
				}

				openChallenge(challenge.getCode());
			});
		});
	}

	private void delete(String code)
	{
		String token = challenges.creatorTokenFor(code);
		if (token == null)
		{
			Cards.warn(this, "Only the creator can delete this challenge.");
			return;
		}

		busy("Deleting…");
		executor.execute(() ->
		{
			BotwApi.Result<BotwApi.Snapshot> result = api.delete(config.serverUrl(), code, token);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					showList();
					Cards.warn(this, result.getError());
					return;
				}

				// Locally too, along with anything still waiting to be sent for it — there is nothing
				// left to send it to.
				challenges.remove(code);
				sender.forget(code);
				showList();
			});
		});
	}

	private void create(Challenge challenge)
	{
		String rsn = playerName.get();
		if (rsn == null)
		{
			Cards.warn(this, "Log in first — a challenge is created under your name.");
			return;
		}

		busy("Creating…");
		executor.execute(() ->
		{
			BotwApi.Result<BotwApi.Snapshot> result = api.create(config.serverUrl(), challenge, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					showList();
					Cards.warn(this, result.getError());
					return;
				}

				BotwApi.Snapshot snapshot = result.getValue();

				// Only the creator token. Making a challenge and competing in it are separate things,
				// and joining is what a client needs to report kills — so the creator joins with the
				// code like everybody else, on whichever account they are actually playing.
				challenges.put(snapshot.getChallenge(), snapshot.getCreatorToken(), null);

				openChallenge(snapshot.getChallenge().getCode());
			});
		});
	}

	private void join(String code)
	{
		if (code.isEmpty())
		{
			Cards.warn(this, "Paste the challenge code first.");
			return;
		}

		String rsn = playerName.get();
		if (rsn == null)
		{
			Cards.warn(this, "Log in first — you join under your own name.");
			return;
		}

		busy("Joining…");
		executor.execute(() ->
		{
			BotwApi.Result<BotwApi.Snapshot> result = api.join(config.serverUrl(), code, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					showList();
					Cards.warn(this, result.getError());
					return;
				}

				BotwApi.Snapshot snapshot = result.getValue();
				challenges.put(snapshot.getChallenge(), null, snapshot.getParticipantToken());
				openChallenge(snapshot.getChallenge().getCode());
			});
		});
	}

	private void openChallenge(String code)
	{
		openChallenge(code, false);
	}

	/**
	 * @param asPlayer draw it the way a participant sees it, even for the creator. Whoever runs a
	 *                 challenge holds its creator token and so can never reach the plain screen on
	 *                 their own account — they get the dashboard even after joining — which leaves
	 *                 them unable to see what they are asking their clan to use.
	 */
	private void openChallenge(String code, boolean asPlayer)
	{
		busy("Loading…");

		executor.execute(() ->
		{
			// Sent before read, so opening a challenge or pressing Refresh shows the kill that has just
			// happened. Reading first would show the board as it was before this client had said
			// anything, which is a refresh that appears not to work — press it, see the old number,
			// press it again, see the old number.
			sender.flush();

			BotwApi.Result<BotwApi.Snapshot> result = api.read(config.serverUrl(), code);

			SwingUtilities.invokeLater(() ->
			{
				ChallengeStore.Membership membership = challenges.find(code);

				if (result.isGone())
				{
					// The service answered and said there is no such challenge: it was deleted by whoever
					// ran it, or it never existed. Keeping it on the list means a card that cannot be
					// opened and cannot be got rid of, so this is the one case where removing it is
					// offered. Offered, not done — it is the player's list.
					forget(code, membership);
					return;
				}

				if (!result.ok())
				{
					// Fall back to what is stored rather than showing nothing. Someone on a train can
					// still check when their challenge ends. Deliberately kept: a challenge that could
					// not be reached is not a challenge that is gone.
					if (membership != null)
					{
						show(new ChallengeView(membership.challenge, new ArrayList<>(), playerName.get(),
							membership.isCreator(), itemManager, this::showList, () -> openChallenge(code)));
					}
					else
					{
						showList();
					}

					Cards.warn(this, result.getError());
					return;
				}

				BotwApi.Snapshot snapshot = result.getValue();
				challenges.put(snapshot.getChallenge(), null, null);

				// Everything the creator gets hangs off this one token, so the preview is simply this
				// being treated as absent. That is what makes it an honest preview rather than a
				// separate screen that could drift out of step with the real one.
				String creatorToken = asPlayer ? null : challenges.creatorTokenFor(code);

				JPanel evidence = creatorToken == null
					? null
					: new EvidencePanel(code, snapshot.getChallenge().getName(), creatorToken,
						config.serverUrl(), api, executor, snapshot.getLeaderboard());

				Challenge open = snapshot.getChallenge();
				boolean canPreview = !asPlayer && challenges.creatorTokenFor(code) != null;

				show(new ChallengeView(
					open,
					snapshot.getLeaderboard(),
					playerName.get(),
					!asPlayer && membership != null && membership.isCreator(),
					itemManager,
					this::showList,
					() -> openChallenge(code, asPlayer),
					evidence,
					creatorToken == null ? null : () -> showEdit(open),
					creatorToken == null ? null : () -> delete(code),
					creatorToken == null ? null : leaderboardEditor(code, creatorToken),
					canPreview ? () -> openChallenge(code, true) : null,
					asPlayer ? () -> openChallenge(code, false) : null));
			});
		});
	}

	/**
	 * The creator's three edits to the leaderboard, each of which reopens the challenge on the way back
	 * so the board they are looking at is the one the service now holds rather than the one they were
	 * looking at a moment ago.
	 */
	private ChallengeView.LeaderboardEditor leaderboardEditor(String code, String creatorToken)
	{
		return new ChallengeView.LeaderboardEditor()
		{
			@Override
			public void add(String rsn, int points)
			{
				run("Adding " + rsn + "…",
					() -> api.addParticipant(config.serverUrl(), code, creatorToken, rsn, points));
			}

			@Override
			public void setPoints(Map<String, Integer> changes)
			{
				// One request per changed player, run in order on the one thread. The service has no
				// endpoint that takes several at once, and firing them off in parallel would race each
				// other's recomputation of the same leaderboard.
				run("Saving…", () ->
				{
					BotwApi.Result<BotwApi.Snapshot> result = null;

					for (Map.Entry<String, Integer> change : changes.entrySet())
					{
						result = api.setPoints(
							config.serverUrl(), code, creatorToken, change.getKey(), change.getValue());

						// Stopped at the first refusal, so the creator is told which name failed rather
						// than being shown the last one's error for all of them.
						if (!result.ok())
						{
							break;
						}
					}

					return result;
				});
			}

			@Override
			public void remove(String rsn)
			{
				run("Removing " + rsn + "…",
					() -> api.removeParticipant(config.serverUrl(), code, creatorToken, rsn));
			}

			private void run(String message, Supplier<BotwApi.Result<BotwApi.Snapshot>> call)
			{
				busy(message);
				executor.execute(() ->
				{
					BotwApi.Result<BotwApi.Snapshot> result = call.get();

					SwingUtilities.invokeLater(() ->
					{
						// Null only if there was nothing to send, which the caller already rules out.
						if (result != null && !result.ok())
						{
							Cards.warn(ClanSuitePanel.this, result.getError());
						}

						// Reopened either way. After a failure the board on screen is still right, and
						// after a success it is the only way to see the new one.
						openChallenge(code);
					});
				});
			}
		};
	}

	/**
	 * Offers to take a challenge that no longer exists off this account's list.
	 * <p>
	 * Also throws away anything still queued for it. Those events can never be delivered — there is
	 * nothing left to deliver them to — and without this they would be retried every minute for as long
	 * as the plugin is installed.
	 */
	private void forget(String code, ChallengeStore.Membership membership)
	{
		String name = membership == null ? code : membership.challenge.getName();

		int answer = JOptionPane.showConfirmDialog(
			this,
			"\"" + name + "\" no longer exists on the server." + System.lineSeparator()
				+ System.lineSeparator()
				+ "Whoever ran it has deleted it. Remove it from your list?",
			"Boss of the Week",
			JOptionPane.YES_NO_OPTION);

		if (answer == JOptionPane.YES_OPTION)
		{
			challenges.remove(code);
			sender.forget(code);
		}

		showList();
	}

	private void busy(String message)
	{
		JPanel waiting = new JPanel();
		waiting.setLayout(new BoxLayout(waiting, BoxLayout.Y_AXIS));
		waiting.setBackground(Theme.BACKGROUND);
		waiting.add(muted(message));
		show(waiting);
	}
}
