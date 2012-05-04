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
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import com.jhlabs.map.*;
import com.jhlabs.map.shapefile.*;

/**
 * The interface defining a map layer
 */
public class ShapefileLayer extends Layer {

	private URL url;

	public ShapefileLayer() {
	}
	
	public ShapefileLayer( URL url, Style style ) throws ShapefileException, IOException {
		this( url, null, style );
	}
	
	public ShapefileLayer( URL url, URL dbfURL, Style style ) throws ShapefileException, IOException {
		super( style );
		this.url = url;

Style pointStyle = new Style();
pointStyle.setSymbol( new com.jhlabs.map.symbol.StarSymbol( 5, true ) );
pointStyle.setSymbolPaint( Color.red );
pointStyle.setSymbolSize( 10 );
pointStyle.setScaleSymbol( false );
pointStyle.setFont( new Font( "sansserif", Font.PLAIN, 10 ) );
pointStyle.setTextPaint( Color.darkGray );
pointStyle.setTextSize( 10 );
pointStyle.setScaleText( false );

		InputStream is = url.openStream();
		Shapefile shapefile;
		if ( dbfURL != null )
			shapefile = new Shapefile( is, null, dbfURL.openStream() );
		else
			shapefile = new Shapefile( is );
		int count = shapefile.getNumRecords();
		for ( int row = 0; row < count; row++ ) {
			ShapefileShape shape = shapefile.readShape();
			if ( shape == null )
				break;//FIXME-shouldn't happen
			Shape s = shape.toShape();
			if ( s != null )
{
				addFeature( new Feature( s, null ) );
//FIXME----
if ( false ) {
String text = (String)shapefile.getField( row, 1 );
Rectangle2D r = s.getBounds();
addFeature( new PointFeature( r.getCenterX(), r.getCenterY(), text, pointStyle ) );
}
}
//FIXME----
		}
	}

	public void setURL( URL url ) {
		this.url = url;
	}
	
	public URL getURL() {
		return url;
	}
	
	public String toString() {
		return "Shapefile: "+url.getFile();
	}
}
