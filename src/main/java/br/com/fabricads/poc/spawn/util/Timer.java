package br.com.fabricads.poc.spawn.util;

/*
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
import java.util.Date;
/**
 * Class for program event timing. Usage:
 *
 * <pre>
 * Timer timer = new Timer();
 *
 * // do stuff
 *
 * System.out.println( timer ); // prints time elapsed since
 * // object was created.
 *
 * </pre>
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer </a>
 */
public class Timer {
    private Date start;
    /**
     * Start timer.
     */
    public Timer() {
        reset();
    }
    /**
     * Returns exact number of milliseconds since timer was started.
     *
     * @return Number of milliseconds since timer was started.
     */
    public long getTime() {
        Date now = new Date();
        long millis = now.getTime() - start.getTime();
        return millis;
    }
    /**
     * Restarts the timer.
     */
    public void reset() {
        start = new Date(); // now
    }
    /**
     * Returns a formatted string showing the elaspsed time suince the instance
     * was created.
     *
     * @return Formatted time string.
     */
    public String toString( boolean mili ) {
        long millis = getTime();
        long hours = millis / 1000 / 60 / 60;
        millis -= hours * 1000 * 60 * 60;
        long minutes = millis / 1000 / 60;
        millis -= minutes * 1000 * 60;
        long seconds = millis / 1000;
        millis -= seconds * 1000;
        StringBuffer time = new StringBuffer();
        if( hours > 0 )
            time.append( hours + ":" );
        if( hours > 0 && minutes < 10 )
            time.append( "0" );
        time.append( minutes + ":" );
        if( seconds < 10 )
            time.append( "0" );
        time.append( seconds );
        if( mili )
        {
            time.append( "." );
            if( millis < 100 )
                time.append( "0" );
            if( millis < 10 )
                time.append( "0" );
            time.append( millis );
        }
        return time.toString();
    }
    @Override
    public String toString() {
        return toString( true );
    }
    /**
     * Testing this class.
     *
     * @param args
     * Not used.
     */
    @SuppressWarnings("unused")
    public static void main( String[] args ) {
        Timer timer = new Timer();
        for( int i = 0; i < 100000000; i++ ) {
            double b = 998.43678;
            double c = Math.sqrt( b );
        }
        System.out.println( timer );
    }
}
