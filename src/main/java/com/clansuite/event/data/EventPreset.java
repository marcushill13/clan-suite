package com.clansuite.event.data;

import com.google.gson.JsonArray;
import java.util.Collections;
import java.util.List;

/**
 * One of the events a clan actually runs, with the answers already filled in.
 * <p>
 * A template says what a <em>sort</em> of event counts. A preset is one real event off a clan's
 * calendar — Wintertodt Mass, Forestry Mass, Hide and Seek — with the tracking and the points it
 * usually has. The difference matters because the answers are different: a Mahogany Homes mass has no
 * kill count and never will, and a Forestry mass is mostly about who gets the whistle first.
 * <p>
 * Everything here is a starting point. The create screen fills the form in from it and then gets out
 * of the way, because a clan's Wintertodt mass is not everybody's Wintertodt mass.
 */
public class EventPreset
{
	private String id = "";
	private String name = "";
	private String category = "custom";
	private List<String> track = Collections.emptyList();
	private JsonArray rules = new JsonArray();

	/** Said on the create screen where the honest answer needs explaining. Usually absent. */
	private String note;

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public EventCategory category()
	{
		return EventCategory.of(category);
	}

	/** What it counts, in the one vocabulary. See {@link Metric}. */
	public List<String> getTrack()
	{
		return track == null ? Collections.emptyList() : track;
	}

	/** What things are worth, including any bounties. Copied into the event as it is made. */
	public JsonArray getRules()
	{
		return rules == null ? new JsonArray() : rules;
	}

	public String getNote()
	{
		return note;
	}

	/** How many of its rules are a race rather than a rate, which the screen says out loud. */
	public int bounties()
	{
		int found = 0;

		for (int i = 0; i < getRules().size(); i++)
		{
			if (getRules().get(i).isJsonObject()
				&& getRules().get(i).getAsJsonObject().has("kind")
				&& "bounty".equalsIgnoreCase(getRules().get(i).getAsJsonObject().get("kind").getAsString()))
			{
				found++;
			}
		}

		return found;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
