package com.clansuite.capture;

import com.clansuite.ClanSuiteFiles;
import com.clansuite.botw.data.Challenge;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageCapture;

/**
 * Saves a picture of the moment a scoring drop landed.
 * <p>
 * A clan that has always verified drops with screenshots will keep wanting them, and asking people to
 * remember to press a key at the exact moment a pet drops is how you end up with no evidence at all.
 * <p>
 * Filed one folder per challenge, because the point is finding them again: "who has proof of that
 * visage on the Vorkath week" should be one folder, not a scroll through a thousand images named after
 * timestamps.
 * <p>
 * The full-size original stays on the player's own machine. Only a smaller copy is sent to the
 * challenge's creator, and only when the player has left that setting on.
 */
@Slf4j
@Singleton
public class Screenshotter
{
	private static final DateTimeFormatter STAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss", Locale.ENGLISH);

	/** How wide the creator's copy is aimed at. Measured: at 1520 the game's own text reads cleanly. */
	private static final int MAX_WIDTH = 1520;

	/** …but never shrink past this much of the original, however large the client is. */
	private static final double MIN_SCALE = 0.75;

	/** What is polite to send and to keep. The service's own limit is a good deal higher. */
	private static final int BUDGET_BYTES = 300 * 1024;

	private static final float QUALITY = 0.80f;

	/** Used only when the picture is too big at the first quality — a very large client. */
	private static final float LOWER_QUALITY = 0.65f;

	private final DrawManager drawManager;
	private final ImageCapture imageCapture;

	@Inject
	private Screenshotter(DrawManager drawManager, ImageCapture imageCapture)
	{
		this.drawManager = drawManager;
		this.imageCapture = imageCapture;
	}

	/**
	 * Takes a shot of the next frame and files it under this challenge.
	 *
	 * @param challengeName what the creator called it, used as the folder
	 * @param itemName      what dropped, used in the file name so a folder is skimmable
	 */
	public void capture(String challengeName, String itemName)
	{
		capture(challengeName, itemName, null, null);
	}

	/**
	 * @param challengeCode when set, a small copy is also sent to the challenge's creator
	 * @param eventId       ties the picture to the drop it is evidence of
	 */
	public void capture(String challengeName, String itemName, String challengeCode, String eventId)
	{
		// The next frame rather than this one: the drop has only just been announced, and the item is
		// not on screen yet when the event fires.
		drawManager.requestNextFrameListener(image ->
			save(image, challengeName, itemName, challengeCode, eventId));
	}

	private void save(Image image, String challengeName, String itemName,
		String challengeCode, String eventId)
	{
		try
		{
			// With the client frame, so the shot shows the whole window rather than the game viewport
			// alone. That is what makes it read as evidence rather than as a cropped picture.
			BufferedImage shot = imageCapture.addClientFrame(image);

			File folder = ClanSuiteFiles.screenshots(safe(challengeName));
			if (!folder.exists() && !folder.mkdirs())
			{
				log.warn("Could not make the screenshot folder {}", folder);
				return;
			}

			String fileName = LocalDateTime.now().format(STAMP) + " " + safe(itemName) + ".png";
			File file = new File(folder, fileName);

			ImageIO.write(shot, "png", file);
			log.debug("Saved {}", file);

			if (challengeCode != null && eventId != null && uploader != null)
			{
				// Best effort, and off this thread. The full-size copy is already on disk, so a failed
				// upload costs the creator a thumbnail rather than the player their evidence.
				uploader.send(challengeCode, eventId, itemName, thumbnail(shot));
			}
		}
		catch (IOException | RuntimeException e)
		{
			// A failed screenshot must never cost anyone their points; the event has already been
			// recorded by the time this runs.
			log.warn("Could not save a screenshot", e);
		}
	}

	/**
	 * The copy that goes to the creator: small enough to store, sharp enough to read.
	 * <p>
	 * It has to be readable, not merely recognisable. What catches a faked drop is usually the game's
	 * own writing — the drop message, or a script's text sitting in the corner where the mouse tooltip
	 * belongs — and that is the first thing to dissolve when a picture is shrunk. An earlier version of
	 * this was six hundred pixels wide and none of that text survived.
	 */
	private static byte[] thumbnail(BufferedImage shot) throws IOException
	{
		return encode(scale(shot, targetWidth(shot.getWidth())), QUALITY, LOWER_QUALITY);
	}

	/** As wide as is worth sending, given how wide the client was. */
	static int targetWidth(int sourceWidth)
	{
		// Two rules, because a single width is wrong at one end or the other. The game draws its text at
		// a fixed pixel size no matter how big the window is, so what decides whether that text survives
		// is how far the picture is shrunk, not what it is shrunk to: a hard 1520 would be generous to a
		// small client and would reduce a 4K one by more than half, which is where the writing goes. So
		// the target is 1520, unless holding to it would mean shrinking past MIN_SCALE — and never more
		// than the client actually gave us.
		return Math.min(sourceWidth, Math.max(MAX_WIDTH, (int) Math.round(sourceWidth * MIN_SCALE)));
	}

	/**
	 * Shrinks by halves rather than in one jump.
	 * <p>
	 * One bilinear step samples four neighbouring pixels, so going straight from 1920 to 600 throws
	 * away most of the picture and turns small writing into grey fuzz. Halving repeatedly means every
	 * pixel of the original contributes to the result.
	 */
	private static BufferedImage scale(BufferedImage source, int width)
	{
		BufferedImage current = source;
		int currentWidth = source.getWidth();
		int currentHeight = source.getHeight();

		while (currentWidth / 2 > width)
		{
			currentWidth /= 2;
			currentHeight = Math.max(1, currentHeight / 2);
			current = draw(current, currentWidth, currentHeight);
		}

		int height = Math.max(1, source.getHeight() * width / Math.max(1, source.getWidth()));
		return draw(current, width, height);
	}

	private static BufferedImage draw(BufferedImage source, int width, int height)
	{
		// INT_RGB rather than ARGB: JPEG has no transparency, and handing an alpha channel to the JPEG
		// writer is what produces those pictures that come back with the colours inverted.
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = out.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(source, 0, 0, width, height, null);
		graphics.dispose();
		return out;
	}

	/**
	 * JPEG, at the best quality that fits the budget.
	 * <p>
	 * ImageIO's default is around 0.75 and cannot be relied on across machines, so it is set here. The
	 * fallback is for the unusually large client: rather than refuse to send anything, or send
	 * something the service will reject, it drops the quality once and sends that.
	 */
	private static byte[] encode(BufferedImage image, float... qualities) throws IOException
	{
		byte[] encoded = null;

		for (float quality : qualities)
		{
			encoded = write(image, quality);
			if (encoded.length <= BUDGET_BYTES)
			{
				return encoded;
			}
		}

		return encoded;
	}

	private static byte[] write(BufferedImage image, float quality) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes))
		{
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}

		return bytes.toByteArray();
	}

	/**
	 * Where a thumbnail goes once it has been made. Set by the plugin rather than injected, because the
	 * uploader needs the panel's configuration and this class does not.
	 */
	public interface Uploader
	{
		void send(String challengeCode, String eventId, String itemName, byte[] jpeg);
	}

	private Uploader uploader;

	public void setUploader(Uploader uploader)
	{
		this.uploader = uploader;
	}

	/**
	 * A name that will survive being a folder or a file. Challenge names are typed by people and will
	 * contain colons, slashes and whatever else.
	 */
	private static String safe(String name)
	{
		String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
		return cleaned.isEmpty() ? "Unnamed" : cleaned;
	}
}
