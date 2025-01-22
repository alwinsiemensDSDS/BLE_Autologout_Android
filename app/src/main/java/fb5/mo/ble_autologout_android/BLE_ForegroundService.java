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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.Collections;
import java.util.List;

import no.nordicsemi.android.ble.callback.FailCallback;
import no.nordicsemi.android.ble.callback.SuccessCallback;
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
    boolean stopping = false;

    public enum Actions {
        StartService,
        StopService,
        StartScanning,
    }

    public enum ConnectionStatus {
        Connected,
        Disconnected,
        Searching,
        Error
    }

    public boolean isScanning(){
        return scanning;
    }

    public void setScanningFlag(boolean isScanning){
        scanning = isScanning;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void SendConnectionStatusChangedToActivity(ConnectionStatus conn_status, Context context){
        Intent broadcastIntent = new Intent("ConnectionStatusChanged");
        broadcastIntent.putExtra("ConnectionStatus", conn_status.name());
        context.sendBroadcast(broadcastIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        scanner = BluetoothLeScannerCompat.getScanner();

        scanCallback = new ScanCallback() {

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                SendConnectionStatusChangedToActivity(ConnectionStatus.Error, getApplicationContext());
                StartScanMode();
            }

            @Override
            public void onScanResult(int callbackType, @NonNull ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (getSavedPairedPcAdress().equals(device.getAddress())){

                    // only if it was in scan mode. It may be fire twice because stopScan is also callbacking.
                    if (isScanning()){
                        scanner.stopScan(this);
                        setScanningFlag(false);

                        bleManagerDistance = new BLE_Manager(getApplicationContext());

                            bleManagerDistance.connect(device)
                                    .useAutoConnect(false)
                                    .done(new SuccessCallback() {
                                        @Override
                                        public void onRequestCompleted(@NonNull BluetoothDevice device) {
                                            SendConnectionStatusChangedToActivity(ConnectionStatus.Connected, getApplicationContext());
                                            bleManagerDistance.writeRSSIStrength(getApplicationContext());
                                        }
                                    })
                                    .fail(new FailCallback() {
                                        @Override
                                        public void onRequestFailed(@NonNull BluetoothDevice device, int status) {
                                            SendConnectionStatusChangedToActivity(ConnectionStatus.Error, getApplicationContext());
                                            StartScanMode();
                                        }
                                    })
                                    .enqueue();
                    }
                }
            }
        };
    }

    public void StopBLEService(){
        stopping = true;
        if (isScanning()){
            setScanningFlag(false);
            scanner.stopScan(scanCallback);
        }

        if (bleManagerDistance != null && bleManagerDistance.isConnected()){
            bleManagerDistance.isStopping = true;
            bleManagerDistance.disconnect()
                    .done(new SuccessCallback() {
                        @Override
                        public void onRequestCompleted(@NonNull BluetoothDevice device) {
                            bleManagerDistance.onServicesInvalidated();
                            bleManagerDistance.close();
                            bleManagerDistance = null;
                            SendConnectionStatusChangedToActivity(ConnectionStatus.Disconnected, getApplicationContext());
                            stopSelf();

                        }
                    })
                    .fail(new FailCallback() {
                        @Override
                        public void onRequestFailed(@NonNull BluetoothDevice device, int status) {
                            SendConnectionStatusChangedToActivity(ConnectionStatus.Error, getApplicationContext());
                        }
                    })
                    .enqueue();
        }
        else {
            bleManagerDistance = null;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START SERVICE
        if (Actions.StartService.toString().equals(intent.getAction())){
            startForegroundNotification();
            StartScanMode();
        }
        // STOP SERVICE
        else if (Actions.StopService.toString().equals(intent.getAction())){
            StopBLEService();
        }
        // START SCAN MODE
        else if (Actions.StartScanning.toString().equals(intent.getAction())){
            StartScanMode();
        };
        return START_NOT_STICKY;
    }

    public void StartScanMode(){
        if (bleManagerDistance != null && bleManagerDistance.isConnected()){
                bleManagerDistance.disconnect()
                        .done(new SuccessCallback() {
                            @Override
                            public void onRequestCompleted(@NonNull BluetoothDevice device) {
                                bleManagerDistance.close();
                                bleManagerDistance = null;
                                SendConnectionStatusChangedToActivity(ConnectionStatus.Disconnected, getApplicationContext());

                                startLowPowerScanning();
                            }
                        })
                        .enqueue();
        }
        else{
            startLowPowerScanning();
        }


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
        if (isScanning()){
            return;
        }
        setScanningFlag(true);

        // Filter on a Paired Device
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(getSavedPairedPcAdress())
                .build();
        List<ScanFilter> filters = Collections.singletonList(filter);
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        scanner.startScan(filters, settings, scanCallback);

        SendConnectionStatusChangedToActivity(ConnectionStatus.Searching, getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!stopping){
            StopBLEService();
        }
    }

}