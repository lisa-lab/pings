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
import java.net.*;
import com.jhlabs.map.*;

public class ImageSymbol implements Symbol {

    protected final static Component component = new Component() {};
    protected final static MediaTracker tracker = new MediaTracker(component);

	private String imageFile;
	private Image image;
	private int width;
	private int height;
	private int xHotSpot;
	private int yHotSpot;

	public ImageSymbol() {
	}
	
	public ImageSymbol(String filename) {
		this.imageFile = filename;
		image = Toolkit.getDefaultToolkit().getImage(filename);
		initialize();
	}

	public ImageSymbol(Image image) {
		this.image = image;
		initialize();
	}
	
	public void setImage(Image image) {
		this.image = image;
		initialize();
	}

	public Image getImage() {
		return image;
	}

	public void setImageFile(String imageFile) {
		this.imageFile = imageFile;
	}

	public String getImageFile() {
		return imageFile;
	}

	private void initialize() {
		synchronized(tracker) {
			tracker.addImage(image, 0);
			try {
				tracker.waitForID(0, 5000);
			} catch (InterruptedException e) {
			}
			tracker.removeImage(image, 0);

			width = image.getWidth(null);
			height = image.getHeight(null);
			xHotSpot = width/2;
			yHotSpot = height/2;
		}
	}

	public Point getHotSpot() {
		return new Point(xHotSpot, yHotSpot);
	}

	public void paintSymbol( Graphics2D g, int x, int y ) {
		Graphics2D g2d = (Graphics2D)g;
		g2d.drawImage( image, x-xHotSpot, y-yHotSpot, null );
	}
	
	public int getSymbolWidth() {
		return width;
	}
	
	public int getSymbolHeight() {
		return height;
	}

	public int getSymbolXOrigin() {
		return -xHotSpot;
	}
	
	public int getSymbolYOrigin() {
		return -yHotSpot;
	}

}

