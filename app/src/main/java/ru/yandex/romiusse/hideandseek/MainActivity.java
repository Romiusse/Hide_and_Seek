package ru.yandex.romiusse.hideandseek;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    public static boolean ISRUNNING = true;
    FreqMic freqMic;
    private String playerType = "HIDE";
    private String hideType = "QUITE";
    private String playerNum = "1";
    private int timerNum = 0;
    private Drawable backgroundHideColor;
    int[] distances = new int[5];
    private BluetoothLeScanner mBluetoothLeScanner;
    BluetoothLeAdvertiser advertiser;
    public final int AUDIO_RECORD_CODE = 1012;
    RadarView mRadarView;
    private Handler mHandler = new Handler();

    double getDistance(int rssi, int txPower) {

        return Math.pow(10d, ((double) txPower - rssi) / (10 * 2));
    }

    int[] poses = new int[6];
    final int WINDOW_SIZE = 25;
    long[] lastTimeId = new long[5];
    double[][] windows = new double[6][WINDOW_SIZE];
    private ScanCallback mScanCallback = new ScanCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //Log.e("BLE", result.toString());
            if (result == null)
                return;

            try {
                Log.println(Log.ERROR, "LOG", new String(result.getScanRecord().getServiceData(new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))), StandardCharsets.UTF_8));
                String sid = new String(result.getScanRecord().getServiceData(new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))), StandardCharsets.UTF_8);
                int id = 0;
                if (sid.equals("HAS:0")) id = 0;
                else if (sid.equals("HAS:1")) id = 1;
                else if (sid.equals("HAS:2")) id = 2;
                else if (sid.equals("HAS:3")) id = 3;
                else if (sid.equals("HAS:4")) id = 4;
                else if (sid.equals("HAS:5")) id = 5;

                if (poses[id] < WINDOW_SIZE) {
                    windows[id][poses[id]] = result.getRssi();
                    poses[id]++;
                } else {
                    for (int i = 0; i < WINDOW_SIZE - 1; i++) {
                        windows[id][i] = windows[id][i + 1];
                    }
                    windows[id][WINDOW_SIZE - 1] = result.getRssi();
                }
                double avrSum = 0;
                for (int i = 0; i < poses[id]; i++) {
                    avrSum += windows[id][i];
                }
                double distance = avrSum / poses[id];
                distance = getDistance((int) distance, -60);

                if (playerType.equals("HIDE")) {
                    TextView infoText = findViewById(R.id.infoText);
                    LinearLayout background = findViewById(R.id.hideBack);
                    if (distance < 1) {
                        background.setBackgroundColor(Color.parseColor("#A9930000"));
                        infoText.setText("Искатель близко");
                    } else {
                        background.setBackground(backgroundHideColor);
                        infoText.setText("Сидите тихо");
                    }
                } else {
                    if (distance < 1) {
                        distances[id - 1] = (int) (80 * distance);
                    } else if (distance < 3) {
                        distances[id - 1] = (int) (60 * distance);
                    } else {
                        distances[id - 1] = (int) (40 * distance);
                    }
                    long timeNow = System.currentTimeMillis();
                    if (id > 0) {
                        lastTimeId[id - 1] = timeNow;
                    }
                }


            } catch (Exception ignored) {
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE", "Discovery onScanFailed: " + errorCode);
            super.onScanFailed(errorCode);
        }
    };

    private void initMain() {
        hide = findViewById(R.id.startHide);
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerType = "HIDE";
                ISRUNNING = true;
                setContentView(R.layout.choose_number_layout);
                initChooseLayout();
            }
        });

        seek = findViewById(R.id.startSeek);
        seek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerType = "SEEK";
                ISRUNNING = true;
                setContentView(R.layout.seek_timer_settings_layout);
                initTimerPicker();
            }
        });

        perm = findViewById(R.id.perm);
        perm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkAndRequestPermissions()) {
                    perm.setVisibility(View.GONE);
                    hide.setEnabled(true);
                    seek.setEnabled(true);
                }
            }
        });

        if (!checkAndRequestPermissions()) {
            hide.setEnabled(false);
            seek.setEnabled(false);
        } else {
            perm.setVisibility(View.GONE);
        }
    }

    private boolean checkAndRequestPermissions() {

        int locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        List<String> listPermissionsNeeded = new ArrayList<>();

        int audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int bluetoothScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
            if (bluetoothScan != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int blconnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
            if (blconnect != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int advertisePermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_ADVERTISE);
            if (advertisePermission != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        }

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (audioPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 23);
            return false;
        }
        return true;
    }

    private boolean checkPermissions() {

        int locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        List<String> listPermissionsNeeded = new ArrayList<>();

        int audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int advertisePermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_ADVERTISE);
            if (advertisePermission != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int blconnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
            if (blconnect != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int bluetoothScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
            if (bluetoothScan != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (audioPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        return listPermissionsNeeded.isEmpty();
    }


    Button hide, seek, perm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        hide = findViewById(R.id.startHide);
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerType = "HIDE";
                ISRUNNING = true;
                setContentView(R.layout.choose_number_layout);
                initChooseLayout();
            }
        });

        seek = findViewById(R.id.startSeek);
        seek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerType = "SEEK";
                ISRUNNING = true;
                setContentView(R.layout.seek_timer_settings_layout);
                initTimerPicker();
            }
        });

        perm = findViewById(R.id.perm);
        perm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkAndRequestPermissions()) {
                    perm.setVisibility(View.GONE);
                    hide.setEnabled(true);
                    seek.setEnabled(true);
                }
            }
        });

        mBluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();


        if (!checkAndRequestPermissions()) {
            hide.setEnabled(false);
            seek.setEnabled(false);
        } else {
            perm.setVisibility(View.GONE);
        }
    }

    public boolean setBluetooth(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        if (enable && !isEnabled) {
            return bluetoothAdapter.enable();
        }
        else if(!enable && isEnabled) {
            return bluetoothAdapter.disable();
        }
        // No need to change bluetooth state
        return true;
    }


    private class UpdateRadar extends AsyncTask<Void, int[], Void> {

        long lastTimeUpdate = 0;

        @Override
        protected Void doInBackground(Void... voids) {

            while (ISRUNNING) {
                long timeNow = System.currentTimeMillis();
                for (int i = 0; i < 5; i++) {
                    if (timeNow - lastTimeId[i] > 3000) {
                        distances[i] = -1;
                    }
                }
                int[] playerDistance = freqMic.getpD();
                int[] newDist = new int[5];
                for (int i = 0; i < 5; i++) {
                    if (distances[i] > 350 || distances[i] <= 0) newDist[i] = playerDistance[i];
                    else newDist[i] = distances[i];
                }
                //Log.println(Log.ERROR, "LOG",Arrays.toString(newDist) );
                mRadarView.setPlayerDistance(newDist);

                if (System.currentTimeMillis() - lastTimeUpdate > 300) {
                    publishProgress(playerDistance);
                    lastTimeUpdate = System.currentTimeMillis();
                }
            }
            return null;

        }

        @Override
        protected void onProgressUpdate(int[]... values) {
            //textView.setText(Arrays.toString(values[0]));
        }
    }

    private void initSeekerLayout() {
        setBluetooth(true);
        Button button = findViewById(R.id.exitButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ISRUNNING = false;
                setContentView(R.layout.activity_main);
                initMain();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                advertiser.stopAdvertising(advertisingCallback);
            }
        });
        mRadarView = (RadarView) findViewById(R.id.radarView);
        mRadarView.setShowCircles(true);
        mRadarView.startAnimation();
        freqMic = new FreqMic();
        if(checkPermission(Manifest.permission.RECORD_AUDIO, AUDIO_RECORD_CODE)){
            freqMic.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        new UpdateRadar().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        checkPermission(Manifest.permission.BLUETOOTH, 2);
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN, 3);
        discover();
        if (BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
            advertise("HAS:0");
        }
    }

    public boolean checkPermission(String permission, int requestCode)
    {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(checkPermissions()){
            hide.setEnabled(true);
            seek.setEnabled(true);
            perm.setVisibility(View.GONE);
        }
    }

    private class Timer extends AsyncTask<Void, Void, Void>{

        long lastTime = 0;
        int cnt = 0;
        @Override
        protected Void doInBackground(Void... voids) {
            while(true) {
                if (System.currentTimeMillis() - lastTime > 1000) {
                    cnt++;
                    lastTime = System.currentTimeMillis();
                    publishProgress();
                    if(cnt > timerNum) break;
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            TextView textView = findViewById(R.id.seekerTime);
            textView.setText(Integer.toString(timerNum - cnt));
        }

        @Override
        protected void onPostExecute(Void unused) {
            setContentView(R.layout.seek_layout);
            initSeekerLayout();
        }
    }

    private void initSeekerTimerLayout() {
        setBluetooth(true);
        TextView textView = findViewById(R.id.seekerTime);
        textView.setText(Integer.toString(timerNum));
        new Timer().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class PlaySound extends AsyncTask<Void, Void, Void> {
        int lastplayer = -1;
        MediaPlayer makeSound = MediaPlayer.create(MainActivity.this, R.raw.chirik);

        @Override
        protected Void doInBackground(Void... voids) {
            while (ISRUNNING) {


                int timeStamp = Integer.parseInt(new SimpleDateFormat("ss").format(Calendar.getInstance().getTime()));
                timeStamp %= 10;

                int player = timeStamp / 2;
                if (player == (Integer.parseInt(playerNum) - 1) && timeStamp == (Integer.parseInt(playerNum) - 1) * 2 + 1) {
                    makeSound.start();
                    System.out.println("Player: " + (player + 1));
                    lastplayer = player;
                }
            }
            return null;
        }
    }

    private void initHideLayout() {
        setBluetooth(true);
        LinearLayout background = findViewById(R.id.hideBack);
        backgroundHideColor = background.getBackground();
        TextView stateText = findViewById(R.id.stateText);
        if (hideType.equals("QUITE"))
            stateText.setText("Ваш режим: тихий\nВаш номер: " + playerNum);
        else
            stateText.setText("Ваш режим: шумный\nВаш номер: " + playerNum);

        Button loose = findViewById(R.id.looseButton);
        loose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ISRUNNING = false;
                setContentView(R.layout.activity_main);
                initMain();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                advertiser.stopAdvertising(advertisingCallback);
            }
        });
        if (hideType.equals("QUITE")) {
            advertise("HAS:" + playerNum);
        } else {
            new PlaySound().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        discover();
    }

    private void initUCanHideLayout() {
        setBluetooth(true);
        Button button = findViewById(R.id.uCanHideButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.hide_layout);
                initHideLayout();
            }
        });
    }

    private void initWaitLayout() {
        setBluetooth(true);
        Button button = findViewById(R.id.waitButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playerType.equals("HIDE")) {
                    setContentView(R.layout.u_can_hide_layout);
                    initUCanHideLayout();
                } else {
                    setContentView(R.layout.seeker_timer_layout);
                    initSeekerTimerLayout();
                }
            }
        });
    }

    private void initTypeLayout() {
        setBluetooth(true);
        Button noice = findViewById(R.id.typePickerNoice);
        noice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideType = "NOICE";
                setContentView(R.layout.wait_layout);
                initWaitLayout();

            }
        });

        Button quite = findViewById(R.id.typePickerQuite);
        if (!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
            quite.setEnabled(false);
        }
        quite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideType = "QUITE";
                setContentView(R.layout.wait_layout);
                initWaitLayout();
            }
        });
    }

    private void initTimerPicker(){
        NumberPicker numberPicker = findViewById(R.id.timerPicker);
        numberPicker.setMaxValue(120);
        numberPicker.setMinValue(0);
        numberPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        Button button = findViewById(R.id.timerNextButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timerNum = numberPicker.getValue();
                setContentView(R.layout.wait_layout);
                initWaitLayout();
            }
        });
    }

    private void initChooseLayout() {
        setBluetooth(true);
        final int[] nextStep = {0};
        NumberPicker numberPicker = findViewById(R.id.numberPicker);
        numberPicker.setMaxValue(5);
        numberPicker.setMinValue(1);
        numberPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        TextView textView = findViewById(R.id.numberPickerInfoText);
        TextView textView1 = findViewById(R.id.numberPickerWhatNumber);
        Button tryAnother = findViewById(R.id.numberPickerTryAnother);
        tryAnother.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nextStep[0] = 0;
                Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.alpha);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation arg0) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation arg0) {
                    }

                    @Override
                    public void onAnimationEnd(Animation arg0) {
                        numberPicker.setVisibility(View.VISIBLE);
                        tryAnother.setVisibility(View.GONE);
                        textView1.setVisibility(View.GONE);
                        textView.setText("Выберите уникальный номер");
                        Animation animation1 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.alpha1);
                        numberPicker.startAnimation(animation1);
                        textView.startAnimation(animation1);
                    }
                });
                textView1.startAnimation(animation);
                textView.startAnimation(animation);
                tryAnother.startAnimation(animation);
            }
        });
        Button next = findViewById(R.id.numberPickerNextButton);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (nextStep[0] == 0) {
                    nextStep[0] = 1;
                    Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.alpha);
                    animation.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation arg0) {
                        }

                        @Override
                        public void onAnimationRepeat(Animation arg0) {
                        }

                        @Override
                        public void onAnimationEnd(Animation arg0) {
                            numberPicker.setVisibility(View.GONE);
                            tryAnother.setVisibility(View.VISIBLE);
                            textView1.setVisibility(View.VISIBLE);
                            textView1.setText(Integer.toString(numberPicker.getValue()));
                            playerNum = Integer.toString(numberPicker.getValue());
                            textView.setText("Ваш номер:");
                            Animation animation1 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.alpha1);
                            textView1.startAnimation(animation1);
                            textView.startAnimation(animation1);
                            tryAnother.startAnimation(animation1);
                        }
                    });
                    numberPicker.startAnimation(animation);
                    textView.startAnimation(animation);
                } else {
                    setContentView(R.layout.choose_type_layout);
                    initTypeLayout();
                }

            }
        });

    }

    private void discover() {

        mBluetoothLeScanner.startScan(mScanCallback);


    }


    AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.e("BLE", "Start: " + settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("BLE", "Advertising onStartFailure: " + errorCode);
            super.onStartFailure(errorCode);
        }
    };

    private void advertise(String name) {
        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        ParcelUuid pUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData( pUuid, name.getBytes(StandardCharsets.UTF_8 ) )
                .build();



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        advertiser.startAdvertising(settings, data, advertisingCallback);
    }


}

