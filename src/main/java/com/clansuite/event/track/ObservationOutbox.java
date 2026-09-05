package com.clansuite.event.track;

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
 * Everything seen that has not reached the service yet.
 * <p>
 * The same arrangement the challenges use, and for the same reason: what happened is written down the
 * moment it happens and sent afterwards. Somebody dying at Vardorvis and logging out in a temper
 * should not lose the six kills before it, and a raid team whose host crashes at the chest should not
 * lose the raid.
 * <p>
 * Written to configuration as soon as it is recorded, so it survives a client restart, and cleared
 * only once the service has confirmed it.
 */
@Slf4j
@Singleton
public class ObservationOutbox
{
	private static final String CONFIG_GROUP = "clansuite";
	private static final String KEY = "observations";

	/** The service refuses more than fifty at once, and a long backlog drains better in pieces. */
	private static final int BATCH = 25;

	/**
	 * A backlog this long means something is badly wrong — the service unreachable for days. Older
	 * observations are dropped rather than growing configuration without limit.
	 */
	private static final int MAX_PENDING = 5000;

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Observation> pending = new ArrayList<>();

	private Runnable onAdded = () ->
	{
	};

	@Inject
	private ObservationOutbox(ConfigManager configManager, Gson gson)
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
			return;
		}

		try
		{
			Type type = new TypeToken<List<Observation>>()
			{
			}.getType();

			List<Observation> stored = gson.fromJson(json, type);
			if (stored != null)
			{
				pending.addAll(stored);
			}
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Could not read what was waiting to be sent", e);
		}
	}

	public void setOnAdded(Runnable onAdded)
	{
		this.onAdded = onAdded;
	}

	public synchronized void add(Collection<Observation> observations)
	{
		if (observations.isEmpty())
		{
			return;
		}

		pending.addAll(observations);

		while (pending.size() > MAX_PENDING)
		{
			pending.remove(0);
		}

		save();
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
	 * The next batch, grouped by the event it belongs to, because that is how the service takes them.
	 */
	public synchronized Map<String, List<Observation>> nextBatch()
	{
		Map<String, List<Observation>> batch = new LinkedHashMap<>();
		int taken = 0;

		for (Observation observation : pending)
		{
			if (taken >= BATCH)
			{
				break;
			}

			batch.computeIfAbsent(observation.getEventCode(), code -> new ArrayList<>()).add(observation);
			taken++;
		}

		return batch;
	}

	/** Only what the service has actually confirmed, which is what makes a failed send harmless. */
	public synchronized void confirm(Collection<Observation> sent)
	{
		pending.removeAll(sent);
		save();
	}

	/** Throws away anything queued for an event that no longer exists or was never joined. */
	public synchronized void forget(String eventCode)
	{
		pending.removeIf(observation -> observation.getEventCode().equalsIgnoreCase(eventCode));
		save();
	}

	public synchronized List<Observation> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(pending));
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(pending));
	}
}
