package com.clansuite.clan.net;

import com.clansuite.clan.data.Clan;
import com.clansuite.clan.data.ClanApplication;
import com.clansuite.clan.data.ClanMember;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Talking to the service about clans.
 * <p>
 * Deliberately its own client rather than more methods on the Boss of the Week one. That plugin's API
 * is about a single competition and knows nothing of who anybody is; this is about who people are, and
 * everything else will eventually hang off it. Keeping them apart also means nothing here can break a
 * competition that is running.
 * <p>
 * Nothing on this class may be called from the Swing thread. Every one of these makes a request.
 */
@Slf4j
@Singleton
public class ClanApi
{
	private static final MediaType JSON = MediaType.get("application/json");

	/** What proves a request is yours. Issued when you are let into a clan. */
	private static final String TOKEN_HEADER = "X-Clan-Token";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	private ClanApi(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** Either what was asked for, or why not. */
	@Value
	public static class Result<T>
	{
		T value;
		String error;

		/**
		 * The service answered and said there is no such clan — as opposed to not answering at all.
		 * A clan that is gone can be cleared off somebody's plugin; one that could not be reached
		 * because their connection dropped must be left exactly where it is.
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
	 * A clan, and where the person asking stands in it.
	 * <p>
	 * The three travel together because no screen wants one without the others: what to draw depends on
	 * the clan, and what to let them press depends on the rank and what it allows.
	 */
	@Value
	public static class Session
	{
		Clan clan;

		/** Null for somebody who is not a member — a stranger reading the hub. */
		String role;

		Set<String> capabilities;

		/** Only ever set when a clan has just been made or joined; never sent back afterwards. */
		String token;

		public boolean can(String capability)
		{
			return capabilities != null && capabilities.contains(capability);
		}
	}

	/** The roster, with the reader's own standing alongside it. */
	@Value
	public static class Roster
	{
		List<ClanMember> members;
		String role;
		Set<String> capabilities;

		public boolean can(String capability)
		{
			return capabilities != null && capabilities.contains(capability);
		}
	}

	public Result<Session> create(String baseUrl, String name, String tagline, String rsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("tagline", tagline);
		body.addProperty("rsn", rsn);

		return session(new Request.Builder()
			.url(url(baseUrl, "v1", "clans"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	/**
	 * The hub: every clan that has chosen to be seen.
	 *
	 * @param query optional, matched against name and tagline by the service
	 */
	public Result<List<Clan>> directory(String baseUrl, String query)
	{
		HttpUrl.Builder url = url(baseUrl, "v1", "clans").newBuilder();
		if (query != null && !query.trim().isEmpty())
		{
			url.addQueryParameter("q", query.trim());
		}

		return list(new Request.Builder().url(url.build()).get(), "clans",
			new TypeToken<List<Clan>>()
			{
			}.getType());
	}

	/**
	 * One clan. Works without a token — that is what lets somebody look at a clan before applying to
	 * it — and says more when there is one.
	 */
	public Result<Session> read(String baseUrl, String code, String token)
	{
		return session(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code))
			.get(), token));
	}

	/**
	 * @param discordWebhook a new address, an empty string to turn announcements off, or null to leave
	 *                       whatever is there alone — which is what every save that is not about
	 *                       Discord does, since the plugin is never told the current one
	 */
	public Result<Session> update(
		String baseUrl, String code, String token, Clan wanted, String discordWebhook)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", wanted.getName());
		body.addProperty("tagline", wanted.getTagline());
		body.addProperty("listed", wanted.isListed());
		body.addProperty("applicationsOpen", wanted.isApplicationsOpen());

		if (discordWebhook != null)
		{
			body.addProperty("discordWebhook", discordWebhook);
		}

		return session(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code))
			.patch(RequestBody.create(JSON, gson.toJson(body))), token));
	}

	public Result<Roster> members(String baseUrl, String code, String token)
	{
		return send(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "members")).get(), token), text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			Type type = new TypeToken<List<ClanMember>>()
			{
			}.getType();

			List<ClanMember> members = root != null && root.has("members")
				? gson.fromJson(root.get("members"), type)
				: new ArrayList<>();

			return new Roster(
				members == null ? new ArrayList<>() : members,
				stringOrNull(root, "role"),
				capabilitiesIn(root));
		});
	}

	/** Asking to join. No token: the whole point is that they are not in it yet. */
	public Result<Boolean> apply(String baseUrl, String code, String rsn, String message)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		body.addProperty("message", message == null ? "" : message);

		return ok(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "applications"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	public Result<List<ClanApplication>> applications(String baseUrl, String code, String token)
	{
		return list(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "applications")).get(), token), "applications",
			new TypeToken<List<ClanApplication>>()
			{
			}.getType());
	}

	public Result<Boolean> decide(String baseUrl, String code, String token, String rsn, boolean accept)
	{
		JsonObject body = new JsonObject();
		body.addProperty("decision", accept ? "accept" : "deny");

		return ok(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "applications", rsn))
			.post(RequestBody.create(JSON, gson.toJson(body))), token));
	}

	public Result<Boolean> setRole(String baseUrl, String code, String token, String rsn, String role)
	{
		JsonObject body = new JsonObject();
		body.addProperty("role", role);

		return ok(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "members", rsn))
			.patch(RequestBody.create(JSON, gson.toJson(body))), token));
	}

	/** Removing somebody, or leaving yourself — the service tells the two apart by who is asking. */
	public Result<Boolean> remove(String baseUrl, String code, String token, String rsn)
	{
		return ok(authorised(new Request.Builder()
			.url(url(baseUrl, "v1", "clans", code, "members", rsn))
			.delete(), token));
	}

	private Request.Builder authorised(Request.Builder builder, String token)
	{
		return token == null ? builder : builder.header(TOKEN_HEADER, token);
	}

	private Result<Session> session(Request.Builder builder)
	{
		return send(builder, text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			Clan clan = root != null && root.has("clan") && !root.get("clan").isJsonNull()
				? gson.fromJson(root.get("clan"), Clan.class)
				: null;

			return new Session(clan, stringOrNull(root, "role"), capabilitiesIn(root),
				stringOrNull(root, "token"));
		});
	}

	private <T> Result<List<T>> list(Request.Builder builder, String key, Type type)
	{
		return send(builder, text ->
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root == null || !root.has(key))
			{
				return Collections.emptyList();
			}

			List<T> found = gson.fromJson(root.get(key), type);
			return found == null ? Collections.emptyList() : found;
		});
	}

	/** For the calls whose answer is only whether it worked. */
	private Result<Boolean> ok(Request.Builder builder)
	{
		return send(builder, text -> Boolean.TRUE);
	}

	private Set<String> capabilitiesIn(JsonObject root)
	{
		if (root == null || !root.has("capabilities") || root.get("capabilities").isJsonNull())
		{
			return Collections.emptySet();
		}

		Type type = new TypeToken<Set<String>>()
		{
		}.getType();

		Set<String> found = gson.fromJson(root.get("capabilities"), type);
		return found == null ? Collections.emptySet() : new HashSet<>(found);
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

	/**
	 * The service's own wording where there is one. It says "This clan is full", which is more use to
	 * somebody than anything this class could invent.
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
		return root != null && root.has(key) && !root.get(key).isJsonNull()
			? root.get(key).getAsString()
			: null;
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
