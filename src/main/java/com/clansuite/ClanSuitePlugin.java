package com.clansuite;

import com.clansuite.botw.net.BotwApi;
import com.clansuite.botw.track.ChallengeStore;
import com.clansuite.clan.ClanStore;
import com.clansuite.event.track.EventTrackers;
import com.clansuite.event.track.ObservationOutbox;
import com.clansuite.event.track.ObservationSender;
import com.clansuite.botw.track.EventSender;
import com.clansuite.botw.track.KillTracker;
import com.clansuite.botw.track.Outbox;
import com.clansuite.capture.Screenshotter;
import com.clansuite.ui.ClanSuitePanel;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Clan Suite",
	description = "Run your clan's events: kill counts, drops, points, screenshots and live leaderboards",
	tags = {"clan", "event", "competition", "leaderboard", "boss", "drops", "kc", "points", "raids"}
)
public class ClanSuitePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChallengeStore challenges;

	@Inject
	private ClanStore clans;

	@Inject
	private EventTrackers eventTrackers;

	@Inject
	private ObservationOutbox observations;

	@Inject
	private ObservationSender observationSender;

	@Inject
	private Outbox outbox;

	@Inject
	private KillTracker killTracker;

	@Inject
	private EventSender sender;

	@Inject
	private ClanSuitePanel panel;

	@Inject
	private Screenshotter screenshotter;

	@Inject
	private BotwApi api;

	@Inject
	private ClanSuiteConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navigationButton;

	@Provides
	ClanSuiteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanSuiteConfig.class);
	}

	@Override
	protected void startUp()
	{
		// The tracker is registered by hand rather than being a plugin of its own, so that it stops
		// listening the moment this plugin is switched off.
		eventBus.register(killTracker);

		// Registered by hand alongside the challenge tracker, so both stop listening the moment this
		// plugin is switched off.
		eventBus.register(eventTrackers);

		challenges.load();
		clans.load();
		outbox.load();
		observations.load();

		panel.setPlayerName(this::localPlayerName);

		// The panel refreshes itself once points land, so an open leaderboard catches up without the
		// player pressing anything.
		sender.setOnSent(panel::onPointsSent);
		sender.start();
		observationSender.start();

		// Evidence goes to the creator on a background thread. Best effort by design: the full-size
		// copy is already on the player's disk, so a failed upload costs a thumbnail rather than proof.
		screenshotter.setUploader((code, eventId, itemName, jpeg) -> executor.execute(() ->
		{
			String token = challenges.participantTokenFor(code);
			if (token == null)
			{
				return;
			}

			BotwApi.Result<BotwApi.Snapshot> result = api.uploadShot(
				config.serverUrl(), code, token, eventId, itemName, System.currentTimeMillis(), jpeg);

			if (!result.ok())
			{
				log.debug("Could not upload evidence for {}: {}", itemName, result.getError());
			}
		}));

		navigationButton = NavigationButton.builder()
			.tooltip("Clan Suite")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		// One last send on the way out, so a session's last few minutes of skilling are not lost.
		observationSender.flush();
		observationSender.stop();
		sender.stop();

		eventBus.unregister(killTracker);
		eventBus.unregister(eventTrackers);
		clientToolbar.removeNavigation(navigationButton);
	}

	/**
	 * The sidebar icon. Drawn rather than shipped as a file so there is one less thing to get wrong in
	 * the plugin hub's packaging.
	 */
	private BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(220, 138, 0));
		graphics.fillOval(1, 1, 14, 14);
		graphics.setColor(new Color(40, 40, 40));
		graphics.setFont(new Font("SansSerif", Font.BOLD, 10));
		graphics.drawString("B", 5, 12);
		graphics.dispose();
		return image;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// All three are stored per account, so they cannot be read until there is an account to read
		// them for. Logging in on a second character has to swap them over, not merge them — an
		// ironman and a main can be in different clans, and usually are.
		challenges.load();
		clans.load();
		outbox.load();
		observations.load();

		// Every skill is announced once on logging in. Without forgetting what they were, arriving on
		// a second account would look like earning its whole lifetime of experience in a second.
		eventTrackers.forgetTotals();

		// And the panel has to be told. It is built at start-up, before there is an account, so without
		// this it goes on showing the empty list it was born with until something else happens to
		// rebuild it — which is why the challenges only turned up after a trip through Create or Join.
		panel.refreshList();

		// A backlog from an earlier session goes out now rather than waiting for the next timer.
		sender.flush();
	}

	/**
	 * The name points are reported under. Null until logged in, which is why nothing is sent before
	 * then — a kill by "nobody" cannot be put on a leaderboard.
	 */
	public String localPlayerName()
	{
		return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
	}
}
