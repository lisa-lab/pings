package com.jhlabs.io;

import java.io.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * A reader for GHHHS data files
 */
public class GSHHSReader {
    
    private int index = 0;
    private DataInputStream dis;
    
	public GSHHSReader( InputStream is ) {
        dis = new DataInputStream( new BufferedInputStream( is ) );
    }
    
    public Shape readShape() throws IOException {
        GeneralPath p = null;
        for (;;) {
            try {
                int id = dis.readInt();
                int n = dis.readInt();
                int level = dis.readInt();
                float west = dis.readInt() * 1e-6f;
                float east = dis.readInt() * 1e-6f;
                float south = dis.readInt() * 1e-6f;
                float north = dis.readInt() * 1e-6f;
                int area = dis.readInt();
                int version = dis.readInt();
                short greenwich = dis.readShort();
                short source = dis.readShort();
                
                if ( level == 1 )
                    p = new GeneralPath();
                for ( int i = 0; i < n; i++ ) {
                    float lon = dis.readInt() * 1e-6f;
                    float lat = dis.readInt() * 1e-6f;
                    
                    //            lon = (h.greenwich && p.x > max_east) ? p.x * 1.0e-6 - 360.0 : p.x * 1.0e-6;
                    if ( level == 1 ) {
                        if ( i == 0 )
                            p.moveTo( lon, lat );
                        else
                            p.lineTo( lon, lat );
                    }
                }
                if ( level == 1 ) {
                    p.closePath();
                    return p;
                }
            }
            catch ( EOFException e ) {
                dis.close();
                return null;
            }
        }
    }
    
}
