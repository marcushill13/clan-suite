package com.clansuite.clan.data;

/**
 * The things a rank can do, as the service names them.
 * <p>
 * The panel asks "may I", not "am I an administrator". Ranks will be added and their powers moved
 * about; every screen that asked about ranks directly would have to be found and changed each time.
 */
public final class Capability
{
	/** Rename the clan, hide it from the hub, open and close applications. */
	public static final String CLAN_SETTINGS = "CLAN_SETTINGS";

	public static final String CLAN_DELETE = "CLAN_DELETE";

	/** Promote and demote. The owner's and the deputy's, and nobody else's. */
	public static final String ROLE_ASSIGN = "ROLE_ASSIGN";

	/** Accept and turn down applications, and remove members. */
	public static final String MEMBER_MANAGE = "MEMBER_MANAGE";

	public static final String EVENT_MANAGE = "EVENT_MANAGE";
	public static final String POINTS_ADJUST = "POINTS_ADJUST";
	public static final String RESULT_VERIFY = "RESULT_VERIFY";
	public static final String MEMBER_VIEW = "MEMBER_VIEW";

	private Capability()
	{
	}
}
