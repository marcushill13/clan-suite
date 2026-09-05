package com.clansuite.ui;

import com.clansuite.botw.data.Challenge;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * How long until a challenge starts, or until it ends once it has.
 * <p>
 * The one question anyone opening this panel has, and it changes meaning halfway through the week
 * without the player doing anything. Kept apart from the panels so both of them phrase it the same
 * way.
 */
public final class Countdown
{
	private Countdown()
	{
	}

	/**
	 * A single line for the top of a challenge: what is being waited for, and how long.
	 */
	public static String describe(Challenge challenge, long now)
	{
		if (!challenge.hasStarted(now))
		{
			return "Starts in " + remaining(challenge.getStartsAt() - now);
		}

		if (!challenge.hasEnded(now))
		{
			return "Ends in " + remaining(challenge.getEndsAt() - now);
		}

		return "Finished";
	}

	/**
	 * Coarse on purpose. Nobody planning a week of bossing needs the seconds, and a figure that
	 * reprints every second is harder to read, not easier.
	 */
	public static String remaining(long millis)
	{
		if (millis <= 0)
		{
			return "moments";
		}

		long days = TimeUnit.MILLISECONDS.toDays(millis);
		long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

		if (days > 0)
		{
			return days + (days == 1 ? " day " : " days ") + hours + "h";
		}

		if (hours > 0)
		{
			return hours + "h " + minutes + "m";
		}

		// Under an hour the minutes are what matter, and seconds only in the last stretch.
		if (minutes > 0)
		{
			return minutes + "m";
		}

		return TimeUnit.MILLISECONDS.toSeconds(millis) + "s";
	}

	/**
	 * A start or end time written in the timezone the creator chose, so what everyone reads is the
	 * time the creator meant rather than whatever their own machine is set to.
	 */
	public static String at(long epochMillis, String timezone)
	{
		ZoneId zone;
		try
		{
			zone = ZoneId.of(timezone);
		}
		catch (Exception e)
		{
			zone = ZoneId.systemDefault();
		}

		ZonedDateTime when = Instant.ofEpochMilli(epochMillis).atZone(zone);
		return when.format(DateTimeFormatter.ofPattern("d MMM, h:mma")) + " " + zone.getId();
	}
}
