package fb5.mo.ble_autologout_android;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {


    //

    // Stat

    // ------------------------------- VIEW ELEMENTS --------------------------------------------
    TextView statusTextView;
    Button pairingButton;
    Button stopConnectionBtn;
    TextView rangeDbTextView;
    Button stopServiceButton;

    BroadcastReceiver ConnectionStatusReceiver;
    BroadcastReceiver RemoteRSSIReceiver;

    private void initViewElements(){
        pairingButton = findViewById(R.id.pairingBtn);
        statusTextView = findViewById(R.id.statusTextView);
        stopConnectionBtn = findViewById(R.id.stopBtn);
        rangeDbTextView = findViewById(R.id.rangeDbTextView);
        stopServiceButton = findViewById(R.id.stopServiceBtn);

        pairingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Stop it, because we want to renew our Paired PC connection
                StopBLEForegroundService();
                CompanionPairingProcess();
            }
        });

        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StopBLEForegroundService();
            }
        });
    }

    // ------------------------------- LIFE-CYCLE METHODEN ---------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViewElements();

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH_SCAN},
                1);


        // Broadcast Receivers for getting Events from Foreground BLE Service

        ConnectionStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get extra data included in the Intent
                String message = intent.getStringExtra("ConnectionStatus");
                statusTextView.setText(message);
                // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        };
        registerReceiver(ConnectionStatusReceiver, new IntentFilter("ConnectionStatusChanged"));

        RemoteRSSIReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get extra data included in the Intent
                int RSSI = intent.getIntExtra("RemoteRSSI", 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rangeDbTextView.setText(""+RSSI);
                    }
                });
            }
        };
        registerReceiver(RemoteRSSIReceiver, new IntentFilter("RemoteRSSIChanged"));


        if (isOnePairedPcSaved()){
            // Start BLE Service when it is not already running
            StartBLEForegroundService();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(ConnectionStatusReceiver);
        unregisterReceiver(RemoteRSSIReceiver);
    }

    public boolean isOnePairedPcSaved(){
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences("AUTOLOGOUT_APP", Context.MODE_PRIVATE);
        String SavedMacAdress = sharedPrefs.getString("SAVED_PC_MACADRESS", null);
        return SavedMacAdress != null;
    }

    public void StartBLEForegroundService(){
        if (!isServiceRunningInForeground(this, BLE_ForegroundService.class)){
            Intent serviceIntent = new Intent(this, BLE_ForegroundService.class);
            serviceIntent.setAction(BLE_ForegroundService.Actions.Start.toString());
            ContextCompat.startForegroundService(this, serviceIntent);

            ConnectionStatusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // Get extra data included in the Intent
                    String message = intent.getStringExtra("ConnectionStatus");
                    statusTextView.setText(message);
                    // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            };
            registerReceiver(ConnectionStatusReceiver, new IntentFilter("ConnectionStatusChanged"));

            RemoteRSSIReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // Get extra data included in the Intent
                    int RSSI = intent.getIntExtra("RemoteRSSI", 0);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            rangeDbTextView.setText(""+RSSI);
                        }
                    });
                }
            };
            registerReceiver(RemoteRSSIReceiver, new IntentFilter("RemoteRSSIChanged"));
        }
    }

    public void StopBLEForegroundService(){
        Intent serviceIntent = new Intent(this, BLE_ForegroundService.class);
        serviceIntent.setAction(BLE_ForegroundService.Actions.Stop.toString());
        ContextCompat.startForegroundService(this, serviceIntent);
        unregisterReceiver(ConnectionStatusReceiver);
        unregisterReceiver(RemoteRSSIReceiver);
    }

    public static boolean isServiceRunningInForeground(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }

            }
        }
        return false;
    }

    // ------------------------------- Permissions -----------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //Handle Permission Selection
    }

    // ------------------------------- BLUETOOTH PAIRING PROCESS -----------------------------------------------

    //    private BLEService bleService;
    private static final String DISTANCE_SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";

    private static final int SELECT_DEVICE_REQUEST_CODE = 0;


//    // after bindService() in OnStart()
//    private ServiceConnection serviceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            bleService = ((BLEService.LocalBinder) service).getService();
//            if (bleService != null) {
//                // call functions on service to check connection and connect to devices
//                bleService.initialize();
//            }
//
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            bleService = null;
//        }
//    };



    public void CompanionPairingProcess(){
        // The Companion Device Manager helps to find only Devices that match a filter.

        // Devicefiltering for DISTANCE SERVICE
        BluetoothLeDeviceFilter deviceFilter = new BluetoothLeDeviceFilter.Builder()
                .setScanFilter(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(DISTANCE_SERVICE_UUID)
                ).build())
                .build();

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .build();

        CompanionDeviceManager deviceManager = (CompanionDeviceManager)getSystemService(COMPANION_DEVICE_SERVICE);

        deviceManager.associate(pairingRequest,
                new Executor() {
                    @Override
                    public void execute(Runnable runnable) {
                        runnable.run();
                    }
                },
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        // Opens a Picklist with the DeviceFilter for Bluetooth Low Energy Devices
                        try {
                            startIntentSenderForResult(
                                    chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                            );
                        } catch (IntentSender.SendIntentException e) {
                            //
                        }
                    }

                    @Override
                    public void onAssociationCreated(AssociationInfo associationInfo) {

                        int associationId = associationInfo.getId();
                        MacAddress macAddress = associationInfo.getDeviceMacAddress();
                        assert macAddress != null;

                        // save in SharedPrefs
                        Context context = getApplicationContext();
                        SharedPreferences sharedPrefs = context.getSharedPreferences("AUTOLOGOUT_APP",Context.MODE_PRIVATE);
                        sharedPrefs.edit().putString("SAVED_PC_MACADRESS", macAddress.toString().toUpperCase()).apply();

                    }

                    @Override
                    public void onFailure(CharSequence charSequence) {

                    }
                });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == SELECT_DEVICE_REQUEST_CODE && data != null) {
            ScanResult scanResult =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
            if (scanResult != null) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                            1
                    );
                }

//                BluetoothDevice device = scanResult.getDevice();
//                if (device.getBondState() == BluetoothDevice.BOND_NONE){
//                    device.createBond();
//                }
//                // Continue to interact with the paired device.
//                boolean connected =  bleService.connect(device.getAddress());
//                if (connected){
//                    statusTextView.setText("connected");
//                }

            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}