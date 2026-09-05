package com.clansuite.clan;

import com.clansuite.clan.data.Role;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Which clan this account belongs to, and the token that proves it.
 * <p>
 * Kept per RuneScape account rather than per installation, for the same reason the challenges are:
 * somebody running an ironman alongside a main is two different people to their clan, and may well be
 * in two different ones.
 * <p>
 * Only one clan at a time. The game allows one, and a plugin that allowed several would have to ask
 * which one every event belonged to — a question nobody wants to be asked twice a night.
 */
@Slf4j
@Singleton
public class ClanStore
{
	private static final String CONFIG_GROUP = "clansuite";
	private static final String KEY = "clan";

	private final ConfigManager configManager;
	private final Gson gson;

	private Membership membership;

	@Inject
	private ClanStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * A clan this account is in, and what it may do there.
	 * <p>
	 * The rank is remembered so the panel can draw itself before the service has answered. It is not
	 * to be trusted for anything else: what somebody may actually do is decided where it cannot be
	 * edited, and a rank saved here is a week out of date the moment they are promoted.
	 */
	@Data
	@NoArgsConstructor
	public static class Membership
	{
		private String code = "";
		private String name = "";

		/** The account this belongs to, so a shared installation cannot mix two people up. */
		private String rsn = "";

		private String token = "";

		/** Last known, for drawing before the service has been asked. */
		private String role = "member";

		public Role role()
		{
			return Role.of(role);
		}
	}

	public void load()
	{
		membership = null;

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		try
		{
			membership = gson.fromJson(json, Membership.class);
		}
		catch (JsonSyntaxException e)
		{
			// Never throw the token away over one unreadable entry: that would put somebody out of
			// their own clan and leave them unable to get back in without being re-accepted.
			log.warn("Could not read the saved clan", e);
		}
	}

	public Membership membership()
	{
		return membership;
	}

	public boolean isInAClan()
	{
		return membership != null && membership.getToken() != null && !membership.getToken().isEmpty();
	}

	public String tokenFor(String code)
	{
		return membership != null && membership.getCode().equalsIgnoreCase(code)
			? membership.getToken()
			: null;
	}

	public void put(String code, String name, String rsn, String token, String role)
	{
		Membership stored = new Membership();
		stored.setCode(code);
		stored.setName(name);
		stored.setRsn(rsn);
		stored.setToken(token);
		stored.setRole(role == null ? "member" : role);

		membership = stored;
		save();
	}

	/** Called after the service answers, so a promotion shows up without having to rejoin. */
	public void rememberRole(String role)
	{
		if (membership != null && role != null && !role.equals(membership.getRole()))
		{
			membership.setRole(role);
			save();
		}
	}

	public void rememberName(String name)
	{
		if (membership != null && name != null && !name.equals(membership.getName()))
		{
			membership.setName(name);
			save();
		}
	}

	public void forget()
	{
		membership = null;

		if (configManager.getRSProfileKey() != null)
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, KEY);
		}
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(membership));
	}
}
