package com.clansuite.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.border.Border;
import net.runelite.client.ui.FontManager;

/**
 * The look of the thing.
 * <p>
 * RuneLite's own palette is flat grey on flat grey, which is right for a tool you glance at and wrong
 * for something people open to see whether they are winning. This is a darker, slightly blue base with
 * gold on top of it — gold because that is what a scoreboard is, and because it reads as a prize
 * rather than as an interface.
 * <p>
 * Kept in one place so a colour can be changed once rather than in nine panels.
 */
public final class Theme
{
	/** Near-black with a little blue in it. Flat grey is what made the first attempt look dreary. */
	public static final Color BACKGROUND = new Color(24, 25, 31);

	/** Cards sit just above the background rather than being outlined, which is quieter and reads better. */
	public static final Color CARD = new Color(35, 37, 46);
	public static final Color CARD_HOVER = new Color(45, 48, 60);

	/** The prize colour. Used for anything that is a score, a code, or a call to action. */
	public static final Color GOLD = new Color(240, 176, 62);
	public static final Color GOLD_DIM = new Color(150, 110, 40);

	/** For a challenge that is live right now. */
	public static final Color LIVE = new Color(93, 200, 120);

	public static final Color TEXT = new Color(228, 228, 232);
	public static final Color TEXT_MUTED = new Color(150, 152, 163);

	private Theme()
	{
	}

	/**
	 * The plugin's own name, larger than anything else on the panel.
	 */
	public static Font title()
	{
		return FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 22f);
	}

	/** The label on a tile button. Big enough to read without looking for it. */
	public static Font tile()
	{
		return FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 15f);
	}

	public static Font heading()
	{
		return FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 14f);
	}

	public static Font body()
	{
		return FontManager.getRunescapeSmallFont();
	}

	/**
	 * A number worth looking at — a score, a countdown, a challenge code.
	 */
	public static Font figure(float size)
	{
		return FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size);
	}

	/**
	 * Swing draws a text field white unless told otherwise, which is where the bright box in the
	 * middle of a dark panel came from.
	 */
	public static JTextField textField(JTextField field)
	{
		field.setBackground(CARD);
		field.setForeground(TEXT);
		field.setCaretColor(GOLD);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(58, 61, 74)),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		field.setFont(body());
		return field;
	}

	public static Border cardBorder()
	{
		return BorderFactory.createEmptyBorder(8, 10, 8, 10);
	}

	/**
	 * Turns antialiasing on before anything is drawn by hand. Without it the rounded corners look
	 * chewed.
	 */
	public static Graphics2D smooth(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return graphics;
	}
}
