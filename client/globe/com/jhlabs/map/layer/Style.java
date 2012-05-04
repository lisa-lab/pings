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

package com.jhlabs.map.layer;

import java.awt.*;
import javax.swing.*;
import com.jhlabs.map.*;
import com.jhlabs.map.symbol.*;

public class Style implements Cloneable {
	
	public static final float[] DS_SOLID = null;
	public static final float[] DS_DASH = { 8, 4 };
	public static final float[] DS_DOT = { 4, 4 };
	public static final float[] DS_DASH_DOT = { 8, 4, 4, 4 };
	public static final float[] DS_DASH_DOT_DOT = { 8, 4, 4, 4, 4, 4 };
	public static final float[][] dashStyles = { DS_SOLID, DS_DASH, DS_DOT, DS_DASH_DOT, DS_DASH_DOT_DOT };

	public static final int ARROW_NONE = 0;
	public static final int ARROW_FLAT = 1;
	public static final int ARROW_POINT = 2;

	public static final int SCALE_TEXT = 0x01;
	public static final int SCALE_LINE_WIDTH = 0x02;
	public static final int SCALE_SYMBOL = 0x04;
	public static final int CHANGED = 0x08;

	private Object code = null;
	private Paint strokePaint = Color.black;
	private Paint fillPaint = null;
	private Paint textPaint = Color.black;
	private Paint symbolPaint = null;
	private Symbol symbol = null;
	public Font font = null;
//	private SymbolSet symbolSet = null;
	private int symbolID = 0;
	private float symbolSize = 1.0f;
	private boolean visible = true;
	private boolean printable = true;
	private Stroke stroke;
	private int lineCap;
	private int lineJoin = 0;
	private float lineWidth = 1;
	private float miter;
	private int dash = 0;
	private String description = null;
	private int minWidth;
	private int maxWidth;
	private int compositeRule = AlphaComposite.SRC_OVER;
	private float compositeAlpha = 1.0f;
	private short startArrowHead = 0;
	private short endArrowHead = 0;
	private short flags = 0;
	private float minTextSize = 0;
	private float maxTextSize = 0;
	private float textOffset;
	private byte textAnchor = 0;//FIXME-NORTHWEST;
	private float textSize = 1.0f;
//	private Theme theme;
	private Object mapper;
	private double minScale = 0;
	private double maxScale = Double.MAX_VALUE;
//	private StyleMapper styleMapper;

	public Style() {
	}
	
	public Style( Paint strokePaint, Paint fillPaint ) {
		this.strokePaint = strokePaint;
		this.fillPaint = fillPaint;
	}
	
	public Style( Object code ) {
		this.code = code;
	}
	
	public Object clone() {
		try {
			return (Style)super.clone();
		}
		catch (CloneNotSupportedException e) {
			return null;
		}
	}

	/**
	 * Set the theme which owns this style. This will be null if this style is not part of a Theme.
	 */
//	public void setTheme(Theme theme) {
//		this.theme = theme;
//	}
//
//	/**
//	 * Get the theme which owns this style. This will be null if this style is not part of a Theme.
//	 */
//	public Theme getTheme() {
//		return theme;
//	}
//
//	public void setStyleMapper(StyleMapper styleMapper) {
//		this.styleMapper = styleMapper;
//	}
//
//	public StyleMapper getStyleMapper() {
//		return styleMapper;
//	}

