package com.clansuite.clan.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/** One person on the roster. */
@Data
@NoArgsConstructor
public class ClanMember
{
	private String rsn = "";

	/** As the service spells it. Read through {@link #role()} rather than compared as text. */
	private String role = "member";

	private long joinedAt;

	public Role role()
	{
		return Role.of(role);
	}
}
