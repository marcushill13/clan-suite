package com.clansuite.botw.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Reading the points a creator typed into the leaderboard's edit boxes.
 */
public class ChallengeViewTest
{
	@Test
	public void readsAWholeNumber()
	{
		assertEquals(Integer.valueOf(0), ChallengeView.parsePoints("0"));
		assertEquals(Integer.valueOf(42), ChallengeView.parsePoints("42"));
	}

	@Test
	public void toleratesTheSpaceSomeoneLeavesBehind()
	{
		assertEquals(Integer.valueOf(7), ChallengeView.parsePoints("  7 "));
	}

	/** Docking points is a real thing a clan does, so a minus sign is not a mistake. */
	@Test
	public void allowsNegatives()
	{
		assertEquals(Integer.valueOf(-5), ChallengeView.parsePoints("-5"));
	}

	/**
	 * Null means "tell them, and send nothing". Guessing a number out of "12 points" or treating an
	 * empty box as zero would quietly set someone's score to something they did not type.
	 */
	@Test
	public void refusesAnythingThatIsNotOne()
	{
		assertNull(ChallengeView.parsePoints(""));
		assertNull(ChallengeView.parsePoints("   "));
		assertNull(ChallengeView.parsePoints("ten"));
		assertNull(ChallengeView.parsePoints("12 points"));
		assertNull(ChallengeView.parsePoints("3.5"));
		assertNull(ChallengeView.parsePoints("1,000"));
	}
}
