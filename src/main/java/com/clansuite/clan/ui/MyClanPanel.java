package com.clansuite.clan.ui;

import com.clansuite.clan.data.Capability;
import com.clansuite.clan.data.Clan;
import com.clansuite.clan.data.ClanApplication;
import com.clansuite.clan.data.ClanMember;
import com.clansuite.clan.data.Role;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A clan as its own members see it — which is three different screens wearing the same coat.
 * <p>
 * A member gets the clan, who is in it, and the way out. Staff also get the applications waiting to be
 * decided. The owner and their deputies also get the settings that decide whether anyone can find the
 * clan at all. Nothing is drawn for a rank that could not use it, because a button that always answers
 * "you cannot do that" is worse than no button.
 * <p>
 * What is drawn is decided by the capabilities the service sent, never by the rank this plugin
 * remembers. The service is asked on every visit, so a promotion or a demotion shows up on the next
 * refresh rather than the next reinstall — and the service checks again when the button is actually
 * pressed, because hiding a control is not the same as forbidding it.
 */
public class MyClanPanel extends JPanel
{
	public interface Actions
	{
		void saveSettings(Clan wanted);

		void decide(String rsn, boolean accept);

		void setRole(String rsn, String role);

		void remove(String rsn);

		void leave();

		void refresh();
	}

	private final JTextField name = Theme.textField(new JTextField());
	private final JTextField tagline = Theme.textField(new JTextField());

	/** Held because the toggles are segmented buttons rather than checkboxes; these are their state. */
	private boolean listed;
	private boolean applicationsOpen;

	public MyClanPanel(
		Clan clan,
		Role yours,
		Set<String> capabilities,
		List<ClanMember> roster,
		List<ClanApplication> applications,
		String yourRsn,
		Actions actions)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		this.listed = clan.isListed();
		this.applicationsOpen = clan.isApplicationsOpen();

		add(header(clan, yours));

		if (capabilities.contains(Capability.MEMBER_MANAGE))
		{
			add(Cards.gap(12));
			add(applicationsSection(applications, actions));
		}

		if (capabilities.contains(Capability.CLAN_SETTINGS))
		{
			add(Cards.gap(12));
			add(settingsSection(clan, actions));
		}

		add(Cards.gap(14));
		add(Cards.sectionLabel("MEMBERS — " + clan.membership()));

		for (ClanMember member : roster)
		{
			add(Cards.gap(4));
			add(memberRow(member, yours, capabilities, yourRsn, actions));
		}

		add(Cards.gap(14));

		JButton refresh = Cards.button("Refresh");
		refresh.addActionListener(event -> actions.refresh());
		add(inRow(refresh));

