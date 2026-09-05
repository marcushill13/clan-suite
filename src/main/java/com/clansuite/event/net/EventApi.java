package com.clansuite.event.net;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.EventParticipant;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talking to the service about a clan's events.
 * <p>
 * Nothing here may be called from the Swing thread; every method makes a request.
 */
@Slf4j
@Singleton
public class EventApi
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String TOKEN_HEADER = "X-Clan-Token";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	private EventApi(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	@Value
	public static class Result<T>
	{
		T value;
		String error;
		boolean gone;

		public boolean ok()
		{
			return error == null;
		}

		static <T> Result<T> of(T value)
		{
			return new Result<>(value, null, false);
		}

		static <T> Result<T> failed(String error)
		{
			return new Result<>(null, error, false);
		}

		static <T> Result<T> gone(String error)
		{
			return new Result<>(null, error, true);
		}
	}

	public Result<List<ClanEvent>> forClan(String baseUrl, String clanCode, String token)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", clanCode, "events")).get(), token), text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root == null || !root.has("events"))
			{
				return Collections.<ClanEvent>emptyList();
			}

			List<ClanEvent> events = gson.fromJson(root.get("events"),
				new TypeToken<List<ClanEvent>>()
				{
				}.getType());

			return events == null ? Collections.<ClanEvent>emptyList() : events;
		});
	}

	public Result<ClanEvent> create(String baseUrl, String clanCode, String token, ClanEvent event)
	{
		return one(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", clanCode, "events"))
			.post(RequestBody.create(JSON, gson.toJson(wire(event)))), token));
	}

	public Result<ClanEvent> read(String baseUrl, String code, String token)
	{
		return one(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code)).get(), token));
	}

	public Result<ClanEvent> update(String baseUrl, String code, String token, ClanEvent event)
	{
		return one(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code))
			.patch(RequestBody.create(JSON, gson.toJson(wire(event)))), token));
	}

	/** Publishing and cancelling are the same call with a different word, so they share one. */
	public Result<ClanEvent> setStatus(String baseUrl, String code, String token, String status)
	{
		JsonObject body = new JsonObject();
		body.addProperty("status", status);

		return one(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code))
			.patch(RequestBody.create(JSON, gson.toJson(body))), token));
	}

	/** Signing up. Idempotent: pressing it twice is joining once. */
	public Result<Boolean> join(String baseUrl, String code, String token)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code, "join"))
			.post(RequestBody.create(JSON, "{}")), token), text -> Boolean.TRUE);
	}

	public Result<List<EventParticipant>> participants(String baseUrl, String code, String token)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code, "participants")).get(), token), text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root == null || !root.has("participants"))
			{
				return Collections.<EventParticipant>emptyList();
			}

			List<EventParticipant> found = gson.fromJson(root.get("participants"),
				new TypeToken<List<EventParticipant>>()
				{
				}.getType());

			return found == null ? Collections.<EventParticipant>emptyList() : found;
		});
	}

	/**
	 * Marking somebody off, or correcting their score.
	 *
	 * @param attended   null to leave the tick as it is
	 * @param adjustment null to leave the score as it is
	 */
	public Result<Boolean> mark(
		String baseUrl, String code, String token, String rsn, Boolean attended, Integer adjustment)
	{
		JsonObject body = new JsonObject();

		if (attended != null)
		{
			body.addProperty("attended", attended);
		}

		if (adjustment != null)
		{
			body.addProperty("adjustment", adjustment);
		}

		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code, "participants", rsn))
			.patch(RequestBody.create(JSON, gson.toJson(body))), token), text -> Boolean.TRUE);
	}

	/** Leaving, or being taken off by whoever runs the event — the service tells them apart. */
	public Result<Boolean> withdraw(String baseUrl, String code, String token, String rsn)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code, "participants", rsn)).delete(), token),
			text -> Boolean.TRUE);
	}

	public Result<Boolean> delete(String baseUrl, String code, String token)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "events", code)).delete(), token), text -> Boolean.TRUE);
	}

	/**
	 * What the service is sent. Its own fields — who made it, when — are never written back, so they
	 * are left out rather than sent to be ignored.
	 */
	private JsonObject wire(ClanEvent event)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", event.getName());
		body.addProperty("category", event.getCategory());
		body.addProperty("template", event.getTemplate());
		body.addProperty("startsAt", event.getStartsAt());
		body.addProperty("endsAt", event.getEndsAt());
		body.addProperty("timezone", event.getTimezone());
		body.addProperty("leaderboard", event.getLeaderboard());
		body.addProperty("status", event.getStatus());
		body.add("config", event.getConfig() == null ? new JsonObject() : event.getConfig());
		return body;
	}

	private Result<ClanEvent> one(Request.Builder builder)
	{
		return send(builder, text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			return root != null && root.has("event") && !root.get("event").isJsonNull()
				? gson.fromJson(root.get("event"), ClanEvent.class)
				: null;
		});
	}

	private Request.Builder authorised(Request.Builder builder, String token)
	{
		return token == null ? builder : builder.header(TOKEN_HEADER, token);
	}

	private <T> Result<T> send(Request.Builder builder, Reader<T> reader)
	{
		try (Response response = httpClient.newCall(builder.build()).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				String message = messageIn(text, "The server said no (" + response.code() + ")");
				return response.code() == 404 ? Result.gone(message) : Result.failed(message);
			}

			return Result.of(reader.read(text));
		}
		catch (IOException e)
		{
			log.debug("Request failed", e);
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Unreadable response", e);
			return Result.failed("The server sent something unreadable");
		}
	}

	private interface Reader<T>
	{
		T read(String text);
	}

	private String messageIn(String text, String fallback)
	{
		try
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root != null && root.has("error"))
			{
				return root.get("error").getAsString();
			}
		}
		catch (JsonSyntaxException ignored)
		{
			// Fall through to the generic message.
		}

		return fallback;
	}

	private static HttpUrl url(String baseUrl, String... segments)
	{
		HttpUrl parsed = HttpUrl.parse(baseUrl.trim());
		if (parsed == null)
		{
			throw new IllegalArgumentException("The server address is not a valid URL: " + baseUrl);
		}

		HttpUrl.Builder builder = parsed.newBuilder();
		for (String segment : segments)
		{
			builder.addPathSegment(segment);
		}

		return builder.build();
	}
}
