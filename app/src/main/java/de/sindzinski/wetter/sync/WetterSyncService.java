package de.sindzinski.wetter.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class WetterSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static WetterSyncAdapter sWetterSyncAdapter = null;

    @Override
    public void onCreate() {
        Log.d("WetterSyncService", "onCreate - WetterSyncService");
        synchronized (sSyncAdapterLock) {
            if (sWetterSyncAdapter == null) {
                sWetterSyncAdapter = new WetterSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sWetterSyncAdapter.getSyncAdapterBinder();
    }
}