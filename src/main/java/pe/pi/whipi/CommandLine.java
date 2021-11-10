/*
    A WHIP client for raspberry pi
    Copyright (C) 2021  Tim Panton

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package pe.pi.whipi;

import com.phono.srtplight.Log;
import java.io.IOException;
import java.util.Hashtable;

/**
 *
 * @author thp
 */
public class CommandLine {
    
    
    public static void main(String args[]) throws IOException{
        Log.setLevel(Log.INFO);
        String url = null;
        String token = null;
        if (args.length > 0){
            url =   args[0];
        }
        if (args.length > 1) {
            token = args[1];
        }
        Whipi.checkPi();
        if (url == null){
            Log.info("QRcode scan here?");
            System.exit(0);
        } 
        Whipi whip = new Whipi(url,token);
        whip.start();
        int c = -1;
        do {
            c =  System.in.read();
            switch (c){
                case 'q':whip.quit();break;
                case 'a':
                    Hashtable<String,Long> stats = whip.getAudioStats();
                    System.out.println("Audio stats");
                    stats.forEach((k,v)-> {System.out.println("\t"+k+"="+v);});
                    break;
                case 'v':
                    Hashtable<String,Long> vstats = whip.getVideoStats();
                    System.out.println("Video stats");
                    vstats.forEach((k,v)-> {System.out.println("\t"+k+"="+v);});
                    break;
                case '\r':
                case '\n':
                    break;
                default: System.out.println("command not understood ");
            }
        }while ((c> 0) && (whip.isRunning()));
        
    }
}
