package ru.e6atb.chat;

import java.util.ArrayList;
import java.util.List;

final class MessageButtonRows {
	private MessageButtonRows() {
	}

	static List<List<MiniTaLib.Button>> group(List<MiniTaLib.Button> buttons) {
		ArrayList<List<MiniTaLib.Button>> rows = new ArrayList<List<MiniTaLib.Button>>();
		if (buttons == null) return rows;
		for (MiniTaLib.Button button : buttons) {
			if (button == null || button.text == null || button.text.length() == 0) continue;
			int rowIndex = Math.max(0, Math.min(11, button.row));
			while (rows.size() <= rowIndex) rows.add(null);
			List<MiniTaLib.Button> row = rows.get(rowIndex);
			if (row == null) {
				row = new ArrayList<MiniTaLib.Button>();
				rows.set(rowIndex, row);
			}
			row.add(button);
		}
		return rows;
	}
}
