package com.clansuite.botw.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One drop the challenge counts, and what it is worth.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DropRule
{
	private String name = "";

	/**
	 * Used only to draw the item's icon. Matching is done on the name, because that is what the game
	 * hands us when something drops and it is what the creator sees when setting the points.
	 */
	private int itemId = -1;

	private int points;
}
