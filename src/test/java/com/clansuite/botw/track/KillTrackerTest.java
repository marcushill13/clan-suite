package com.clansuite.botw.track;

import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rules that decide whether something counts, all of which have been wrong at some point.
 */
public class KillTrackerTest
{
	/**
	 * The bug this was written for: one Scurrius, two kills.
	 * <p>
	 * The Loot Tracker re-announces every NPC kill as a {@code LootReceived}, so a plugin listening to
	 * both events sees an ordinary boss twice. Counting it here would double every kill count in the
	 * competition for everyone running the Loot Tracker, which is the default.
	 */
	@Test
	public void npcLootIsLeftToTheOtherListener()
	{
		assertFalse(KillTracker.countsAsKill(LootRecordType.NPC));
	}

	/** Chest payouts are the whole reason this event is listened to; nothing else reports a raid. */
	@Test
	public void chestPayoutsCount()
	{
		assertTrue(KillTracker.countsAsKill(LootRecordType.EVENT));
	}

	@Test
	public void killingPeopleAndRobbingThemAreNotBossKills()
	{
		assertFalse(KillTracker.countsAsKill(LootRecordType.PLAYER));
		assertFalse(KillTracker.countsAsKill(LootRecordType.PICKPOCKET));
	}

	@Test
	public void bossNamesMatchWhateverTheGameCallsThem()
	{
		assertTrue(KillTracker.matches("Scurrius", "Scurrius"));
		assertTrue(KillTracker.matches("scurrius", "SCURRIUS"));

		// The game adds a form or a level in brackets; the challenge does not.
		assertTrue(KillTracker.matches("Dagannoth Rex", "Dagannoth Rex (Level 303)"));
		assertTrue(KillTracker.matches("Kalphite Queen (Second form)", "Kalphite Queen"));
	}

	/**
	 * All three, because the third is the bug that started this.
	 * <p>
	 * A clan member killed the boss with a full inventory and a pet already following them, so theirs
	 * went to Probita and the game said "would have been followed" rather than "being followed".
	 * Reading only the usual message would have gone on missing exactly that case.
	 */
	@Test
	public void everyWayThePetCanArriveIsAPet()
	{
		assertTrue(KillTracker.isPetMessage("You have a funny feeling like you're being followed."));
		assertTrue(KillTracker.isPetMessage("You feel something weird sneaking into your backpack."));
		assertTrue(KillTracker.isPetMessage("You have a funny feeling like you would have been followed."));
	}

	@Test
	public void ordinaryChatIsNotAPet()
	{
		assertFalse(KillTracker.isPetMessage("You have a funny feeling like you forgot to bank."));
		assertFalse(KillTracker.isPetMessage("Your Vorkath kill count is: 500."));
		assertFalse(KillTracker.isPetMessage(null));
	}

	/**
	 * A pet is announced without a boss attached, so the only thing tying it to the challenge is when
	 * it happened. Both orders count: ordinarily the pet lands first, but the raids announce it at the
	 * end of the raid and do not pay out until the chest is opened.
	 */
	@Test
	public void aPetBelongsToAKillOnEitherSideOfIt()
	{
		long now = 1_700_000_000_000L;

		assertTrue(KillTracker.fromTheSameKill(now, now));
		assertTrue(KillTracker.fromTheSameKill(now, now + 2_000));
		assertTrue(KillTracker.fromTheSameKill(now, now - 45_000));

		// Cutting yews an hour into the week is not a boss pet.
		assertFalse(KillTracker.fromTheSameKill(now, now - 3_600_000));
		assertFalse(KillTracker.fromTheSameKill(now, now + 3_600_000));

		// Nothing has happened yet, which pairs with nothing.
		assertFalse(KillTracker.fromTheSameKill(0, now));
		assertFalse(KillTracker.fromTheSameKill(now, 0));
	}

	@Test
	public void oneBossIsNotAnother()
	{
		assertFalse(KillTracker.matches("Scurrius", "Giant rat"));
		assertFalse(KillTracker.matches("Zulrah", null));
		assertFalse(KillTracker.matches(null, "Zulrah"));

		// A prefix is not a match without the bracket: the King Black Dragon is not a black dragon.
		assertFalse(KillTracker.matches("Black dragon", "Black dragon guard"));
	}
}
