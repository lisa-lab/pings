/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.map.symbol;

import java.awt.*;
import com.jhlabs.map.*;

public class FontSymbol implements Symbol {
	private Font font;
	private char character;
	private int width;
	private int height;

	public FontSymbol() {
	}
	
	public FontSymbol(Font font, char character) {
		this.font = font;
		this.character = character;
		initialize();
	}
	
	public void setFont(Font font) {
		this.font = font;
		initialize();
	}

	public Font getFont() {
		return font;
	}

	public void setCharacter(char character) {
		this.character = character;
		initialize();
	}

	public char getCharacter() {
		return character;
	}

	private void initialize() {
		FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(font);
		width = fm.charWidth(character);
		height = fm.getAscent();
	}

	public Point getHotSpot() {
		return new Point(width/2, height);
	}

	public void paintSymbol( Graphics2D g, int x, int y ) {
		Graphics2D g2d = (Graphics2D)g;
		g2d.setFont(font);
		g2d.drawChars(new char[] { character }, 0, 1, x-width/2, y+height);
	}
	
	public int getSymbolWidth() {
		return width;
	}
	
	public int getSymbolHeight() {
		return height;
	}

	public int getSymbolXOrigin() {
		return -width/2;
	}
	
	public int getSymbolYOrigin() {
		return -height;
	}
}
