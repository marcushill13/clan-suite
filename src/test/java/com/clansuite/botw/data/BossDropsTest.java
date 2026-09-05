package com.clansuite.botw.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Working out which pet dropped, which the game does not say.
 */
public class BossDropsTest
{
	private static final Set<String> PETS =
		new HashSet<>(Arrays.asList("vorki", "olmlet", "little nightmare", "beaver"));

	@Test
	public void thePetIsTheOneThePlayersAreCompetingFor()
	{
		assertEquals("Vorki", BossDrops.petIn(challenge(
			new DropRule("Draconic visage", 11286, 100),
			new DropRule("Vorki", 21992, 500)), PETS));
	}

	/**
	 * Typed by the creator rather than filled in from the bundled list, which is what happens at every
	 * raid — the wiki gave those bosses no uniques, so their pets are always hand-typed.
	 */
	@Test
	public void aHandTypedPetCountsTheSame()
	{
		assertEquals("olmlet", BossDrops.petIn(challenge(new DropRule("olmlet", -1, 400)), PETS));
	}

	@Test
	public void aChallengeThatCountsNoPetHasNone()
	{
		assertNull(BossDrops.petIn(challenge(new DropRule("Draconic visage", 11286, 100)), PETS));
		assertNull(BossDrops.petIn(challenge(), PETS));
		assertNull(BossDrops.petIn(null, PETS));
	}

	/** A pet on the list for nothing is a pet the creator took off it. */
	@Test
	public void aPetWorthNothingIsNotCounted()
	{
		assertNull(BossDrops.petIn(challenge(new DropRule("Vorki", 21992, 0)), PETS));
	}

	/**
	 * No boss drops two pets, so this is a challenge nobody will run — but guessing between them would
	 * be inventing a drop, and scoring nothing is the honest answer.
	 */
	@Test
	public void twoPetsCannotBeToldApart()
	{
		assertNull(BossDrops.petIn(challenge(
			new DropRule("Vorki", 21992, 500),
			new DropRule("Little nightmare", 24491, 500)), PETS));
	}

	private static Challenge challenge(DropRule... drops)
	{
		Challenge challenge = new Challenge();
		challenge.setCode("VORK01");
		challenge.setDrops(drops.length == 0 ? Collections.emptyList() : Arrays.asList(drops));
		return challenge;
	}
}
