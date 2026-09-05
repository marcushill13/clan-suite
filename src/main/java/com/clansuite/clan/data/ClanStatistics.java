package com.clansuite.clan.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a clan has done together, and the table of who has done what.
 */
@Data
@NoArgsConstructor
public class ClanStatistics
{
	private Totals clan = new Totals();
	private List<Member> members = new ArrayList<>();

	@Data
	@NoArgsConstructor
	public static class Totals
	{
		private int eventsHeld;

		/** Turn-ups rather than people: somebody at forty events counts forty times. */
		private int attendances;

		/** How many different people have ever taken part. */
		private int people;

		private long points;

		/**
		 * Everything counted, by metric — kills, experience, completions, deaths, coins.
		 * <p>
		 * A map rather than fields because the metrics will grow, and a clan that upgrades its plugin
		 * before the service should still see the ones it knows about.
		 */
		private Map<String, Long> totals = new LinkedHashMap<>();

		public long of(String metric)
		{
			Long found = totals == null ? null : totals.get(metric);
			return found == null ? 0 : found;
		}
	}

	@Data
	@NoArgsConstructor
	public static class Member
	{
		private String rsn = "";
		private int attended;
		private long points;
		private int won;
	}

	public List<Member> getMembers()
	{
		return members == null ? Collections.emptyList() : members;
	}
}
