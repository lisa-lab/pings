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
import java.io.*;
import com.jhlabs.map.*;

public class FontSymbolSet implements SymbolSet {
	private Font font;
	private Symbol[] symbols = new Symbol[256];

	public FontSymbolSet() {
	}
	
	public FontSymbolSet(Font font) {
		this.font = font;
	}
	
	public FontSymbolSet(String name, String fontFileName) {
		try {
			File file = new File(fontFileName);
			font = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(file));
			font = font.deriveFont(18.0f);
		}
		catch (Exception e) {
			e.printStackTrace();
			font = new Font(name, Font.PLAIN, 18);
		}
	}

	public void setFont(Font font) {
		this.font = font;
	}

	public Font getFont() {
		return font;
	}

	public int getNumSymbols() {
		return symbols.length;
	}
	
	public Symbol getSymbol(int index) {
		if (symbols[index] == null)
			symbols[index] = new FontSymbol(font, (char)index);
		return symbols[index];
	}

	public String toString() {
		return font.getFamily();
	}
}
