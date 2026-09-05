package com.clansuite.event.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The events a clan runs, bundled.
 * <p>
 * Read from a file rather than typed into the create screen, because the list is long and it is a
 * clan's list rather than a program's — it changes when the clan's calendar changes, not when the
 * plugin does. Bundled rather than fetched so it works offline and nobody waits on a service to set up
 * an event they run every week.
 */
@Slf4j
@Singleton
public class EventPresets
{
	private static final String RESOURCE = "/com/clansuite/event/presets.json";

	private final List<EventPreset> presets;

	@Inject
	private EventPresets(Gson gson)
	{
		this.presets = load(gson);
	}

	private static List<EventPreset> load(Gson gson)
	{
		try (InputStream stream = EventPresets.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("The event presets are missing; the create screen will start empty");
				return Collections.emptyList();
			}

			File file = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), File.class);
			return file == null || file.presets == null ? Collections.emptyList() : file.presets;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read the event presets", e);
			return Collections.emptyList();
		}
	}

	public List<EventPreset> all()
	{
		return Collections.unmodifiableList(presets);
	}

	/**
	 * Presets whose name contains this, case-insensitively. An empty query returns everything, so the
	 * list is browsable before anything is typed.
	 */
	public List<EventPreset> search(String query)
	{
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<EventPreset> matches = new ArrayList<>();

		for (EventPreset preset : presets)
		{
			if (needle.isEmpty()
				|| preset.getName().toLowerCase(Locale.ROOT).contains(needle)
				|| preset.category().getLabel().toLowerCase(Locale.ROOT).contains(needle))
			{
				matches.add(preset);
			}
		}

		return matches;
	}

	public EventPreset byId(String id)
	{
		for (EventPreset preset : presets)
		{
			if (preset.getId().equalsIgnoreCase(id))
			{
				return preset;
			}
		}

		return null;
	}

	/** Mirrors the bundled file. */
	private static class File
	{
		int dataVersion;
		String note;
		List<EventPreset> presets = new ArrayList<>();
	}
}
