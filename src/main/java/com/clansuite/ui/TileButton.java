package com.clansuite.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * A big thing to press.
 * <p>
 * The two actions this plugin exists for — make a challenge, join one — were ordinary little buttons
 * lost among the text. They are the reason anyone opens the panel, so they are now tiles: tall,
 * rounded, gold-edged, and impossible to miss.
 * <p>
 * Painted rather than styled because Swing's button will not round its own corners, and a flat
 * rectangle is most of what made the first attempt look like a form.
 */
public class TileButton extends JPanel
{
	private static final int HEIGHT = 54;
	private static final int RADIUS = 10;

	private final String label;
	private final String sublabel;
	private final Runnable onClick;

	private boolean hovered;

	public TileButton(String label, String sublabel, Runnable onClick)
	{
		this.label = label;
		this.sublabel = sublabel;
		this.onClick = onClick;

		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setPreferredSize(new Dimension(0, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hovered = false;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event))
				{
					onClick.run();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D graphics = Theme.smooth((Graphics2D) g.create());
		int width = getWidth();
		int height = getHeight();

		graphics.setColor(hovered ? Theme.CARD_HOVER : Theme.CARD);
		graphics.fillRoundRect(0, 0, width - 1, height - 1, RADIUS, RADIUS);

		graphics.setColor(hovered ? Theme.GOLD : Theme.GOLD_DIM);
		graphics.drawRoundRect(0, 0, width - 1, height - 1, RADIUS, RADIUS);

		// A gold edge down the left, so the tile reads as a button rather than as a box of text.
		graphics.fillRoundRect(0, 0, 4, height - 1, RADIUS, RADIUS);

		graphics.setColor(Theme.GOLD);
		graphics.setFont(Theme.tile());
		int labelY = sublabel == null ? height / 2 + 6 : height / 2 - 1;
		graphics.drawString(label, 16, labelY);

		if (sublabel != null)
		{
			graphics.setColor(Theme.TEXT_MUTED);
			graphics.setFont(Theme.body());
			graphics.drawString(sublabel, 16, labelY + 15);
		}

		graphics.dispose();
	}

	/**
	 * Kept so a tile can be greyed while something is happening, without swapping the component out.
	 */
	public void setEnabledLook(boolean enabled)
	{
		setEnabled(enabled);
		setCursor(java.awt.Cursor.getPredefinedCursor(
			enabled ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
		repaint();
	}

	static Color textColour()
	{
		return Theme.TEXT;
	}
}
