package fb5.mo.ble_autologout_android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.Collections;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class BLE_ForegroundService extends Service {
    private BluetoothLeScannerCompat scanner;
    private ScanCallback scanCallback;
    private BLE_Manager bleManagerDistance;

    boolean scanning = false;

    public enum Actions {
        Start,
        Stop,
        StartLowPowerScanningMode
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        scanner = BluetoothLeScannerCompat.getScanner();

        scanCallback = new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, @NonNull ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (getSavedPairedPcAdress().equals(device.getAddress())){

                    if (scanning){
                        // callbacks return to this method after stopping complete
                        scanner.stopScan(this);
                        scanning = false;

                        bleManagerDistance = new BLE_Manager(getApplicationContext());
                        bleManagerDistance.connect(device)
                                .useAutoConnect(false)
                                .done(deviceConnected -> {
                                    bleManagerDistance.writeRSSIStrength(getApplicationContext());
                                })
                                .enqueue();

                        Intent broadcastIntent = new Intent("ConnectionStatusChanged");
                        broadcastIntent.putExtra("ConnectionStatus", "connected");
                        sendBroadcast(broadcastIntent);
                    }
                }
            }
        };
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Actions.Start.toString().equals(intent.getAction())){
            startForegroundNotification();
            startLowPowerScanning();
        }
        else if (Actions.Stop.toString().equals(intent.getAction())){

            try{
                if (scanning){
                    scanner.stopScan(scanCallback);
                }
                bleManagerDistance.disconnect();
                bleManagerDistance = null;
            }catch (Exception e){

            }
            Intent broadcastIntent = new Intent("ConnectionStatusChanged");
            broadcastIntent.putExtra("ConnectionStatus", "disconnected");
            sendBroadcast(broadcastIntent);
            stopSelf();
        }
        else if (Actions.StartLowPowerScanningMode.toString().equals(intent.getAction())){
            if (bleManagerDistance != null){
                bleManagerDistance.disconnect();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Intent broadcastIntent = new Intent("ConnectionStatusChanged");
            broadcastIntent.putExtra("ConnectionStatus", "disconnected");
            sendBroadcast(broadcastIntent);

            startLowPowerScanning();

        }

        return START_STICKY;
    }

    private void startForegroundNotification() {
        NotificationChannel channel = new NotificationChannel("BLE_AUTOLOGOUT_SERVICE", "BLEAutologoutChannel", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("BLE Autologout Service");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Notification notification =
                new NotificationCompat.Builder(this, "BLE_AUTOLOGOUT_SERVICE")
                        .build();
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
        ServiceCompat.startForeground(this,100, notification, type);
    }


    private String getSavedPairedPcAdress(){
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences("AUTOLOGOUT_APP", Context.MODE_PRIVATE);
        String SavedMacAdress = sharedPrefs.getString("SAVED_PC_MACADRESS", null);
        return SavedMacAdress;
    }

    private void startLowPowerScanning() {
        if (scanning){
            return;
        }
        scanning = true;
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(getSavedPairedPcAdress())
                .build();
        List<ScanFilter> filters = Collections.singletonList(filter);
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        scanner.startScan(filters, settings, scanCallback);

        Intent broadcastIntent = new Intent("ConnectionStatusChanged");
        broadcastIntent.putExtra("ConnectionStatus", "searching...");
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        scanner.stopScan(scanCallback);
    }

}