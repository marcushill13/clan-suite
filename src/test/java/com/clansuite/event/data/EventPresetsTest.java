package com.clansuite.event.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The bundled list of the clan's events.
 * <p>
 * Mostly checking that the file and the code still agree: the data is written by hand, and the ways it
 * can go wrong are quiet ones. A metric spelt "drops" instead of "drop" counts nothing and says
 * nothing, which is exactly the bug that got past everything once already.
 */
public class EventPresetsTest
{
	private static final Set<String> METRICS = new HashSet<>(Arrays.asList(
		Metric.KILL, Metric.DROP, Metric.EXPERIENCE, Metric.DEATH, Metric.COMPLETION, Metric.ATTENDANCE));

	private final EventPresets presets = presets();

	@Test
	public void theListIsThere()
	{
		assertTrue("the bundled presets should not be empty", presets.all().size() > 20);
	}

	@Test
	public void everyPresetCountsThingsThatExist()
	{
		for (EventPreset preset : presets.all())
		{
			for (String metric : preset.getTrack())
			{
				assertTrue(preset.getName() + " counts \"" + metric + "\", which is not a metric",
					METRICS.contains(metric));
			}
		}
	}

	/** A rule that names a metric the event does not count is a rule that pays nobody, silently. */
	@Test
	public void everyRuleIsAboutSomethingTheEventCounts()
	{
		for (EventPreset preset : presets.all())
		{
			for (JsonElement element : preset.getRules())
			{
				JsonObject rule = element.getAsJsonObject();
				String metric = rule.get("metric").getAsString();

				assertTrue(preset.getName() + " has a rule for \"" + metric + "\", which is not a metric",
					METRICS.contains(metric));

				assertTrue(preset.getName() + " pays for \"" + metric + "\" but does not count it",
					preset.getTrack().contains(metric));
			}
		}
	}

	@Test
	public void everyPresetHasItsOwnName()
	{
		Set<String> ids = new HashSet<>();
		Set<String> names = new HashSet<>();

		for (EventPreset preset : presets.all())
		{
			assertFalse("two presets share the id " + preset.getId(), ids.contains(preset.getId()));
			assertFalse("two presets are called " + preset.getName(), names.contains(preset.getName()));

			ids.add(preset.getId());
			names.add(preset.getName());
		}
	}

	/** The whole reason presets exist rather than one generic template. */
	@Test
	public void aSkillingMassDoesNotCountKills()
	{
		EventPreset mahogany = presets.byId("mahogany_homes_mass");
		assertNotNull(mahogany);
		assertFalse(mahogany.getTrack().contains(Metric.KILL));
		assertTrue(mahogany.getTrack().contains(Metric.EXPERIENCE));
		assertEquals(EventCategory.SKILLING, mahogany.category());
	}

	/** The example this was built for: first to the whistle takes it. */
	@Test
	public void theForestryMassRacesForTheWhistle()
	{
		EventPreset forestry = presets.byId("forestry_mass");
		assertNotNull(forestry);
		assertEquals(2, forestry.bounties());

		Set<String> raced = new HashSet<>();
		for (JsonElement element : forestry.getRules())
		{
			JsonObject rule = element.getAsJsonObject();
			if (rule.has("kind"))
			{
				raced.add(rule.get("subject").getAsString());
			}
		}

		assertTrue(raced.contains("Fox whistle"));
		assertTrue(raced.contains("Golden pheasant egg"));
	}

	@Test
	public void searchFindsThingsByNameAndByKind()
	{
		assertFalse(presets.search("wintertodt").isEmpty());
		assertFalse(presets.search("WINTERTODT").isEmpty());
		assertFalse(presets.search("social").isEmpty());
		assertTrue(presets.search("nothing like this exists").isEmpty());
		assertEquals(presets.all().size(), presets.search("").size());
	}

	private static EventPresets presets()
	{
		try
		{
			Constructor<EventPresets> constructor = EventPresets.class.getDeclaredConstructor(Gson.class);
			constructor.setAccessible(true);
			return constructor.newInstance(new Gson());
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
