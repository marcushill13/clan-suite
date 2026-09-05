package com.clansuite.botw.track;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Everything that has happened and has not reached the service yet.
 * <p>
 * Kills are written down first and sent afterwards. That ordering is the whole point: a player is
 * regularly disconnected, offline, or logging out the moment a pet drops, and none of that should
 * cost them the points. An event survives a client restart because it is written to configuration as
 * soon as it is recorded, and leaves only once the service has confirmed it.
 * <p>
 * Every event carries an id made when it happened and never changed, so sending is always safe to
 * retry. The plugin never has to work out whether a request that timed out actually landed — it sends
 * again, and the service ignores what it already has.
 */
@Slf4j
@Singleton
public class Outbox
{
	private static final String CONFIG_GROUP = "clansuite";

	/**
	 * The group Boss of the Week wrote under, read once so that nothing is lost on the way across.
	 * <p>
	 * Clan Suite keeps its own configuration rather than writing over that plugin's, so that a player
	 * who installs this while still running the old one does not have two plugins fighting over the
	 * same key. But a player switching over has challenges they have already joined and events that
	 * have not reached the service yet, and losing either would cost them points. So the old key is
	 * read when there is nothing here yet, and written back under this one.
	 */
	private static final String CARRIED_FROM = "botw";


	private static final String KEY = "outbox";

	/**
	 * The service refuses more than fifty at once. Sending in batches also means a long backlog drains
	 * steadily rather than in one request that might time out and achieve nothing.
	 */
	private static final int BATCH = 25;

	/**
	 * A backlog this long means something is badly wrong — the service unreachable for days, most
	 * likely. Older events are dropped rather than growing configuration without limit.
	 */
	private static final int MAX_PENDING = 5000;

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<PendingEvent> pending = new ArrayList<>();

	@Inject
	private Outbox(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void load()
	{
		pending.clear();

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			json = configManager.getRSProfileConfiguration(CARRIED_FROM, KEY);

			if (json == null || json.isEmpty())
			{
				return;
			}

			// Brought across rather than read in place every time, so this happens once and the two
			// plugins stop sharing anything the moment it has.
			log.debug("Carrying unsent events over from Boss of the Week");
			configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, json);
		}

		try
		{
			Type type = new TypeToken<List<PendingEvent>>()
			{
			}.getType();

			List<PendingEvent> stored = gson.fromJson(json, type);
			if (stored != null)
			{
				pending.addAll(stored);
			}
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Could not read the pending events", e);
		}

		if (!pending.isEmpty())
		{
			log.debug("{} events still to send", pending.size());
		}
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(pending));
	}

	/**
	 * Told whenever something is put in, so a kill can be sent shortly afterwards rather than waiting
	 * for the next scheduled sweep. Set by whoever does the sending; does nothing until then.
	 */
	private Runnable onAdded = () ->
	{
	};

	public void setOnAdded(Runnable onAdded)
	{
		this.onAdded = onAdded;
	}

	public void add(Collection<PendingEvent> events)
	{
		synchronized (this)
		{
			pending.addAll(events);

			while (pending.size() > MAX_PENDING)
			{
				pending.remove(0);
			}

			save();
		}

		// Outside the lock. This ends up scheduling work that will come back here for the events, and
		// calling it while holding the monitor is how that turns into a deadlock one day.
		onAdded.run();
	}

	public synchronized boolean isEmpty()
	{
		return pending.isEmpty();
	}

	public synchronized int size()
	{
		return pending.size();
	}

	/**
	 * The next batch to send, grouped by the challenge it belongs to.
	 * <p>
	 * Grouped because the service takes one challenge per request, and a single kill can produce events
	 * for several challenges at once when a player has joined more than one for the same boss.
	 */
	public synchronized Map<String, List<PendingEvent>> nextBatch()
	{
		Map<String, List<PendingEvent>> byChallenge = new LinkedHashMap<>();

		for (PendingEvent event : pending)
		{
			List<PendingEvent> batch =
				byChallenge.computeIfAbsent(event.getChallengeCode(), code -> new ArrayList<>());

			if (batch.size() < BATCH)
			{
				batch.add(event);
			}
		}

		return byChallenge;
	}

	/**
	 * Forgets events the service has confirmed.
	 * <p>
	 * Only called after a successful response. Anything else — a timeout, a refused connection, a
	 * server error — leaves the events exactly where they are, to be tried again.
	 */
	public synchronized void confirm(Collection<PendingEvent> sent)
	{
		List<String> ids = new ArrayList<>();
		for (PendingEvent event : sent)
		{
			ids.add(event.getId());
		}

		pending.removeIf(event -> ids.contains(event.getId()));
		save();
	}

	/**
	 * Drops everything belonging to a challenge, for when it is left or deleted. There is nobody left
	 * to report to.
	 */
	public synchronized void forget(String challengeCode)
	{
		pending.removeIf(event -> event.getChallengeCode().equalsIgnoreCase(challengeCode));
		save();
	}

	public synchronized List<PendingEvent> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(pending));
	}
}
