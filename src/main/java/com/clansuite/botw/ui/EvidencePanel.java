package com.clansuite.botw.ui;

import com.clansuite.ClanSuiteFiles;
import com.clansuite.botw.data.LeaderboardEntry;
import com.clansuite.botw.net.BotwApi;
import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 * The evidence, for whoever is running the challenge.
 * <p>
 * A folder per participant, because that is the question people actually ask: not "what happened this
 * week" but "show me this person's drops". Folded shut to begin with, since a fifty-person clan is
 * fifty folders and nobody wants to scroll past forty-nine of them.
 * <p>
 * Pictures are fetched only when a folder is opened. A hundred thumbnails at once would be several
 * megabytes for a screen most of which is never looked at.
 */
@Slf4j
public class EvidencePanel extends JPanel
{
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());

	private final String challengeCode;
	private final String challengeName;
	private final String creatorToken;
	private final String serverUrl;
	private final BotwApi api;
	private final ScheduledExecutorService executor;

	private final Map<String, List<BotwApi.Shot>> byParticipant = new LinkedHashMap<>();
	private final java.util.Set<String> open = new java.util.HashSet<>();

	/**
	 * @param participants everyone who has joined, so each gets a folder whether or not they have
	 *                     anything in it yet. A folder that only appears once someone scores looks like
	 *                     a missing feature rather than an empty one.
	 */
	public EvidencePanel(
		String challengeCode,
		String challengeName,
		String creatorToken,
		String serverUrl,
		BotwApi api,
		ScheduledExecutorService executor,
		List<com.clansuite.botw.data.LeaderboardEntry> participants)
	{
		this.challengeCode = challengeCode;
		this.challengeName = challengeName;
		this.creatorToken = creatorToken;
		this.serverUrl = serverUrl;
		this.api = api;
		this.executor = executor;

		// Seeded empty so every participant has a folder from the moment they join.
		for (com.clansuite.botw.data.LeaderboardEntry entry : participants)
		{
			byParticipant.put(entry.getRsn(), new ArrayList<>());
		}

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(loading());
		load();
	}

	private void load()
	{
		executor.execute(() ->
		{
			BotwApi.Result<List<BotwApi.Shot>> result =
				api.listShots(serverUrl, challengeCode, creatorToken);

			SwingUtilities.invokeLater(() ->
			{
				removeAll();

				if (!result.ok())
				{
					add(muted(result.getError()));
					revalidate();
					repaint();
					return;
				}

				// The seeded folders stay; the shots fill them in.
				for (List<BotwApi.Shot> shots : byParticipant.values())
				{
					shots.clear();
				}

				for (BotwApi.Shot shot : result.getValue())
				{
					byParticipant.computeIfAbsent(shot.getRsn(), rsn -> new ArrayList<>()).add(shot);
				}

				rebuild();
			});
		});
	}

	private void rebuild()
	{
		removeAll();

		if (byParticipant.isEmpty())
		{
			add(muted("Nobody has joined yet. Share the code and their folders will appear here."));
		}

		for (Map.Entry<String, List<BotwApi.Shot>> entry : byParticipant.entrySet())
		{
			add(folder(entry.getKey(), entry.getValue()));
			add(Cards.gap(3));
		}

		revalidate();
		repaint();
	}

	/** One participant, shut until asked for. */
	private JPanel folder(String rsn, List<BotwApi.Shot> shots)
	{
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setBackground(Theme.BACKGROUND);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(Theme.CARD);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		boolean opened = open.contains(rsn);

		JLabel name = new JLabel((opened ? "− " : "+ ") + rsn);
		name.setFont(Theme.heading());
		name.setForeground(Theme.GOLD);
		header.add(name, BorderLayout.CENTER);

		JLabel count = new JLabel(shots.isEmpty()
			? "none yet"
			: shots.size() + (shots.size() == 1 ? " drop" : " drops"));
		count.setFont(Theme.body());
		count.setForeground(Theme.TEXT_MUTED);
		header.add(count, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (opened)
				{
					open.remove(rsn);
				}
				else
				{
					open.add(rsn);
				}

				rebuild();
			}
		});

		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		wrapper.add(header);

		if (opened)
		{
			if (shots.isEmpty())
			{
				wrapper.add(Cards.gap(2));
				wrapper.add(muted("No screenshots yet."));
			}

			for (BotwApi.Shot shot : shots)
			{
				wrapper.add(Cards.gap(2));
				wrapper.add(shotRow(shot));
			}

			if (!shots.isEmpty())
			{
				wrapper.add(Cards.gap(4));
				wrapper.add(exportButton(rsn, shots));
			}
		}

		return wrapper;
	}

	/**
	 * One drop, with its picture fetched as the row is built.
	 */
	private JPanel shotRow(BotwApi.Shot shot)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel item = new JLabel(shot.getItemName());
		item.setFont(Theme.body());
		item.setForeground(Theme.TEXT);
		item.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(item);

		JLabel when = new JLabel(WHEN.format(Instant.ofEpochMilli(shot.getOccurredAt())));
		when.setFont(Theme.body());
		when.setForeground(Theme.TEXT_MUTED);
		when.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(when);

		row.add(text, BorderLayout.NORTH);

		JLabel picture = new JLabel("loading…");
		picture.setFont(Theme.body());
		picture.setForeground(Theme.TEXT_MUTED);
		row.add(picture, BorderLayout.CENTER);

		executor.execute(() ->
		{
			BotwApi.Result<byte[]> image =
				api.readShot(serverUrl, challengeCode, creatorToken, shot.getEventId());

			SwingUtilities.invokeLater(() ->
			{
				if (!image.ok())
				{
					picture.setText(image.getError());
					return;
				}

				try
				{
					// Scaled to the panel's width. The stored copy is far wider than the sidebar, on
					// purpose — this is a preview, and the export is where the full detail is read.
					Image full = ImageIO.read(new ByteArrayInputStream(image.getValue()));
					int width = Math.max(80, getWidth() - 40);
					int height = full.getHeight(null) * width / Math.max(1, full.getWidth(null));

					picture.setText(null);
					picture.setIcon(new ImageIcon(
						full.getScaledInstance(width, height, Image.SCALE_SMOOTH)));

					row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
					row.revalidate();
				}
				catch (IOException | RuntimeException e)
				{
					picture.setText("Could not read that screenshot");
				}
			});
		});

		return row;
	}

	/**
	 * Everything one participant has, written to a zip.
	 * <p>
	 * Clan leaders pass these between themselves to settle an argument, and a folder full of loose
	 * images is not something anyone can hand over in one go.
	 */
	private JButton exportButton(String rsn, List<BotwApi.Shot> shots)
	{
		JButton export = Cards.button("Export " + rsn + "'s drops");
		export.setAlignmentX(Component.LEFT_ALIGNMENT);
		export.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		export.addActionListener(event ->
		{
			export.setEnabled(false);
			export.setText("Exporting…");

			executor.execute(() ->
			{
				File written = exportZip(rsn, shots);

				SwingUtilities.invokeLater(() ->
				{
					export.setEnabled(true);
					export.setText("Export " + rsn + "'s drops");

					if (written == null)
					{
						Cards.warn(this, "Could not write that export.");
						return;
					}

					reveal(written);
				});
			});
		});

		return export;
	}

	private File exportZip(String rsn, List<BotwApi.Shot> shots)
	{
		File folder = ClanSuiteFiles.exports();
		if (!folder.exists() && !folder.mkdirs())
		{
			log.warn("Could not make the export folder {}", folder);
			return null;
		}

		File zip = new File(folder, safe(challengeName) + " - " + safe(rsn) + ".zip");

		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip)))
		{
			int written = 0;

			for (BotwApi.Shot shot : shots)
			{
				BotwApi.Result<byte[]> image =
					api.readShot(serverUrl, challengeCode, creatorToken, shot.getEventId());

				if (!image.ok())
				{
					// One unreadable picture should not lose the other nineteen.
					log.debug("Skipping {} in the export: {}", shot.getEventId(), image.getError());
					continue;
				}

				String name = WHEN.format(Instant.ofEpochMilli(shot.getOccurredAt()))
					.replace(':', '-').replace(',', ' ')
					+ " " + safe(shot.getItemName()) + ".jpg";

				out.putNextEntry(new ZipEntry(name));
				out.write(image.getValue());
				out.closeEntry();
				written++;
			}

			log.debug("Exported {} screenshots to {}", written, zip);
			return zip;
		}
		catch (IOException e)
		{
			log.warn("Could not write the export", e);
			return null;
		}
	}

	/**
	 * Says where the export went, and puts the path on the clipboard so it can be pasted straight into
	 * a file browser.
	 * <p>
	 * It would be nicer to open the folder, and this did. Both ways of doing that — AWT's
	 * {@code Desktop} and RuneLite's {@code LinkBrowser} — are restricted on the plugin hub, because
	 * handing a local path to the system to open is handing it something to execute. Copying the path
	 * is the way round it that the hub's maintainers point people to, and it costs the reader one
	 * paste.
	 */
	private void reveal(File zip)
	{
		String path = zip.getAbsolutePath();

		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(path), null);

			Cards.warn(this, "Saved to " + path + System.lineSeparator()
				+ System.lineSeparator() + "The path is on your clipboard.");
		}
		catch (RuntimeException e)
		{
			// A clipboard can be unavailable or held by something else. The path is the point, so it
			// is still shown; only the convenience is lost.
			log.debug("Could not copy the export path", e);
			Cards.warn(this, "Saved to " + path);
		}
	}

	private static String safe(String name)
	{
		String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
		return cleaned.isEmpty() ? "Unnamed" : cleaned;
	}

	private JPanel loading()
	{
		JPanel panel = new JPanel();
		panel.setBackground(Theme.BACKGROUND);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(muted("Loading evidence…"));
		return panel;
	}

	private JLabel muted(String text)
	{
		JLabel label = new JLabel("<html><body style='width:165px'>" + text + "</body></html>");
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT_MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
