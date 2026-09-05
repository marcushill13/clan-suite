package com.clansuite.event.data;

/**
 * The things an event can count, spelled one way.
 * <p>
 * This exists because they were spelled two ways. A template said it tracked "drops" and "completions"
 * while the tracker reported a "drop" and a "completion", so an event set up from that template counted
 * kills and quietly ignored every purple that fell in it. Nothing complained: both halves were doing
 * exactly what they had been told.
 * <p>
 * Singular throughout, because an observation is one of the thing. A rule names the same words, so what
 * a template asks for, what a tracker sends and what a rule pays for are all the same string.
 */
public final class Metric
{
	/** A boss killed, or a raid finished. The subject is what was killed. */
	public static final String KILL = "kc";

	/** One item, of a quantity. The subject is the item's name. */
	public static final String DROP = "drop";

	/**
	 * What a drop was worth, in coins, at the prices the client had.
	 * <p>
	 * Reported alongside the drop rather than instead of it, because they answer different questions:
	 * how many of a thing somebody got, and how much it came to. Without this "biggest drop of the
	 * year" is not a question anybody can ask, and a loot competition cannot be scored at all.
	 * <p>
	 * A guide rather than a valuation. It is the exchange price the client happened to have, so two
	 * people reporting the same item on the same evening can differ by a little, and an untradeable is
	 * worth nothing at all here however much anybody wanted it.
	 */
	public static final String LOOT = "loot";

	/** Experience gained. The subject is the skill. */
	public static final String EXPERIENCE = "xp";

	/** The player dying. No subject: there is only one way to do it. */
	public static final String DEATH = "death";

	/** A raid or minigame finished. The subject is what was finished. */
	public static final String COMPLETION = "completion";

	/**
	 * Somebody having turned up, ticked by a person who was there.
	 * <p>
	 * Never reported by a tracker, and never will be — nothing a game client can see proves attendance
	 * at a hide and seek. It is here because an event still has to be able to say that is what it
	 * counts, so the screen offers the right thing.
	 */
	public static final String ATTENDANCE = "attendance";

	private Metric()
	{
	}
}
