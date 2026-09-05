package com.clansuite.botw.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The bosses that can be picked, and the drops each is known for.
 * <p>
 * Bundled rather than fetched, so creating a challenge works offline and the wiki is not asked for the
 * same thing by every member of a clan. See {@code scripts/generate-boss-drops.mjs} for where it comes
 * from.
 * <p>
 * The uniques are a starting point, not a ruling. They are inferred from drop rarity, which catches
 * the pets and the visages but also lets the odd bolt tip through, so the create screen lets the list
 * be edited. Saving the creator typing out sixteen items matters more than being right about the
 * seventeenth.
 * <p>
 * The pet names are kept separately from the bosses because a pet is not announced like other loot.
 * The game says one has dropped without saying which, so the tracker has to work that out from the
 * challenge, and this is the list it works it out against. See {@link #petIn}.
 */
@Slf4j
@Singleton
public class BossDrops
{
	private static final String RESOURCE = "/com/clansuite/botw/boss-drops.json";

	private final List<Boss> bosses;
	private final String attribution;

	/** Lowercased, because a rule's name is typed by the creator and a drop's comes from the game. */
	private final Set<String> pets;

	@Inject
	private BossDrops(Gson gson)
	{
		File file = load(gson);
		this.bosses = file.bosses == null ? Collections.emptyList() : file.bosses;
		this.attribution = file.attribution == null ? "" : file.attribution;
		this.pets = lowercased(file.pets);
	}

	private static Set<String> lowercased(List<String> names)
	{
		Set<String> set = new HashSet<>();

		if (names != null)
		{
			for (String name : names)
			{
				if (name != null && !name.trim().isEmpty())
				{
					set.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
		}

		return set;
	}

	private static File load(Gson gson)
	{
		try (InputStream stream = BossDrops.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Boss data is missing; no boss can be picked");
				return new File();
			}

			File file = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), File.class);
			return file == null ? new File() : file;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read the boss data", e);
			return new File();
		}
	}

	public List<Boss> all()
	{
		return Collections.unmodifiableList(bosses);
	}

	/**
	 * Required credit for the wiki's data.
	 */
	public String getAttribution()
	{
		return attribution;
	}

	/**
	 * Bosses whose name contains this, case-insensitively. An empty query returns everything, so the
	 * list is browsable before anything is typed.
	 */
	public List<Boss> search(String query)
	{
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Boss> matches = new ArrayList<>();

		for (Boss boss : bosses)
		{
			if (needle.isEmpty() || boss.getName().toLowerCase(Locale.ROOT).contains(needle))
			{
				matches.add(boss);
			}
		}

		return matches;
	}

	/** Whether this item is somebody's pet. Every pet in the game, not only the ones bosses drop. */
	public boolean isPet(String itemName)
	{
		return itemName != null && pets.contains(itemName.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * The pet this challenge counts, or null if it counts none.
	 * <p>
	 * Needed because the game announces a pet without naming it — "you have a funny feeling like you're
	 * being followed" and nothing more. What dropped therefore has to be worked out rather than read,
	 * and the challenge's own list is what it is worked out from: a Vorkath challenge that scores Vorki
	 * can only have meant Vorki.
	 * <p>
	 * Reading the challenge rather than the boss is deliberate. Half the raid bosses came out of the
	 * wiki with no uniques at all, so their creators type the pet in by hand — and going by the boss
	 * would leave exactly those challenges unable to score the pet they were set up for.
	 */
	public String petIn(Challenge challenge)
	{
		return petIn(challenge, pets);
	}

	/**
	 * @param pets lowercased pet names
	 * @return the one pet the challenge scores, or null where there is no single answer
	 */
	static String petIn(Challenge challenge, Set<String> pets)
	{
		if (challenge == null || challenge.getDrops() == null)
		{
			return null;
		}

		String found = null;

		for (DropRule rule : challenge.getDrops())
		{
			if (rule == null || rule.getName() == null || rule.getPoints() <= 0
				|| !pets.contains(rule.getName().trim().toLowerCase(Locale.ROOT)))
			{
				continue;
			}

			// Two pets on one challenge is not something a boss can produce, and guessing between them
			// would be inventing a drop nobody had. Better to score nothing than the wrong thing.
			if (found != null)
			{
				return null;
			}

			found = rule.getName();
		}

		return found;
	}

	public Boss byName(String name)
	{
		for (Boss boss : bosses)
		{
			if (boss.getName().equalsIgnoreCase(name))
			{
				return boss;
			}
		}

		return null;
	}

	/**
	 * One boss and the drops it is known for.
	 */
	public static class Boss
	{
		private String name = "";
		private List<Unique> uniques = new ArrayList<>();

		public String getName()
		{
			return name;
		}

		public List<Unique> getUniques()
		{
			return uniques == null ? Collections.emptyList() : uniques;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static class Unique
	{
		private String name = "";
		private String rarity = "";
		private int oneIn;

		/**
		 * Resolved at build time, because the plugin's own item search reads the price API and that only
		 * knows tradeable things — every pet came out without an icon, which is the half of the list
		 * people care about most.
		 */
		private int itemId = -1;

		public String getName()
		{
			return name;
		}

		/** As the wiki writes it, e.g. "1/3000". Shown so the creator can judge what it is worth. */
		public String getRarity()
		{
			return rarity;
		}

		public int getOneIn()
		{
			return oneIn;
		}

		public int getItemId()
		{
			return itemId;
		}
	}

	/** Mirrors the generated JSON. */
	private static class File
	{
		int dataVersion;
		String source;
		String attribution;
		String generatedAt;
		List<String> pets = new ArrayList<>();
		List<Boss> bosses = new ArrayList<>();
	}
}
