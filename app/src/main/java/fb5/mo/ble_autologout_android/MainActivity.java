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
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import java.util.UUID;
import java.util.concurrent.Executor;

// Todo: make SharedPrefs Usage simpler
// Todo: Settings Page and About Page missing

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // ------------------------------- VIEW ELEMENTS --------------------------------------------
    TextView statusTextView;
    TextView rangeDbTextView;

    ImageView notConnectedImageView;

    ProgressBar searchIndicatorProgressbar;

    Button pairingButton;
    Button stopServiceButton;
    Button savedPCConnectButton;

    DrawerLayout drawerLayout;
    ConstraintLayout circularBackground;

    BroadcastReceiver ConnectionStatusReceiver;
    BroadcastReceiver RemoteRSSIReceiver;

    final static int REQUEST_POST_NOTIFICATIONS = 15;
    final static int REQUEST_BLE_FOREGROUND_PERMISSIONS = 16;
    final static int REQUEST_BLUETOOTH_CONNECT = 17;
    final static int CLICKED_COMPANION_BLUETOOTH_DEVICE = 18;


    private void initViewElements(){
        statusTextView = findViewById(R.id.statusTextView);
        rangeDbTextView = findViewById(R.id.rangeDbTextView);
        notConnectedImageView = findViewById(R.id.not_connected_image_view);
        searchIndicatorProgressbar = findViewById(R.id.searchIndicatorProgressbar);
        pairingButton = findViewById(R.id.pairingBtn);
        savedPCConnectButton = findViewById(R.id.connectToSavedPcButton);
        stopServiceButton = findViewById(R.id.stopServiceBtn);
        notConnectedImageView = findViewById(R.id.not_connected_image_view);
        circularBackground = findViewById(R.id.circularBackground);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);

        // Toolbar Init
        setSupportActionBar(myToolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, myToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // NavigationView Init
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Circular Range Indicator Init
        circularBackground.setBackground(AppCompatResources.getDrawable(this, R.drawable.circle_background_grey));
        notConnectedImageView.setVisibility(View.VISIBLE);
        rangeDbTextView.setVisibility(View.INVISIBLE);
        searchIndicatorProgressbar.setVisibility(View.INVISIBLE);

        // Statustext init
        statusTextView.setBackground(AppCompatResources.getDrawable(this, R.drawable.rounded_badge_not_connected));

        // Buttons Init
        pairingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CompanionPairingProcess();
            }
        });

        stopServiceButton.setVisibility(View.INVISIBLE);
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StopBLEForegroundService();

                stopServiceButton.setVisibility(View.INVISIBLE);
                pairingButton.setVisibility(View.VISIBLE);
                savedPCConnectButton.setVisibility(View.VISIBLE);

                circularBackground.setBackground(AppCompatResources.getDrawable(view.getContext(), R.drawable.circle_background_grey));
                notConnectedImageView.setVisibility(View.VISIBLE);
                rangeDbTextView.setVisibility(View.INVISIBLE);

                statusTextView.setBackground(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_badge_not_connected));
                statusTextView.setText("not connected");
            }
        });

        savedPCConnectButton.setVisibility(View.INVISIBLE);
        savedPCConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    StartBLEForegroundService();
            }
        });

        Intent serviceIntent = new Intent(this, BLE_ForegroundService.class);
        serviceIntent.setAction(BLE_ForegroundService.Actions.StartSilentService.toString());
        ContextCompat.startForegroundService(this, serviceIntent);
    }


    // ------------------------------- LIFE-CYCLE METHODEN ---------------------------------------
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

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

        if(!checkPermissions()){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_BLE_FOREGROUND_PERMISSIONS);
            Toast.makeText(this, "Please restart the App please.", Toast.LENGTH_LONG);
        }else {
                autostart_service();
        }
    }

    public boolean isAutoModeSet(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean automode_is_set  = sharedPreferences.getBoolean("service_auto_mode", false);
        return automode_is_set;
    }

    public void autostart_service(){
        // Auto-Start of BLE Service
        if (isOnePairedPcSaved()){
            savedPCConnectButton.setVisibility(View.VISIBLE);

            SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences("AUTOLOGOUT_APP", Context.MODE_PRIVATE);
            String SavedPCName = sharedPrefs.getString("SAVED_PC_NAME", null);

            if(SavedPCName != null){
                savedPCConnectButton.setText("connect with: " + SavedPCName);
            }
            if (!isServiceRunningInForeground(this, BLE_ForegroundService.class)) {
                if (isAutoModeSet()){
                    StartBLEForegroundService();
                }

            }

        }
    }

    public boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(ConnectionStatusReceiver);
            unregisterReceiver(RemoteRSSIReceiver);
        }
        catch (IllegalArgumentException e){
            // receiver were not registered
        }
        StopBLEForegroundService();
    }

    // ------------------------------------------------------------------------------------

    public boolean isOnePairedPcSaved(){
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences("AUTOLOGOUT_APP", Context.MODE_PRIVATE);
        String SavedMacAdress = sharedPrefs.getString("SAVED_PC_MACADRESS", null);
        return SavedMacAdress != null;
    }

    //Todo: nochmal genauer schauen, wie das mit den Messages ist die du vom Service bekommst
    public void StartBLEForegroundService(){

            stopServiceButton.setVisibility(View.VISIBLE);
            pairingButton.setVisibility(View.INVISIBLE);
            savedPCConnectButton.setVisibility(View.INVISIBLE);

            Intent serviceIntent = new Intent(this, BLE_ForegroundService.class);
            serviceIntent.setAction(BLE_ForegroundService.Actions.StartService.toString());
            ContextCompat.startForegroundService(this, serviceIntent);

            ConnectionStatusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    String message = intent.getStringExtra("ConnectionStatus");
                    switch (message){
                        case "Searching":
                            statusTextView.setBackground(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_badge_not_connected));
                            searchIndicatorProgressbar.setVisibility(View.VISIBLE);
                            circularBackground.setBackground(AppCompatResources.getDrawable(context, R.drawable.circle_background_grey));
                            notConnectedImageView.setVisibility(View.VISIBLE);
                            rangeDbTextView.setVisibility(View.INVISIBLE);
                            break;
                        case "Disconnected":
                            statusTextView.setBackground(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_badge_not_connected));
                            searchIndicatorProgressbar.setVisibility(View.INVISIBLE);
                            circularBackground.setBackground(AppCompatResources.getDrawable(context, R.drawable.circle_background_grey));
                            notConnectedImageView.setVisibility(View.VISIBLE);
                            rangeDbTextView.setVisibility(View.INVISIBLE);
                            break;
                        case "Connected":
                            statusTextView.setBackground(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_badge_connected));
                            searchIndicatorProgressbar.setVisibility(View.INVISIBLE);
                            circularBackground.setBackground(AppCompatResources.getDrawable(context, R.drawable.circle_background_blue));
                            notConnectedImageView.setVisibility(View.INVISIBLE);
                            rangeDbTextView.setVisibility(View.VISIBLE);
                    }
                    statusTextView.setText(message);
                }
            };
            registerReceiver(ConnectionStatusReceiver, new IntentFilter("ConnectionStatusChanged"));

            RemoteRSSIReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

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

    public void StopBLEForegroundService(){
    {
        Intent serviceIntent = new Intent(this, BLE_ForegroundService.class);
        serviceIntent.setAction(BLE_ForegroundService.Actions.StopService.toString());
        ContextCompat.startForegroundService(this, serviceIntent);

        stopServiceButton.setVisibility(View.INVISIBLE);
        pairingButton.setVisibility(View.VISIBLE);
        savedPCConnectButton.setVisibility(View.VISIBLE);
        searchIndicatorProgressbar.setVisibility(View.INVISIBLE);
    }

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


    // ------------------------------- BLUETOOTH PAIRING PROCESS -----------------------------------------------

    public void CompanionPairingProcess(){
        // The Companion Device Manager helps to find only Devices that match a filter.
        ParcelUuid DistanceServiceUUID = ParcelUuid.fromString( BLE_Manager.DISTANCE_SERVICE_UUID);
        // Filters only for devices with having the distance service
        BluetoothLeDeviceFilter deviceFilter = new BluetoothLeDeviceFilter.Builder()
                .setScanFilter(new ScanFilter.Builder().setServiceUuid(DistanceServiceUUID).build())
                .build();

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .build();

        CompanionDeviceManager deviceManager = (CompanionDeviceManager)getSystemService(COMPANION_DEVICE_SERVICE);

        deviceManager.associate(
                pairingRequest,
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
                                    chooserLauncher, CLICKED_COMPANION_BLUETOOTH_DEVICE/*SELECT DEVICE REQUEST CODE*/, null, 0, 0, 0
                            );
                        } catch (IntentSender.SendIntentException e) {
                            //
                        }
                    }

                    @Override
                    public void onAssociationCreated(AssociationInfo associationInfo) {
                        // retrieve mac address
                        int associationId = associationInfo.getId();
                        MacAddress macAddress = associationInfo.getDeviceMacAddress();
                        assert macAddress != null;

                        // save in SharedPrefs
                        Context context = getApplicationContext();
                        SharedPreferences sharedPrefs = context.getSharedPreferences("AUTOLOGOUT_APP",Context.MODE_PRIVATE);
                        sharedPrefs.edit().putString("SAVED_PC_MACADRESS", macAddress.toString().toUpperCase()).apply();
                        sharedPrefs.edit().putString("SAVED_PC_NAME", associationInfo.getDisplayName().toString().toUpperCase()).apply();

                        savedPCConnectButton.setVisibility(View.VISIBLE);
                        savedPCConnectButton.setText("connect with: " + associationInfo.getDisplayName());
                    }

                    @Override
                    public void onFailure(CharSequence charSequence) {

                    }
                });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        final int navSettings = R.id.nav_settings; //1000000
        final int navAbout = R.id.nav_about; //1000006

        switch (item.getTitle().toString()) {
            case "Settings":
                // Handle "Settings" action
                Intent settings_intent = new Intent(this,  SettingsActivity.class);
                startActivity(settings_intent);
                break;
            case "About":
                // Handle "About" action
                Intent about_intent = new Intent(this,  AboutActivity.class);
                startActivity(about_intent);
                break;
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CLICKED_COMPANION_BLUETOOTH_DEVICE && data != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Denied.", Toast.LENGTH_LONG);
                return;
            }
            ScanResult scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);

            BluetoothDevice device = scanResult.getDevice();
            // save in SharedPrefs
            Context context = getApplicationContext();
            SharedPreferences sharedPrefs = context.getSharedPreferences("AUTOLOGOUT_APP",Context.MODE_PRIVATE);
            sharedPrefs.edit().putString("SAVED_PC_MACADRESS", device.getAddress().toString().toUpperCase()).apply();
            sharedPrefs.edit().putString("SAVED_PC_NAME", device.getName().toString().toUpperCase()).apply();

            savedPCConnectButton.setVisibility(View.VISIBLE);

            savedPCConnectButton.setText("connect with: " + device.getName());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        if (resultCode != Activity.RESULT_OK) {
//            return;
//        }
//        if (requestCode == 0 /*SELECT_DEVICE_REQUEST_CODE*/ && data != null) {
//            ScanResult scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
//            if (scanResult != null) {
//                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                    ActivityCompat.requestPermissions(
//                            this,
//                            new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
//                            1
//                    );
//                }
//
////                BluetoothDevice device = scanResult.getDevice();
////                if (device.getBondState() == BluetoothDevice.BOND_NONE){
////                    device.createBond();
////                }
////                // Continue to interact with the paired device.
////                boolean connected =  bleService.connect(device.getAddress());
////                if (connected){
////                    statusTextView.setText("connected");
////                }
//
//            }
//        } else {
//            super.onActivityResult(requestCode, resultCode, data);
//        }
//    }