package com.clansuite.botw.track;

import com.clansuite.botw.data.Challenge;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The challenges this account has made or joined, and the tokens that prove it.
 * <p>
 * Kept per RuneScape account rather than per installation. Someone running an ironman alongside a main
 * is competing as two different people, and their challenges should not bleed into each other.
 * <p>
 * Tokens live here and nowhere else. The creator token is what allows a challenge to be edited and the
 * participant token is what allows points to be reported, so losing them means losing the challenge —
 * which is why they are written down the moment they arrive rather than held in memory.
 */
@Slf4j
@Singleton
public class ChallengeStore
{
	private static final String CONFIG_GROUP = "clansuite";

	/**
	 * The group Boss of the Week wrote under, read once so that nothing is lost on the way across.
	 * <p>
	 * Clan Suite keeps its own configuration rather than writing over that plugin's, so that a player
	 * who installs this while still running the old one does not have two plugins fighting over the
	 * same key. But a player switching over has challenges they have already joined and events that
	 * have not reached the service yet, and losing either would cost them points. So the old key is
	 * read when there is nothing here yet, and written back under this one.
	 */
	private static final String CARRIED_FROM = "botw";


	private static final String KEY = "challenges";

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Membership> memberships = new ArrayList<>();

	@Inject
	private ChallengeStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * What this account knows about one challenge.
	 */
	public static class Membership
	{
		@SerializedName("challenge")
		public Challenge challenge = new Challenge();

		/** Present when this account created it. Lets the challenge be edited. */
		@SerializedName("creatorToken")
		public String creatorToken;

		/** Present once joined. Lets points be reported. */
		@SerializedName("participantToken")
		public String participantToken;

		public boolean isCreator()
		{
			return creatorToken != null && !creatorToken.isEmpty();
		}

		public boolean isParticipant()
		{
			return participantToken != null && !participantToken.isEmpty();
		}
	}

	public void load()
	{
		memberships.clear();

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			json = configManager.getRSProfileConfiguration(CARRIED_FROM, KEY);

			if (json == null || json.isEmpty())
			{
				return;
			}

			// Brought across rather than read in place every time, so this happens once and the two
			// plugins stop sharing anything the moment it has.
			log.debug("Carrying joined challenges over from Boss of the Week");
			configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, json);
		}

		try
		{
			Type type = new TypeToken<List<Membership>>()
			{
			}.getType();

			List<Membership> stored = gson.fromJson(json, type);
			if (stored != null)
			{
				memberships.addAll(stored);
			}
		}
		catch (JsonSyntaxException e)
		{
			// Never throw away a token because one entry is unreadable; that would silently remove
			// someone from a challenge they had already joined.
			log.warn("Could not read saved challenges", e);
		}

		log.debug("Loaded {} challenges", memberships.size());
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(memberships));
	}

	public List<Membership> all()
	{
		return Collections.unmodifiableList(memberships);
	}

	/**
	 * The challenges whose kills should be counted. Everything joined, whether or not it is running —
	 * the tracker decides that per kill, because a challenge can start while the client is open.
	 */
	public List<Challenge> joined()
	{
		List<Challenge> joined = new ArrayList<>();
		for (Membership membership : memberships)
		{
			if (membership.isParticipant())
			{
				joined.add(membership.challenge);
			}
		}

		return joined;
	}

	@Nullable
	public Membership find(String code)
	{
		for (Membership membership : memberships)
		{
			if (membership.challenge.getCode().equalsIgnoreCase(code))
			{
				return membership;
			}
		}

		return null;
	}

	/**
	 * Adds a challenge, or updates one already known. Tokens already held are kept when the incoming
	 * copy has none — a refresh from the service carries the challenge but not the secrets.
	 */
	public void put(Challenge challenge, @Nullable String creatorToken, @Nullable String participantToken)
	{
		Membership existing = find(challenge.getCode());

		if (existing == null)
		{
			existing = new Membership();
			memberships.add(existing);
		}

		existing.challenge = challenge;

		if (creatorToken != null && !creatorToken.isEmpty())
		{
			existing.creatorToken = creatorToken;
		}

		if (participantToken != null && !participantToken.isEmpty())
		{
			existing.participantToken = participantToken;
		}

		save();
	}

	public void remove(String code)
	{
		memberships.removeIf(membership -> membership.challenge.getCode().equalsIgnoreCase(code));
		save();
	}

	@Nullable
	public String participantTokenFor(String code)
	{
		Membership membership = find(code);
		return membership == null ? null : membership.participantToken;
	}

	@Nullable
	public String creatorTokenFor(String code)
	{
		Membership membership = find(code);
		return membership == null ? null : membership.creatorToken;
	}
}
