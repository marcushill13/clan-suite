package com.clansuite.clan.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One of a clan's permanent records.
 * <p>
 * A name, a number, and what it was for — because a record without the last part is a boast rather
 * than a record. "1.2B" means nothing; "1.2B, from a Tumeken's shadow, at March's raid night" is the
 * thing people actually retell.
 */
@Data
@NoArgsConstructor
public class ClanRecord
{
	private String title = "";
	private String rsn = "";
	private long amount;

	/** What it was, where there is one: the item, the boss. Absent for records that are only a count. */
	private String detail;

	/** The event it happened at, where the record is about a single occasion. */
	private String event;

	private long at;
}