	public void setVisible(boolean b) {
		this.visible = b;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setPrintable(boolean b) {
		this.printable = b;
	}

	public boolean isPrintable() {
		return printable;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getCode() {
		return code.toString();
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description == null ? getCode() : description;
	}

	public void setStroke(Stroke stroke) {
		this.stroke = stroke;
	}

	public Stroke getStroke(double scale) {
		if (stroke != null)
			return stroke;
		if (getScaleLineWidth())
			return new BasicStroke((float)(lineWidth*scale), lineCap, lineJoin, 10.0f, dashStyles[dash], 0);
		return new BasicStroke(lineWidth, lineCap, lineJoin, 10.0f, dashStyles[dash], 0);
	}

	public void setStrokePaint(Paint strokePaint) {
		this.strokePaint = strokePaint;
	}

	public Paint getStrokePaint() {
		return strokePaint;
	}

	public void setFillPaint(Paint fillPaint) {
		fillPaint = fillPaint;
	}

	public Paint getFillPaint() {
		return fillPaint;
	}

	public void setTextPaint(Paint textPaint) {
		this.textPaint = textPaint;
	}

	public Paint getTextPaint() {
		return textPaint;
	}

	public void setSymbolPaint(Paint symbolPaint) {
		this.symbolPaint = symbolPaint;
	}

	public Paint getSymbolPaint() {
		return symbolPaint;
	}

	public void setFont(Font font) {
		this.font = font;
	}

	public Font getFont() {
		return font;
	}

/*
	public void setSymbolSet(SymbolSet symbolSet) {
		this.symbolSet = symbolSet;
	}

	public SymbolSet getSymbolSet() {
		return symbolSet;
	}

	public void setSymbolID(int symbolID) {
		this.symbolID = symbolID;
	}

	public int getSymbolID() {
		return symbolID;
	}

	public Symbol getSymbol() {
		return symbolSet != null ? symbolSet.getSymbol(symbolID) : null;
	}
*/
	public void setSymbol( Symbol symbol ) {
		this.symbol = symbol;
	}

	public Symbol getSymbol() {
		return symbol;
	}

	private void setFlag(int flag, boolean b) {
		if (b)
			flags |= flag;
		else
			flags &= ~flag;
	}
	
	public void setScaleLineWidth(boolean scaleLineWidth) {
		setFlag(SCALE_LINE_WIDTH, scaleLineWidth);
	}

	public boolean getScaleLineWidth() {
		return (flags & SCALE_LINE_WIDTH) != 0;
	}

	public void setScaleSymbol(boolean scaleSymbol) {
		setFlag(SCALE_SYMBOL, scaleSymbol);
	}

	public boolean getScaleSymbol() {
		return (flags & SCALE_SYMBOL) != 0;
	}

	public void setScaleText(boolean scaleText) {
		setFlag(SCALE_TEXT, scaleText);
	}

	public boolean getScaleText() {
		return (flags & SCALE_TEXT) != 0;
	}

	public void setChanged(boolean changed) {
		setFlag(CHANGED, changed);
	}

	public boolean isChanged() {
		return (flags & CHANGED) != 0;
	}

	public void setMinTextSize(float minTextSize) {
		this.minTextSize = minTextSize;
	}

	public float getMinTextSize() {
		return minTextSize;
	}

	public void setMaxTextSize(float maxTextSize) {
		this.maxTextSize = maxTextSize;
	}

	public float getMaxTextSize() {
		return maxTextSize;
	}

	public void setTextOffset(float textOffset) {
		this.textOffset = textOffset;
	}

	public float getTextOffset() {
		return textOffset;
	}

	public void setTextAnchor(int textAnchor) {
		this.textAnchor = (byte)textAnchor;
	}

	public byte getTextAnchor() {
		return textAnchor;
	}
	
	public void setSymbolSize(float symbolSize) {
		this.symbolSize = symbolSize;
	}

	public float getSymbolSize() {
		return symbolSize;
	}

	public void setTextSize(float textSize) {
		this.textSize = textSize;
	}

	public float getTextSize() {
		return textSize;
	}

	public void setMinScale(double minScale) {
		this.minScale = minScale;
	}

	public double getMinScale() {
		return minScale;
	}

	public void setMaxScale(double maxScale) {
		this.maxScale = maxScale;
	}

	public double getMaxScale() {
		return maxScale;
	}

	public Style getThemeStyle(Feature feature) {
		return this;
	}

	public String toString() {
		return description == null ? (code == null ? "none" : code.toString()) : description;
	}

}
