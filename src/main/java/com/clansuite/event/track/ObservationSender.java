package com.clansuite.event.track;

import com.clansuite.ClanSuiteConfig;
import com.clansuite.clan.ClanStore;
import com.clansuite.event.net.EventApi;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Drains what was seen to the service, and keeps the calendar it is measured against up to date.
 * <p>
 * On a timer rather than as things happen, for the same reason the challenge sender is: a boss dies
 * every minute or two and a leaderboard is read by people, not machines. Batching keeps a raid night
 * to a few dozen requests rather than a few thousand.
 * <p>
 * Nothing here runs on the client thread. A request that hangs would freeze the game if it did.
 */
@Slf4j
@Singleton
public class ObservationSender
{
	/** The safety net: whatever a failed send left behind, and anything held over from last session. */
	private static final int EVERY_SECONDS = 60;

	/** How long after something happens it goes out. Long enough to travel with its neighbours. */
	private static final int SOON_SECONDS = 5;

	/**
	 * How often the calendar is re-read.
	 * <p>
	 * Events are made days ahead and start on the hour; nothing is lost by learning about one a couple
	 * of minutes late, and asking more often would be thousands of requests to be told nothing changed.
	 */
	private static final int CALENDAR_SECONDS = 120;

	private final ScheduledExecutorService executor;
	private final ObservationOutbox outbox;
	private final JoinedEvents joined;
	private final EventTrackers trackers;
	private final ClanStore clans;
	private final EventApi api;
	private final ClanSuiteConfig config;

	private ScheduledFuture<?> sending;
	private ScheduledFuture<?> calendar;
	private ScheduledFuture<?> soon;

	@Inject
	private ObservationSender(
		ScheduledExecutorService executor,
		ObservationOutbox outbox,
		JoinedEvents joined,
		EventTrackers trackers,
		ClanStore clans,
		EventApi api,
		ClanSuiteConfig config)
	{
		this.executor = executor;
		this.outbox = outbox;
		this.joined = joined;
		this.trackers = trackers;
		this.clans = clans;
		this.api = api;
		this.config = config;
	}

	public void start()
	{
		stop();

		outbox.setOnAdded(this::nudge);

		sending = executor.scheduleWithFixedDelay(
			this::flush, EVERY_SECONDS, EVERY_SECONDS, TimeUnit.SECONDS);

		// The first read is immediate: somebody who starts the client during an event should be
		// counted from their first kill, not from two minutes in.
		calendar = executor.scheduleWithFixedDelay(
			this::refresh, 0, CALENDAR_SECONDS, TimeUnit.SECONDS);
	}

	public void stop()
	{
		outbox.setOnAdded(() ->
		{
		});

		for (ScheduledFuture<?> task : new ScheduledFuture<?>[]{sending, calendar, soon})
		{
			if (task != null)
			{
				task.cancel(false);
			}
		}

		sending = null;
		calendar = null;
		soon = null;
	}

	/** Sends in a few seconds rather than at the next sweep. A burst of kills books one send. */
	public synchronized void nudge()
	{
		if (soon != null && !soon.isDone())
		{
			return;
		}

		soon = executor.schedule(this::flush, SOON_SECONDS, TimeUnit.SECONDS);
	}

	private void refresh()
	{
		try
		{
			joined.refresh();
		}
		catch (Exception e)
		{
			// A scheduled task that throws stops being scheduled, which would quietly end all tracking.
			log.warn("Could not read the calendar", e);
		}
	}

	/**
	 * Sends what is waiting. Safe to call at any time and safe to call twice — the service ignores an
	 * observation it already has, and only a confirmed send clears anything locally.
	 */
	public void flush()
	{
		try
		{
			// Experience is held in memory until now, so a sweep with nothing else to do still carries
			// the last few minutes of skilling.
			trackers.flushExperience();

			if (outbox.isEmpty())
			{
				return;
			}

			ClanStore.Membership mine = clans.membership();
			if (mine == null || mine.getToken() == null || mine.getToken().isEmpty())
			{
				// Nobody to report as. Kept rather than dropped: they may be between clans, and what
				// they did during an event they had joined is still theirs.
				return;
			}

			for (Map.Entry<String, List<Observation>> batch : outbox.nextBatch().entrySet())
			{
				EventApi.Result<Integer> result =
					api.report(config.serverUrl(), batch.getKey(), mine.getToken(), batch.getValue());

				if (result.isGone())
				{
					// The event was deleted, or was never one this account could report to. These have
					// nowhere to go and never will.
					log.debug("Dropping {} observations for {}: it is gone",
						batch.getValue().size(), batch.getKey());
					outbox.forget(batch.getKey());
					continue;
				}

				if (!result.ok())
				{
					// Left in place deliberately. The next sweep tries again, and the ids mean a
					// request that landed before timing out will not count twice.
					log.debug("Could not send {} observations for {}: {}",
						batch.getValue().size(), batch.getKey(), result.getError());
					continue;
				}

				outbox.confirm(batch.getValue());
			}
		}
		catch (Exception e)
		{
			log.warn("Sending failed", e);
		}
	}
}