/*
public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter  = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;

    public static final int REQUEST_BT_PERMISSIONS = 0;
    public static final int REQUEST_BT_ENABLE = 1;

    private boolean mScanning = false;
    private Handler mHandler = null;

    private Button btnScan = null;

    double getDistance(int rssi, int txPower) {

        return Math.pow(10d, ((double) txPower - rssi) / (10 * 2));
    }

    private ScanCallback mLeScanCallback =
            new ScanCallback() {

                int pos = 0;
                final int WINDOW_SIZE = 25;
                double[] window = new double[WINDOW_SIZE];
                double[] window1 = new double[WINDOW_SIZE];
                @SuppressLint("MissingPermission")
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    //super.onScanResult(callbackType, result);
                    try {
                        if(result.getDevice().getName().equals("HUAWEI FreeBuds Pro 2")){
                            if(pos < WINDOW_SIZE){
                                window[pos] = result.getRssi();
                                window1[pos] = result.getTxPower();
                                pos++;
                            }
                            else {
                                for (int i = 0; i < WINDOW_SIZE - 1; i++) {
                                    window[i] = window[i + 1];
                                }
                                for (int i = 0; i < WINDOW_SIZE - 1; i++) {
                                    window1[i] = window1[i + 1];
                                }
                                window[WINDOW_SIZE - 1] = result.getRssi();
                                window1[WINDOW_SIZE - 1] = result.getTxPower();
                                double avrSum = 0;
                                double avrSum1 = 0;
                                for (int i = 0; i < WINDOW_SIZE; i++) {
                                    avrSum += window[i];
                                }
                                for (int i = 0; i < WINDOW_SIZE; i++) {
                                    avrSum1 += window1[i];
                                }


                                Log.println(Log.ERROR, "LOG", Double.toString(getDistance((int) (avrSum / WINDOW_SIZE), -69)) + "   " + (avrSum / WINDOW_SIZE) + "    " + (avrSum1 / WINDOW_SIZE));
                            }
                        }


                    }
                    catch (Exception ignored){

                    }

                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Log.i("BLE", "error");
                }
            };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan = (Button) findViewById(R.id.btnScan);

        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        this.mHandler = new Handler();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        checkBtPermissions();
        enableBt();
    }

    public void onBtnScan(View v){
        if (mScanning){
            mScanning = false;
            scanLeDevice(false);
            btnScan.setText("STOP");
        } else {
            mScanning = true;
            scanLeDevice(true);
            btnScan.setText("SCAN");
        }
    }

    public void checkBtPermissions() {
        this.requestPermissions(
                new String[]{
                        Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN
                },
                REQUEST_BT_PERMISSIONS);
    }

    @SuppressLint("MissingPermission")
    public void enableBt(){
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }
    }

    @SuppressLint("MissingPermission")
    public void scanLeDevice(final boolean enable) {
        //ScanSettings mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build();

        if (enable) {
            mScanning = true;
            Log.i("Scanning", "start");
            mBluetoothLeScanner.startScan(mLeScanCallback);
        } else {
            Log.i("Scanning", "stop");
            mScanning = false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }
}
*
 */

