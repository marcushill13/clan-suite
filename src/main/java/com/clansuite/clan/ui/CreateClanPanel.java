package com.clansuite.clan.ui;

import com.clansuite.ui.Cards;
import com.clansuite.ui.Theme;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Making a clan, which is two boxes and a button.
 * <p>
 * Deliberately short. Everything else about a clan — whether it is listed, whether it is recruiting,
 * who runs it — can be changed afterwards and has a sensible answer to start with, so asking about any
 * of it here would be asking somebody to make decisions before they have seen the thing they are
 * deciding about.
 */
public class CreateClanPanel extends JPanel
{
	private final JTextField name = Theme.textField(new JTextField());
	private final JTextField tagline = Theme.textField(new JTextField());

	/**
	 * @param onCreate name and tagline, once they press the button
	 */
	public CreateClanPanel(BiConsumer<String, String> onCreate, Runnable onCancel)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);

		add(Cards.title("Create a clan"));
		add(Cards.gap(4));
		add(Cards.muted("You will be its owner. Everything else can be changed later."));
		add(Cards.gap(12));

		add(Cards.field("Clan name", name));
		add(Cards.gap(8));
		add(Cards.field("Tagline", tagline));
		add(Cards.gap(4));
		add(Cards.muted("One line, shown in the clan list. Your timezone, your focus, whatever people "
			+ "need to know before they apply."));

		add(Cards.gap(14));

		add(button("Create clan", () ->
		{
			if (name.getText().trim().isEmpty())
			{
				Cards.warn(this, "Give the clan a name first.");
				return;
			}

			onCreate.accept(name.getText().trim(), tagline.getText().trim());
		}));

		add(Cards.gap(6));
		add(button("Back", onCancel));
	}

	private Component button(String label, Runnable onClick)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		javax.swing.JButton press = Cards.button(label);
		press.addActionListener(event -> onClick.run());
		row.add(press);

		return row;
	}
}
