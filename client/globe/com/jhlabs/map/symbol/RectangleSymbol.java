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

public class RectangleSymbol implements Symbol {

	private int size;
	private boolean fill;

	public RectangleSymbol(int size, boolean fill) {
		this.size = size;
		this.fill = fill;
	}
	
	public void paintSymbol( Graphics2D g, int x, int y ) {
		int size2 = size/2;
		if (fill)
			g.fillRect(x-size2, y-size2, size, size);
		else
			g.drawRect(x-size2, y-size2, size-1, size-1);
	}
	
	public int getSymbolWidth() {
		return size;
	}
	
	public int getSymbolHeight() {
		return size;
	}

	public int getSymbolXOrigin() {
		return -size/2;
	}
	
	public int getSymbolYOrigin() {
		return -size/2;
	}
}
