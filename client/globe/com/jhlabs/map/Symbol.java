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

package com.jhlabs.map;

import java.awt.*;

/**
 * A map symbol.
 */
public interface Symbol {
	/**
	 * Paint the symbol. The symbol is drawn with its hot spot at x,y.
	 */
	public void paintSymbol( Graphics2D g, int x, int y );
	
	/**
	 * Get the width of the symbol.
	 */

	public int getSymbolWidth();

	/**
	 * Get the height of the symbol.
	 */
	public int getSymbolHeight();

	/**
	 * Get the position of the left side of the symbol relative to the hot spot.
	 * This will nearly always be negative.
	 */
	public int getSymbolXOrigin();


	/**
	 * Get the position of the top side of the symbol relative to the hot spot.
	 * This will nearly always be negative.
	 */
	public int getSymbolYOrigin();
}

