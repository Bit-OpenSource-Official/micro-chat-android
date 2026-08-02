package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MessageButtonRowsTest {
	@Test
	public void groupsButtonsByServerRowAndKeepsSwipeFlag() {
		ArrayList<MiniTaLib.Button> buttons = new ArrayList<MiniTaLib.Button>();
		buttons.add(new MiniTaLib.Button("Previous", "", "previous", 0, 0, false));
		buttons.add(new MiniTaLib.Button("Next", "", "next", 0, 0, false));
		buttons.add(new MiniTaLib.Button("Confirm", "", "confirm", 0, 2, true));

		List<List<MiniTaLib.Button>> rows = MessageButtonRows.group(buttons);

		assertEquals(3, rows.size());
		assertEquals(2, rows.get(0).size());
		assertNull(rows.get(1));
		assertEquals("Confirm", rows.get(2).get(0).text);
		assertTrue(rows.get(2).get(0).swipeConfirm);
	}
}
