package com.clansuite.event.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The kinds of event a clan actually runs, with the answers filled in.
 * <p>
 * A template is a starting point, not a straitjacket: it says what this sort of event usually tracks
 * and what it is usually scored on, and everything it sets can be changed afterwards. The point is
 * that nobody sets up a raid night by first deciding, from nothing, which nine things to count.
 * <p>
 * Bundled in the plugin rather than fetched, so the list works offline and a clan is not waiting on a
 * service to add the sort of event they run every week. The service stores which template an event
 * came from and nothing else about it, so a plugin that knows a template the service has never heard
 * of still works.
 */
public enum EventTemplate
{
	BOSS_OF_THE_WEEK("botw", "Boss of the Week", EventCategory.PVM,
		"A boss, a week, kills and drops. The one this plugin started as.",
		"kc", "drops"),

	BOSS_MASS("boss_mass", "Boss mass", EventCategory.PVM,
		"Everyone at one boss for an evening. Kills and drops on the night.",
		"kc", "drops"),

	KC_COMPETITION("kc_competition", "KC competition", EventCategory.PVM,
		"Most kills wins. No drop list to argue about.",
		"kc"),

	RAID_NIGHT("raid_night", "Raid night", EventCategory.RAIDS,
		"Completions, deaths and purples across CoX, ToB or ToA.",
		"completions", "deaths", "uniques", "time"),

	SKILL_OF_THE_WEEK("skill_of_the_week", "Skill of the week", EventCategory.SKILLING,
		"One skill, a week, most experience gained.",
		"xp"),

	XP_RACE("xp_race", "XP race", EventCategory.SKILLING,
		"First to a target, or furthest in the time.",
		"xp"),

	MINIGAME("minigame", "Minigame", EventCategory.MINIGAME,
		"Wintertodt, Tempoross, the Rift — games with their own completions.",
		"completions"),

	SOCIAL("social", "Social", EventCategory.SOCIAL,
		"Hide and seek, quizzes, scavenger hunts. Marked off by a person.",
		"attendance"),

	CUSTOM("custom", "Custom", EventCategory.CUSTOM,
		"Nothing filled in. Decide the whole thing yourself.");

	private final String id;
	private final String label;
	private final EventCategory category;
	private final String blurb;
	private final List<String> tracks;

	EventTemplate(String id, String label, EventCategory category, String blurb, String... tracks)
	{
		this.id = id;
		this.label = label;
		this.category = category;
		this.blurb = blurb;
		this.tracks = Collections.unmodifiableList(Arrays.asList(tracks));
	}

	public String getId()
	{
		return id;
	}

	public String getLabel()
	{
		return label;
	}

	public EventCategory getCategory()
	{
		return category;
	}

	public String getBlurb()
	{
		return blurb;
	}

	/**
	 * What this sort of event counts.
	 * <p>
	 * Written into the event so the trackers know what to watch for. Some of these cannot be tracked
	 * from a client at all — attendance at a hide and seek is a person ticking names off, and always
	 * will be. Saying so in the event is what lets the screen offer the right thing rather than
	 * pretending.
	 */
	public List<String> getTracks()
	{
		return tracks;
	}

	/** Whether this is the Boss of the Week competition, which already has screens of its own. */
	public boolean isBossOfTheWeek()
	{
		return this == BOSS_OF_THE_WEEK;
	}

	@Override
	public String toString()
	{
		return label;
	}

	public static EventTemplate of(String id)
	{
		if (id != null)
		{
			for (EventTemplate template : values())
			{
				if (template.id.equalsIgnoreCase(id.trim()) || template.name().equalsIgnoreCase(id.trim()))
				{
					return template;
				}
			}
		}

		return CUSTOM;
	}

	public static String idOf(EventTemplate template)
	{
		return template == null ? CUSTOM.id : template.id.toLowerCase(Locale.ROOT);
	}
}
