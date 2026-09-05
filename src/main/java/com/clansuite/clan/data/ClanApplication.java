package com.clansuite.clan.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Somebody asking to join, waiting on an answer. */
@Data
@NoArgsConstructor
public class ClanApplication
{
	private String rsn = "";

	/** Why they should be let in, in their own words. Optional, and often empty. */
	private String message = "";

	private long appliedAt;
}
