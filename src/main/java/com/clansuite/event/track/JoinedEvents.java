package com.clansuite.event.track;

import com.clansuite.ClanSuiteConfig;
import com.clansuite.clan.ClanStore;
import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.net.EventApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The events this account is taking part in and that are running right now.
 * <p>
 * Held in memory and refreshed on a timer rather than asked for as things happen: a boss dies every
 * minute or two and the calendar changes about once a week, so asking the service on every kill would
 * be thousands of requests to learn something that has not moved.
 * <p>
 * Nothing is reported to an event the player has not joined. That is the difference between a
 * competition and surveillance — somebody in a clan that is running a raid night has not agreed to
 * have their evening counted unless they said they were coming.
 */
@Slf4j
@Singleton
public class JoinedEvents
{
	private final ClanStore clans;
	private final EventApi api;
	private final ClanSuiteConfig config;

	private volatile List<ClanEvent> events = Collections.emptyList();

	@Inject
	private JoinedEvents(ClanStore clans, EventApi api, ClanSuiteConfig config)
	{
		this.clans = clans;
		this.api = api;
		this.config = config;
	}

	/**
	 * Asks the service what is on. Never on the client thread; called from the sender's timer.
	 */
	public void refresh()
	{
		ClanStore.Membership mine = clans.membership();
		if (mine == null || mine.getToken() == null || mine.getToken().isEmpty())
		{
			events = Collections.emptyList();
			return;
		}

		EventApi.Result<List<ClanEvent>> result =
			api.forClan(config.serverUrl(), mine.getCode(), mine.getToken());

		if (!result.ok())
		{
			// Left as it was. A calendar that could not be fetched is not an empty calendar, and
			// throwing it away would stop tracking an event that is running right now.
			log.debug("Could not refresh the calendar: {}", result.getError());
			return;
		}

		List<ClanEvent> joined = new ArrayList<>();
		for (ClanEvent event : result.getValue())
		{
			if (event.isJoined())
			{
				joined.add(event);
			}
		}

		events = Collections.unmodifiableList(joined);
	}

	public void clear()
	{
		events = Collections.emptyList();
	}

	/**
	 * The events a thing that just happened should be reported to: joined, running, and interested in
	 * this kind of thing.
	 *
	 * @param metric what was seen — {@code kc}, {@code drop}, {@code xp}, {@code death}, {@code completion}
	 */
	public List<ClanEvent> watching(String metric, long now)
	{
		List<ClanEvent> interested = new ArrayList<>();

		for (ClanEvent event : events)
		{
			if (event.isRunning(now) && tracks(event, metric))
			{
				interested.add(event);
			}
		}

		return interested;
	}

	/**
	 * Whether an event asked for this.
	 * <p>
	 * An event that says nothing about what it tracks is taken to want everything, because that is
	 * what somebody who left the box empty meant — not that their event should count nothing.
	 */
	static boolean tracks(ClanEvent event, String metric)
	{
		if (event.getConfig() == null || !event.getConfig().has("track"))
		{
			return true;
		}

		try
		{
			for (com.google.gson.JsonElement element : event.getConfig().getAsJsonArray("track"))
			{
				if (element.getAsString().equalsIgnoreCase(metric))
				{
					return true;
				}
			}
		}
		catch (RuntimeException e)
		{
			// Configuration written by a newer plugin, or by hand. Better to count than to drop it.
			return true;
		}

		return false;
	}
}
