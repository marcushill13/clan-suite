package com.clansuite.botw.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the leaderboard, as the service computes it.
 */
@Data
@NoArgsConstructor
public class LeaderboardEntry
{
	private String rsn = "";
	private int points;
	private int kills;
	private int drops;

	/**
	 * Added by the creator rather than having joined from a plugin — a mobile player, whose kills
	 * nobody can see. Shown on their row, because a total that was typed in should not be passed off
	 * as one that was counted.
	 */
	private boolean manual;

	/** What the creator has added or taken away by hand. Zero for almost everyone. */
	private int adjustment;
}
