package com.clansuite.botw.track;

import com.clansuite.ClanSuiteConfig;
import com.clansuite.botw.net.BotwApi;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Drains the outbox to the service.
 * <p>
 * Runs on a timer rather than sending as kills happen. A boss dies every minute or two and the
 * leaderboard is read by people, not machines — nobody needs their kill to appear within the second,
 * and batching keeps a week of killing to a few hundred requests instead of thousands.
 * <p>
 * Nothing here is on the client thread. A request that hangs would freeze the game if it were.
 */
@Slf4j
@Singleton
public class EventSender
{
	/**
	 * The safety net: catches whatever a failed send left behind, and anything held over from a
	 * previous session. Not what makes a kill appear — {@link #nudge()} does that.
	 */
	private static final int EVERY_SECONDS = 60;

	/**
	 * How long after a kill it gets sent.
	 * <p>
	 * Long enough that a boss killed three times over goes in one request rather than three, short
	 * enough that nobody waits for it. Relying on the minute sweep meant a kill could take a full
	 * minute to show, and pressing Refresh in the meantime did not help: that only re-reads the
	 * service, which had not been told yet.
	 */
	private static final int SOON_SECONDS = 5;

	private final ScheduledExecutorService executor;
	private final Outbox outbox;
	private final ChallengeStore challenges;
	private final BotwApi api;
	private final ClanSuiteConfig config;

	private ScheduledFuture<?> scheduled;

	/** The one-off send that follows a kill. Held so a burst of kills schedules one, not one each. */
	private ScheduledFuture<?> soon;

	/** Told after every successful send, so the panel can refresh without polling separately. */
	private Runnable onSent = () ->
	{
	};

	@Inject
	private EventSender(
		ScheduledExecutorService executor,
		Outbox outbox,
		ChallengeStore challenges,
		BotwApi api,
		ClanSuiteConfig config)
	{
		this.executor = executor;
		this.outbox = outbox;
		this.challenges = challenges;
		this.api = api;
		this.config = config;
	}

	public void setOnSent(Runnable onSent)
	{
		this.onSent = onSent;
	}

	public void start()
	{
		stop();

		// Anything put in the outbox from here on brings a send with it.
		outbox.setOnAdded(this::nudge);

		scheduled = executor.scheduleWithFixedDelay(
			this::flush, EVERY_SECONDS, EVERY_SECONDS, TimeUnit.SECONDS);
	}

	public void stop()
	{
		outbox.setOnAdded(() ->
		{
		});

		if (scheduled != null)
		{
			scheduled.cancel(false);
			scheduled = null;
		}

		if (soon != null)
		{
			soon.cancel(false);
			soon = null;
		}
	}

	/**
	 * Sends in a few seconds rather than at the next sweep.
	 * <p>
	 * Ignored if one is already on its way, which is what keeps a run of kills to a single request:
	 * the first one books the send and the rest ride along with it.
	 */
	public synchronized void nudge()
	{
		if (soon != null && !soon.isDone())
		{
			return;
		}

		soon = executor.schedule(this::flush, SOON_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Throws away anything queued for a challenge that no longer exists.
	 */
	public void forget(String challengeCode)
	{
		outbox.forget(challengeCode);
	}

	/**
	 * Sends what is waiting. Safe to call at any time and safe to call twice — an event the service has
	 * already seen is ignored by it, and only a confirmed send clears anything locally.
	 */
	public void flush()
	{
		try
		{
			if (outbox.isEmpty())
			{
				return;
			}

			boolean sentAnything = false;

			for (Map.Entry<String, List<PendingEvent>> batch : outbox.nextBatch().entrySet())
			{
				String code = batch.getKey();
				String token = challenges.participantTokenFor(code);

				if (token == null)
				{
					// Joined on another account, or left the challenge. There is nobody to report to,
					// and keeping these forever would mean retrying them forever.
					log.debug("Dropping {} events for {}: not a participant", batch.getValue().size(), code);
					outbox.forget(code);
					continue;
				}

				BotwApi.Result<BotwApi.Snapshot> result =
					api.submit(config.serverUrl(), code, token, batch.getValue());

				if (result.isGone())
				{
					// The challenge has been deleted. These events have nowhere to go and never will, so
					// retrying them every minute for as long as the plugin is installed helps nobody.
					// The challenge itself stays on the player's list until they are asked about it.
					log.debug("Dropping {} events for {}: the challenge no longer exists",
						batch.getValue().size(), code);
					outbox.forget(code);
					continue;
				}

				if (!result.ok())
				{
					// Left in place deliberately. The next run tries again, and the ids mean a request
					// that actually landed before timing out will not count twice.
					log.debug("Could not send {} events for {}: {}",
						batch.getValue().size(), code, result.getError());
					continue;
				}

				outbox.confirm(batch.getValue());
				sentAnything = true;
			}

			if (sentAnything)
			{
				onSent.run();
			}
		}
		catch (Exception e)
		{
			// A scheduled task that throws stops being scheduled, which would quietly end all tracking.
			log.warn("Sending failed", e);
		}
	}
}