		// The owner has nowhere to go: a clan with nobody able to run it would be a clan nobody could
		// delete either. Handing it over comes before leaving it does.
		if (yours != Role.OWNER)
		{
			add(Cards.gap(6));
			JButton leave = Cards.button("Leave clan");
			leave.addActionListener(event -> actions.leave());
			add(inRow(leave));
		}
	}

	private JPanel header(Clan clan, Role yours)
	{
		JPanel card = Cards.card();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		card.add(Cards.title(clan.getName()));

		if (!clan.getTagline().isEmpty())
		{
			card.add(Cards.muted(clan.getTagline()));
		}

		card.add(Cards.gap(8));

		JLabel code = new JLabel(clan.getCode());
		code.setFont(Theme.figure(20f));
		code.setForeground(Theme.GOLD);
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(Cards.sectionLabel("CLAN CODE"));
		card.add(code);
		card.add(Cards.muted("Share this and anyone can find the clan, listed or not."));

		card.add(Cards.gap(8));
		card.add(Cards.body("You are " + (yours == null ? "not in this clan" : yours.getLabel())));
		card.add(Cards.body(clan.membership() + " members"
			+ (clan.isFull() ? " — full" : "")
			+ (clan.isApplicationsOpen() ? "" : " — applications closed")));

		return card;
	}

	/**
	 * The queue of people waiting to be let in. Empty is the normal state, and says so rather than
	 * leaving a heading over nothing.
	 */
	private JPanel applicationsSection(List<ClanApplication> applications, Actions actions)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(Theme.BACKGROUND);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		section.add(Cards.sectionLabel("APPLICATIONS" + (applications.isEmpty() ? "" : " — " + applications.size())));

		if (applications.isEmpty())
		{
			section.add(Cards.gap(4));
			section.add(Cards.muted("Nobody is waiting."));
			return section;
		}

		for (ClanApplication application : applications)
		{
			JPanel card = Cards.card();
			card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

			card.add(Cards.headline(application.getRsn()));

			if (!application.getMessage().isEmpty())
			{
				card.add(Cards.muted(application.getMessage()));
			}

			card.add(Cards.gap(6));

			JButton accept = Cards.button("Accept");
			accept.addActionListener(event -> actions.decide(application.getRsn(), true));

			JButton deny = Cards.button("Deny");
			deny.addActionListener(event -> actions.decide(application.getRsn(), false));

			card.add(inRow(accept, deny));

			section.add(Cards.gap(4));
			section.add(card);
		}

		return section;
	}

	private JPanel settingsSection(Clan clan, Actions actions)
	{
		name.setText(clan.getName());
		tagline.setText(clan.getTagline());

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);

		body.add(Cards.field("Clan name", name));
		body.add(Cards.gap(8));
		body.add(Cards.field("Tagline", tagline));

		body.add(Cards.gap(10));
		body.add(Cards.sectionLabel("IN THE CLAN LIST"));
		body.add(Cards.segmented(new String[]{"Listed", "Hidden"}, listed ? 0 : 1,
			index -> listed = index == 0));
		body.add(Cards.muted("Hidden keeps the clan off the list. Your code still works."));

		body.add(Cards.gap(10));
		body.add(Cards.sectionLabel("APPLICATIONS"));
		body.add(Cards.segmented(new String[]{"Open", "Closed"}, applicationsOpen ? 0 : 1,
			index -> applicationsOpen = index == 0));
		body.add(Cards.muted("Closed removes the apply button and refuses any that arrive anyway."));

		body.add(Cards.gap(10));

		JButton save = Cards.button("Save settings");
		save.addActionListener(event ->
		{
			if (name.getText().trim().isEmpty())
			{
				Cards.warn(this, "The clan needs a name.");
				return;
			}

			Clan wanted = new Clan();
			wanted.setCode(clan.getCode());
			wanted.setName(name.getText().trim());
			wanted.setTagline(tagline.getText().trim());
			wanted.setListed(listed);
			wanted.setApplicationsOpen(applicationsOpen);

			actions.saveSettings(wanted);
		});

		body.add(inRow(save));

		return Cards.expandable("Clan settings", body, button ->
		{
		});
	}

	/**
	 * One person, and whatever the reader is allowed to do about them.
	 * <p>
	 * Ranks are only offered below the reader's own, which is the same rule the service applies. The
	 * owner's row never offers anything: a clan that can be taken from its owner by someone they
	 * promoted is not a clan anybody would run an event in.
	 */
	private JPanel memberRow(
		ClanMember member, Role yours, Set<String> capabilities, String yourRsn, Actions actions)
	{
		JPanel card = Cards.card();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		boolean you = member.getRsn().equalsIgnoreCase(yourRsn == null ? "" : yourRsn);
		card.add(Cards.headline(member.getRsn() + (you ? " (you)" : "")));

		Role theirs = member.role();
		card.add(Cards.body(theirs == null ? member.getRole() : theirs.getLabel()));

		boolean above = yours != null && theirs != null && yours.outranks(theirs);
		if (!above || you)
		{
			return fullWidth(card);
		}

		// Underneath rather than beside. A sidebar is 225 pixels wide, and a rank picker next to a name
		// leaves neither of them room: the names came out as "Torn..." and the ranks as "Administra".
		List<Component> controls = new ArrayList<>();

		if (capabilities.contains(Capability.ROLE_ASSIGN))
		{
			controls.add(rankPicker(member, yours, theirs, actions));
		}

		if (capabilities.contains(Capability.MEMBER_MANAGE))
		{
			JButton remove = Cards.button("Remove");
			remove.addActionListener(event -> actions.remove(member.getRsn()));
			controls.add(remove);
		}

		if (!controls.isEmpty())
		{
			card.add(Cards.gap(6));
			card.add(inRow(controls.toArray(new Component[0])));
		}

		return fullWidth(card);
	}

	/**
	 * Makes a card as wide as the panel. Without it a row of nothing but labels is only as wide as its
	 * longest one, and sits visibly short beside the rows that carry controls.
	 */
	private JPanel fullWidth(JPanel card)
	{
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private Component rankPicker(ClanMember member, Role yours, Role theirs, Actions actions)
	{
		List<Role> offered = new ArrayList<>();
		for (Role role : Role.values())
		{
			if (yours.outranks(role))
			{
				offered.add(role);
			}
		}

		JComboBox<Role> picker = Cards.comboBox(offered.toArray(new Role[0]));
		picker.setSelectedItem(theirs);
		picker.setMaximumSize(new Dimension(120, 22));

		picker.addActionListener(event ->
		{
			Role chosen = (Role) picker.getSelectedItem();
			if (chosen != null && chosen != theirs)
			{
				actions.setRole(member.getRsn(), chosen.wire());
			}
		});

		return picker;
	}

	private JPanel inRow(Component... parts)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				row.add(Box.createHorizontalStrut(6));
			}

			row.add(parts[i]);
		}

		return row;
	}
}
