package com.clansuite.botw.net;

import com.clansuite.botw.data.Challenge;
import com.clansuite.botw.data.LeaderboardEntry;
import com.clansuite.botw.track.PendingEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
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
 * Talks to the service the plugins share.
 * <p>
 * Every call here blocks and must be made off the client thread. The panel runs them on the executor
 * and updates itself afterwards, because a request that hangs on the client thread freezes the game.
 * <p>
 * Failures come back as a {@link Result} carrying a message rather than as an exception. A challenge
 * failing to load is an ordinary thing — someone typed a code wrong, or the network is down — and the
 * panel needs to say so rather than swallow it.
 */
@Slf4j
@Singleton
public class BotwApi
{
	private static final MediaType JSON = MediaType.get("application/json");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	private BotwApi(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Either what was asked for, or why not.
	 */
	@Value
	public static class Result<T>
	{
		T value;
		String error;

		/**
		 * The service answered, and said there is no such thing.
		 * <p>
		 * Kept apart from every other failure because the two call for opposite responses. A challenge
		 * that is gone should be cleared off the player's list; a challenge that merely could not be
		 * reached — no connection, server restarting — must be left exactly where it is. Treating the
		 * second as the first would quietly delete somebody's competition because their wifi dropped.
		 */
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

	/**
	 * A challenge and its leaderboard, which always travel together — there is no screen that wants one
	 * without the other.
	 */
	@Value
	public static class Snapshot
	{
		Challenge challenge;
		List<LeaderboardEntry> leaderboard;

		/** Only present when the challenge was just created or joined. */
		String creatorToken;
		String participantToken;
	}

	public Result<Snapshot> create(String baseUrl, Challenge challenge, String creatorRsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", challenge.getName());
		body.addProperty("boss", challenge.getBoss());
		body.addProperty("startsAt", challenge.getStartsAt());
		body.addProperty("endsAt", challenge.getEndsAt());
		body.addProperty("timezone", challenge.getTimezone());
		body.addProperty("kcPer", challenge.getKcPer());
		body.addProperty("kcPoints", challenge.getKcPoints());
		body.addProperty("creatorRsn", creatorRsn);
		body.add("drops", gson.toJsonTree(challenge.getDrops()));

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	public Result<Snapshot> read(String baseUrl, String code)
	{
		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code))
			.get());
	}

