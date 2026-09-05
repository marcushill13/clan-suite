package com.clansuite.clan.data;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One person's history with their clan.
 */
@Data
@NoArgsConstructor
public class PlayerStatistics
{
	private String rsn = "";

	private int attended;

	/** How many the clan has held, so "eleven of fourteen" can be said rather than just "eleven". */
	private int eventsHeld;

	private int won;

	private long points;

	/** Their best single event. */
	private long best;

	/** Events in a row not missed, now and at its longest. */
	private int streak;
	private int longestStreak;

	private Map<String, Long> totals = new LinkedHashMap<>();

	public long of(String metric)
	{
		Long found = totals == null ? null : totals.get(metric);
		return found == null ? 0 : found;
	}
}
