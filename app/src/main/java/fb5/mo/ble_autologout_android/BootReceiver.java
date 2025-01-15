package fb5.mo.ble_autologout_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)){

            // Steps
            // Check if a paired PC is saved
            // if one is saved start ble service

        }
    }
}