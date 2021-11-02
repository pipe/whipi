
package pe.pi.whipi;

import com.phono.srtplight.Log;
import java.io.IOException;

/**
 *
 * @author thp
 */
public class CommandLine {
    
    
    public static void main(String args[]) throws IOException{
        Log.setLevel(Log.DEBUG);
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
                default: System.out.println("command not understood ");
            }
        }while ((c> 0) && (whip.isRunning()));
        
    }
}
