package duhblea.me.posturerepair;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


import java.util.concurrent.TimeUnit;

/**
 * Helper to save and retrieve values from Shared Preferences representing time spent in good
 * and bad position.  Messages are sent every 500 ms, so every tick represents have a second.
 *
 * @author Austin Alderton
 * @version 2 December 2016
 */
public class HistoryHelper {

    private long good = 0;
    private long bad = 0;

    private SharedPreferences prefs = null;


    protected HistoryHelper(Context context) {
        if (context != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);

            good = prefs.getLong("good", 0);
            bad = prefs.getLong("bad", 0);
        }
    }

    /**
     * Format the current count of time spend in good position and output to a human readable format
     *
     * @return Formatted string in days hours minutes seconds
     */
    private String getGoodTime() {
        long seconds = good / 1000l;

        int day = (int) TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24);
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

        return day + " days, " + hours + " hrs, " + minute
                + " min, " + second + " secs";
    }

    /**
     * Format the current count of time spend in bad position and output to a human readable format
     *
     * @return Formatted string in days hours minutes seconds
     */
    private String getBadTime() {
        long seconds = bad / 1000l;

        int day = (int) TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24);
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

        return day + " days, " + hours + " hrs, " + minute
                + " min, " + second + " secs";
    }

    /**
     * Save the times to Shared Prefs
     */
    protected void saveTimes() {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("good", good);
        editor.putLong("bad", bad);

        editor.commit();
    }

    /**
     * Tick the good counter
     */

    protected void incrementGoodTime() {
        good = good + 500;

    }

    /**
     * Tick the bad counter
     */
    protected void incrementBadTime() {
        bad = bad + 500;

    }


    /**
     * Formatted output stream to update History Text View in Main Activity
     *
     * @return
     */

    @Override
    public String toString() {
        return getGoodTime() + "\n\n\n\n" + getBadTime();
    }
}
