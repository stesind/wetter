package de.sindzinski.wetter.util;

import android.util.Log;

/**
 * Created by steffen on 13.03.14.
 *
 * set logging on on adb shell using
 * adb shell setprop log.tag.LPICTrainer WARN
 *
 */
public class Logger {

    public static Boolean logging;

    // Das ist nur für Singleton, alternativ kann man static verwenden
    // Eine (versteckte) Klassenvariable vom Typ der eigenen Klasse
    private static Logger instance;
    // Verhindere die Erzeugung des Objektes über andere Methoden
    private Logger() {}
    // Eine Zugriffsmethode auf Klassenebene, welches dir '''einmal''' ein konkretes
    // Objekt erzeugt und dieses zurückliefert.
    public static Logger getInstance (String tag, Boolean logging) {
        if (Logger.instance == null) {
            Logger.instance = new Logger ();
            Logger.logging = logging;
        }
        return Logger.instance;
    }

    public static void v(String tag, String str) {
        if ((Log.isLoggable(tag, Log.VERBOSE)) | logging) {
            Log.v(tag, str);
        }
    }
    public static void v(String tag, String str, Throwable t) {
        if ((Log.isLoggable(tag, Log.VERBOSE)) | logging) {
            Log.v(tag, str, t);
        }
    }

    public static void i(String tag, String str) {
        if ((Log.isLoggable(tag, Log.INFO)) & logging) {
            Log.i(tag, str);
        }
    }
    public static void i(String tag, String str, Throwable t) {
        if ((Log.isLoggable(tag, Log.INFO)) & logging) {
            Log.i(tag, str, t);
        }
    }
    public static void w(String tag, String str) {
        if ((Log.isLoggable(tag, Log.WARN)) & logging) {
            Log.w(tag, str);
        }
    }
    public static void w(String tag, String str, Throwable t) {
        if ((Log.isLoggable(tag, Log.WARN)) & logging) {
            Log.w(tag, str, t);
        }
    }
    public static void d(String tag, String str) {
        if ((Log.isLoggable(tag, Log.DEBUG)) & logging) {
            Log.d(tag, str);
        }
    }
    public static void d(String tag, String str, Throwable t) {
        if ((Log.isLoggable(tag, Log.DEBUG)) & logging) {
            Log.d(tag, str, t);
        }
    }
    public static void e(String tag, String str) {
        if ((Log.isLoggable(tag, Log.ERROR)) & logging) {
            Log.e(tag, str);
        }
    }
    public static void e(String tag, String str, Throwable t) {
        if ((Log.isLoggable(tag, Log.ERROR)) & logging) {
            Log.e(tag, str, t);
        }
    }
}
