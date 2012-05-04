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
import java.awt.geom.*;
import com.jhlabs.map.*;

/**
 * A set of default Symbols created at startup.
 */
public class BasicSymbolSet implements SymbolSet {
	private String name;
	private Symbol[] symbols;

	public BasicSymbolSet(String name, Symbol[] symbols) {
		this.name = name;
		this.symbols = symbols;
	}
	
	public BasicSymbolSet(String name) {
		this.name = name;
		this.symbols = new Symbol[] {
			new OvalSymbol(3, true),
			new OvalSymbol(5, true),
			new OvalSymbol(7, true),
			new OvalSymbol(9, true),
			new OvalSymbol(3, false),
			new OvalSymbol(5, false),
			new OvalSymbol(7, false),
			new OvalSymbol(9, false),
			new RectangleSymbol(3, true),
			new RectangleSymbol(5, true),
			new RectangleSymbol(7, true),
			new RectangleSymbol(9, true),
			new RectangleSymbol(3, false),
			new RectangleSymbol(5, false),
			new RectangleSymbol(7, false),
			new RectangleSymbol(9, false),
			new PolygonSymbol(3, true),
			new PolygonSymbol(4, true),
			new PolygonSymbol(5, true),
			new PolygonSymbol(6, true),
			new PolygonSymbol(8, true),
			new PolygonSymbol(3, false),
			new PolygonSymbol(4, false),
			new PolygonSymbol(5, false),
			new PolygonSymbol(6, false),
			new PolygonSymbol(8, false),
			new StarSymbol(4, true),
			new StarSymbol(5, true),
			new StarSymbol(6, true),
			new StarSymbol(7, true),
			new StarSymbol(8, true),
			new StarSymbol(9, true),
			new StarSymbol(4, false),
			new StarSymbol(5, false),
			new StarSymbol(6, false),
			new StarSymbol(7, false),
			new StarSymbol(8, false),
			new StarSymbol(9, false),
			new CrossSymbol(4),
			new CrossSymbol(5),
			new CrossSymbol(6),
			new CrossSymbol(7),
			new CrossSymbol(8),
		};
	}
	
	public int getNumSymbols() {
		return symbols.length;
	}
	
	public Symbol getSymbol(int index) {
		return symbols[index];
	}

	public String toString() {
		return name;
	}
}