	public Result<Snapshot> join(String baseUrl, String code, String rsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "join"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	public Result<Snapshot> update(String baseUrl, Challenge challenge, String creatorToken)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", challenge.getName());
		body.addProperty("boss", challenge.getBoss());
		body.addProperty("startsAt", challenge.getStartsAt());
		body.addProperty("endsAt", challenge.getEndsAt());
		body.addProperty("timezone", challenge.getTimezone());
		body.addProperty("kcPer", challenge.getKcPer());
		body.addProperty("kcPoints", challenge.getKcPoints());
		body.add("drops", gson.toJsonTree(challenge.getDrops()));

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", challenge.getCode()))
			.patch(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Creator-Token", creatorToken));
	}

	public Result<Snapshot> delete(String baseUrl, String code, String creatorToken)
	{
		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code))
			.delete()
			.header("X-Creator-Token", creatorToken));
	}

	/**
	 * Puts someone on the leaderboard who will never report anything themselves.
	 * <p>
	 * A mobile player cannot run a plugin, so their kills cannot be counted. The clan handles them the
	 * way it always has — screenshots, and a number entered by staff — and this is where that number
	 * goes, so they appear on the same board as everyone else.
	 */
	public Result<Snapshot> addParticipant(
		String baseUrl, String code, String creatorToken, String rsn, int points)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		body.addProperty("points", points);

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "participants"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Creator-Token", creatorToken));
	}

	/**
	 * Sets someone's total.
	 * <p>
	 * A total rather than a change, because that is how a creator thinks about it. What the service
	 * keeps is the difference from the player's own tracked score, so their kills go on counting
	 * underneath this.
	 */
	public Result<Snapshot> setPoints(
		String baseUrl, String code, String creatorToken, String rsn, int points)
	{
		JsonObject body = new JsonObject();
		body.addProperty("points", points);

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "participants", rsn))
			.patch(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Creator-Token", creatorToken));
	}

	/** Takes someone off the leaderboard, for when a name was added wrong. */
	public Result<Snapshot> removeParticipant(
		String baseUrl, String code, String creatorToken, String rsn)
	{
		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "participants", rsn))
			.delete()
			.header("X-Creator-Token", creatorToken));
	}

	/**
	 * Reports what happened. The events keep their ids, so a batch that is sent twice counts once.
	 */
	public Result<Snapshot> submit(
		String baseUrl, String code, String participantToken, List<PendingEvent> events)
	{
		JsonObject body = new JsonObject();
		body.add("events", gson.toJsonTree(events));

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "events"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Participant-Token", participantToken));
	}

	/**
	 * One screenshot, as evidence the creator can look at without asking anyone for it.
	 */
	public Result<Snapshot> uploadShot(
		String baseUrl, String code, String participantToken, String eventId, String itemName,
		long occurredAt, byte[] jpeg)
	{
		JsonObject body = new JsonObject();
		body.addProperty("eventId", eventId);
		body.addProperty("itemName", itemName);
		body.addProperty("occurredAt", occurredAt);
		body.addProperty("image", Base64.getEncoder().encodeToString(jpeg));

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "shots"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Participant-Token", participantToken));
	}

	/**
	 * What evidence exists, without the pictures. Creator only.
	 */
	public Result<List<Shot>> listShots(String baseUrl, String code, String creatorToken)
	{
		Request request = new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "shots"))
			.get()
			.header("X-Creator-Token", creatorToken)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				return Result.failed(messageIn(text, "Could not load the evidence"));
			}

			JsonObject root = gson.fromJson(text, JsonObject.class);
			Type type = new TypeToken<List<Shot>>()
			{
			}.getType();

			List<Shot> shots = root != null && root.has("shots")
				? gson.fromJson(root.get("shots"), type)
				: new ArrayList<>();

			return Result.of(shots == null ? new ArrayList<>() : shots);
		}
		catch (IOException e)
		{
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException e)
		{
			return Result.failed("The server sent something unreadable");
		}
	}

	/**
	 * One picture, fetched only when it is opened. A hundred thumbnails in one response would be
	 * several megabytes for a screen most of which is never looked at.
	 */
	public Result<byte[]> readShot(String baseUrl, String code, String creatorToken, String eventId)
	{
		Request request = new Request.Builder()
			.url(url(baseUrl, "v1", "challenges", code, "shots", eventId))
			.get()
			.header("X-Creator-Token", creatorToken)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				return Result.failed(messageIn(text, "Could not load that screenshot"));
			}

			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root == null || !root.has("image"))
			{
				return Result.failed("That screenshot is missing");
			}

			return Result.of(Base64.getDecoder().decode(root.get("image").getAsString()));
		}
		catch (IOException e)
		{
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException | IllegalArgumentException e)
		{
			return Result.failed("That screenshot is unreadable");
		}
	}

	/** One piece of evidence, without its picture. */
	@lombok.Data
	public static class Shot
	{
		private String eventId = "";
		private String rsn = "";
		private String itemName = "";
		private long occurredAt;
	}

	private Result<Snapshot> send(Request.Builder builder)
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

			return Result.of(parse(text));
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

	private Snapshot parse(String text)
	{
		JsonObject root = gson.fromJson(text, JsonObject.class);
		if (root == null)
		{
			return new Snapshot(null, new ArrayList<>(), null, null);
		}

		Challenge challenge = root.has("challenge") && !root.get("challenge").isJsonNull()
			? gson.fromJson(root.get("challenge"), Challenge.class)
			: null;

		List<LeaderboardEntry> leaderboard = new ArrayList<>();
		if (root.has("leaderboard") && root.get("leaderboard").isJsonArray())
		{
			Type type = new TypeToken<List<LeaderboardEntry>>()
			{
			}.getType();

			List<LeaderboardEntry> parsed = gson.fromJson(root.get("leaderboard"), type);
			if (parsed != null)
			{
				leaderboard.addAll(parsed);
			}
		}

		// The code comes back at the top level on create, where the challenge does not carry it yet.
		if (challenge != null && (challenge.getCode() == null || challenge.getCode().isEmpty())
			&& root.has("code"))
		{
			challenge.setCode(root.get("code").getAsString());
		}

		return new Snapshot(
			challenge,
			leaderboard,
			stringOrNull(root, "creatorToken"),
			stringOrNull(root, "participantToken"));
	}

	/**
	 * The server's own wording where there is one. It says "No challenge with that code", which is more
	 * use to a player than anything this class could invent.
	 */
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

	private static String stringOrNull(JsonObject root, String key)
	{
		return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : null;
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
