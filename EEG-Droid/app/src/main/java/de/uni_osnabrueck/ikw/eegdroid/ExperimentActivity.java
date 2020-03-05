package de.uni_osnabrueck.ikw.eegdroid;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

public class ExperimentActivity extends AppCompatActivity {

    private final static String TAG = ExperimentActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private TextView mConnectionState;
    private TextView viewDeviceAddress;
    private TextView experimentState;
    private TextView time;
    private String mDeviceAddress;
    private String mDeviceName;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private boolean recording = false;
    private String selected_gain;
    private final Handler handler = new Handler();
    private final List<List<Float>> accumulated = new ArrayList<>();
    // constants
    private final int CONNECT_DELAY = 2000;
    private final float DATAPOINT_TIME = 4.5f;
    private float res_time;
    private float res_freq;
    private int cnt = 0;
    private int ch1_color;
    private int ch2_color;
    private int ch3_color;
    private int ch4_color;
    private int ch5_color;
    private int ch6_color;
    private int ch7_color;
    private int ch8_color;
    private TextView mCh1;
    private TextView mCh2;
    private TextView mCh3;
    private TextView mCh4;
    private TextView mCh5;
    private TextView mCh6;
    private TextView mCh7;
    private TextView mCh8;
    private TextView mDataResolution;
    private Spinner gain_spinner;
    private Button experimentButton;
    private Button abortButton;
    private LinkedList<Float> dp_received;
    private LinkedList<float[]> main_data;
    private float data_cnt = 0;
    private long start_data = 0;
    private String start_time;
    private String end_time;
    private long start_watch;
    private String recording_time;
    private long start_timestamp;
    private long end_timestamp;
    private boolean deviceConnected = false;
    private boolean casting = false;
    private Menu menu;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<Float> microV;
    private ExperimentActivity.CastThread caster;
    private int block = 1;
    private EditText userInputLabel;
    private Timer timer;
    private Timer terminator;
    private LinkedList<Long> auditoryStimuli_time;
    private LinkedList<Integer> auditoryStimuli;
    private LinkedList<Long> metaAwareness_time;
    private boolean fileLabelled = false;
    private FileWriter fileWriterEEG;
    private FileWriter fileWriterAuditory;
    private FileWriter fileWriterMW;
    private boolean fileCreated = false;
    private boolean finishedWriting = false;
    private SoundPool soundPool;
    private int stimulusStandard;
    private int stimulusOddball;
    private PowerManager.WakeLock wakeLock;
    private String date;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // hack for ensuring a successful connection
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeService.connect(mDeviceAddress);
                }
            }, CONNECT_DELAY);  // connect with a defined delay

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (mBluetoothLeService != null) {
                mBluetoothLeService = null;
            }
        }
    };

    private final View.OnClickListener experimentButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!fileLabelled) {
                askForSessionLabel();
            } else {
                if (deviceConnected) {
                    if (!recording) {
                            startTrial();
                    } else {
                        metaAwareness_time.add(System.currentTimeMillis() - start_watch);
                        Log.d(TAG, "Meta awareness recorded");
                        Toast.makeText(
                                getApplicationContext(),
                                "Meta awareness recorded",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                } else {
                    Toast.makeText(
                            getApplicationContext(),
                            "Please connect to Traumschreiber before starting the experiment.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        }
    };

    private final View.OnClickListener abortButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (recording){
                //Handles the Dialog to confirm aborting the current block of the experiment
                AlertDialog.Builder alert = new AlertDialog.Builder(ExperimentActivity.this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_abort));
                alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        abortBlock();
                    }
                });
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // close dialog
                        dialog.cancel();
                    }
                });
                alert.show();
            }
            else {
                //Handles the Dialog to confirm resetting the experiment
                AlertDialog.Builder alert = new AlertDialog.Builder(ExperimentActivity.this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_reset));
                alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        reset();
                    }
                });
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // close dialog
                        dialog.cancel();
                    }
                });
                alert.show();
            }
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                setConnectionStatus(true);


            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                setConnectionStatus(false);
                clearUI();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                if (mNotifyCharacteristic == null) {
                    readGattCharacteristic(mBluetoothLeService.getSupportedGattServices());
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {


                System.out.println(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                data_cnt++;
                long last_data = System.currentTimeMillis();

                microV = transData(intent.getIntArrayExtra(BluetoothLeService.EXTRA_DATA));
                displayData(microV);
                if (recording) storeData(microV);
                if (start_data == 0) start_data = System.currentTimeMillis();
                res_time = (last_data - start_data) / data_cnt;
                res_freq = (1 / res_time) * 1000;
                String hertz = String.valueOf((int) res_freq) + "Hz";
                @SuppressLint("DefaultLocale") String resolution = String.format("%.2f", res_time) + "ms - ";
                String content = resolution + hertz;
                mDataResolution.setText(content);
                //setConnectionStatus(true);
                if (recording) {
                    mConnectionState.setText(R.string.recording);
                    mConnectionState.setTextColor(Color.RED);
                } else {
                    mConnectionState.setText(R.string.device_connected);
                    mConnectionState.setTextColor(Color.GREEN);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment);
        ch1_color = ContextCompat.getColor(getApplicationContext(), R.color.aqua);
        ch2_color = ContextCompat.getColor(getApplicationContext(), R.color.fuchsia);
        ch3_color = ContextCompat.getColor(getApplicationContext(), R.color.green);
        ch4_color = ContextCompat.getColor(getApplicationContext(), android.R.color.holo_purple);
        ch5_color = ContextCompat.getColor(getApplicationContext(), R.color.orange);
        ch6_color = ContextCompat.getColor(getApplicationContext(), R.color.red);
        ch7_color = ContextCompat.getColor(getApplicationContext(), R.color.yellow);
        ch8_color = ContextCompat.getColor(getApplicationContext(), R.color.black);
        experimentButton = findViewById(R.id.button_experiment);
        abortButton = findViewById(R.id.button_abort);
        gain_spinner = findViewById(R.id.gain_spinner);
        gain_spinner.setSelection(1);
        gain_spinner.setEnabled(false);
        selected_gain = gain_spinner.getSelectedItem().toString();
        gain_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                float max;
                switch (position) {
                    case 0:
                        selected_gain = "0.5";
                        max = 2100f;
                        break;
                    case 1:
                        selected_gain = "1";
                        max = 1700f;
                        break;
                    case 2:
                        selected_gain = "2";
                        max = 850f;
                        break;
                    case 3:
                        selected_gain = "4";
                        max = 425f;
                        break;
                    case 4:
                        selected_gain = "8";
                        max = 210f;
                        break;
                    case 5:
                        selected_gain = "16";
                        max = 110f;
                        break;
                    case 6:
                        selected_gain = "32";
                        max = 60f;
                        break;
                    case 7:
                        selected_gain = "64";
                        max = 30f;
                        break;
                    default:
                        selected_gain = "1";
                        max = 2100f;
                }
                if (mBluetoothLeService != null) {
                    writeGattCharacteristic(mBluetoothLeService.getSupportedGattServices());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // sometimes you need nothing here
            }
        });
        experimentButton.setOnClickListener(experimentButtonOnClickListener);
        abortButton.setOnClickListener(abortButtonOnClickListener);


        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        experimentState = findViewById(R.id.experiment_state);
        time = findViewById(R.id.time);
        time.setText(R.string.time_default);

        // Extract the info from the intent

        //Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        //bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        viewDeviceAddress = (TextView) findViewById(R.id.device_address);
        mConnectionState = findViewById(R.id.connection_state);
        mCh1 = findViewById(R.id.ch1);
        mCh2 = findViewById(R.id.ch2);
        mCh3 = findViewById(R.id.ch3);
        mCh4 = findViewById(R.id.ch4);
        mCh5 = findViewById(R.id.ch5);
        mCh6 = findViewById(R.id.ch6);
        mCh7 = findViewById(R.id.ch7);
        mCh8 = findViewById(R.id.ch8);
        mCh1.setTextColor(ch1_color);
        mCh2.setTextColor(ch2_color);
        mCh3.setTextColor(ch3_color);
        mCh4.setTextColor(ch4_color);
        mCh5.setTextColor(ch5_color);
        mCh6.setTextColor(ch6_color);
        mCh7.setTextColor(ch7_color);
        mCh8.setTextColor(ch8_color);

        mDataResolution = findViewById(R.id.resolution_value);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // create sound pool for auditory oddball paradigm
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(audioAttributes).build();
        stimulusStandard = soundPool.load(this, R.raw.standard16_f, 1);
        stimulusOddball = soundPool.load(this, R.raw.oddball16_f, 1);

        // create wake lock in order to keep the CPU running while the experiment is in progress
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TAG + "Wake Lock");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceConnected) {
            unbindService(mServiceConnection);
        }

        soundPool.release();
        soundPool = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluethoot_conect, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();
        if (id == R.id.scan) {
            if (!deviceConnected) {
                Intent intent = new Intent(this, DeviceScanActivity.class);
                startActivityForResult(intent, 1200);
            } else {

                //Handles the Dialog to confirm the closing of the activity
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_disconnect));
                alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        mBluetoothLeService.disconnect();
                    }
                });
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // close dialog
                        dialog.cancel();
                    }
                });
                alert.show();
            }
        }

        if (id == android.R.id.home) {

            if (recording) {

                //Handles the Dialog to confirm the closing of the activity
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_close_record));
                alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        onBackPressed();
                    }
                });
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // close dialog
                        dialog.cancel();
                    }
                });
                alert.show();
            } else {
                onBackPressed();
            }

            return true;
        }

        if (id == R.id.cast) {

            MenuItem menuItemCast = menu.findItem(R.id.cast);

            if (!casting) {

                casting = true;
                caster = new ExperimentActivity.CastThread();
                caster.start();
                menuItemCast.setIcon(R.drawable.ic_cast_blue_24dp);

            } else {
                casting = false;
                caster.staph();
                menuItemCast.setIcon(R.drawable.ic_cast_white_24dp);

            }

        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check which request we're responding to
        if (requestCode == 1200) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected
                mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
                mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            }
        }
    }


    private void writeGattCharacteristic(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if (!uuid.equals("a22686cb-9268-bd91-dd4f-b52d03d85593")) {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    // uuid -> "faa7b588-19e5-f590-0545-c99f193c5c3e"
                    // start reading the EEG data received from this gatt characteristic
                    final int charaProp = gattCharacteristic.getProperties();
                    if (((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) |
                            (charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                        // gain-> {0.5:0b111, 1:0b000, 2:0b001, 4:0b010, 8:0b011, 16:0b100, 32:0b101, 64:0b110}
                        final byte[] newValue = new byte[6];
                        switch (selected_gain) {
                            case "0.5":
                                newValue[4] = 0b111;
                                break;
                            case "1":
                                newValue[4] = 0b000;
                                break;
                            case "2":
                                newValue[4] = 0b001;
                                break;
                            case "4":
                                newValue[4] = 0b010;
                                break;
                            case "8":
                                newValue[4] = 0b011;
                                break;
                            case "16":
                                newValue[4] = 0b100;
                                break;
                            case "32":
                                newValue[4] = 0b101;
                                break;
                            case "64":
                                newValue[4] = 0b110;
                                break;
                        }
                        gattCharacteristic.setValue(newValue);
                        mBluetoothLeService.writeCharacteristic(gattCharacteristic);
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = gattCharacteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    gattCharacteristic, true);
                        }
                    }
                }
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    private void readGattCharacteristic(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if (uuid.equals("a22686cb-9268-bd91-dd4f-b52d03d85593")) {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    // uuid -> "faa7b588-19e5-f590-0545-c99f193c5c3e"
                    // start reading the EEG data received from this gatt characteristic
                    final int charaProp = gattCharacteristic.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (mNotifyCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    mNotifyCharacteristic, false);
                            mNotifyCharacteristic = null;
                        }
                        mBluetoothLeService.readCharacteristic(gattCharacteristic);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        mNotifyCharacteristic = gattCharacteristic;
                        mBluetoothLeService.setCharacteristicNotification(
                                gattCharacteristic, true);
                    }
                    //mBluetoothLeService.disconnect();
                    mBluetoothLeService.connect(mDeviceAddress);
                }
            }
        }
    }

    private void clearUI() {
        mCh1.setText("");
        mCh2.setText("");
        mCh3.setText("");
        mCh4.setText("");
        mCh5.setText("");
        mCh6.setText("");
        mCh7.setText("");
        mCh8.setText("");
        mDataResolution.setText(R.string.no_data);
        data_cnt = 0;
        start_data = 0;
    }


    private List<Float> transData(int[] data) {
        // Assuming GAIN = 64
        // Conversion formula: V_in = X*1.65V/(1000 * GAIN * 2048)
        float gain = Float.parseFloat(selected_gain);
        float numerator = 1650;
        float denominator = gain * 2048;
        List<Float> data_trans = new ArrayList<>();
        for (int datapoint : data)
            data_trans.add((datapoint * numerator) / denominator);
        return data_trans;
    }

    @SuppressLint("DefaultLocale")
    private void displayData(List<Float> data_microV) {
        if (data_microV != null) {
            // data format example: +01012 -00234 +01374 -01516 +01656 +01747 +00131 -00351
            StringBuilder trans = new StringBuilder();
            List<String> values = new ArrayList<>();
            for (Float value : data_microV) {
                if (value >= 0) {
                    trans.append("+");
                    trans.append(String.format("%5.2f", value));
                } else trans.append(String.format("%5.2f", value));
                values.add(trans.toString());
                trans = new StringBuilder();
            }
            mCh1.setText(values.get(0));
            mCh2.setText(values.get(1));
            mCh3.setText(values.get(2));
            mCh4.setText(values.get(3));
            mCh5.setText(values.get(4));
            mCh6.setText(values.get(5));
            mCh7.setText(values.get(6));
            mCh8.setText(values.get(7));
        }
    }

    // Starts a block of the experiment
    @SuppressLint({"SimpleDateFormat", "SetTextI18n"})
    private void startTrial() {
        if (block <= 3){
            // create lists for storing data
            dp_received = new LinkedList<>();
            main_data = new LinkedList<>();
            auditoryStimuli_time = new LinkedList<>();
            auditoryStimuli = new LinkedList<>();
            metaAwareness_time = new LinkedList<>();

            date = new SimpleDateFormat("dd-MM-yyyy_HH-mm").format(new Date());
            start_time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            cnt = 0;
            start_watch = System.currentTimeMillis();
            recording = true;
            start_timestamp = new Timestamp(start_watch).getTime();

            playSounds(); // auditory oddball paradigm
            updateTimer();
            openFiles();
            writeToCSV();
            wakeLock.acquire(21*60*1000); // keep the CPU on while a block is running

            experimentButton.setText(R.string.meta_awareness);
            experimentButton.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
            abortButton.setText(R.string.abort);
            switch (block) {
                case 1:
                    experimentState.setText(R.string.status_block1);
                    break;
                case 2:
                    experimentState.setText(R.string.status_block2);
                    break;
                case 3:
                    experimentState.setText(R.string.status_block3);
                    break;
            }
            Toast.makeText(
                    getApplicationContext(),
                    "Block "+ block + " started.",
                    Toast.LENGTH_LONG
            ).show();
        }
        else {
            Toast.makeText(
                    getApplicationContext(),
                    "Experiment completed.",
                    Toast.LENGTH_LONG
            ).show();
            reset();
        }
    }

    // Terminates a block of the experiment
    @SuppressLint("SimpleDateFormat")
    private void endTrial() {
        recording = false;
        long stop_watch = System.currentTimeMillis();
        end_timestamp = new Timestamp(stop_watch).getTime();
        recording_time = Long.toString(stop_watch - start_watch);
        end_time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());

        closeFiles();

        try{
            wakeLock.release();
        }
        catch (Exception e) {
            Log.e(TAG, "Error releasing wake lock: " + e);
        }

        if (block < 3) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    experimentState.setText(R.string.status_waiting);
                    experimentButton.setText(R.string.next);
                    experimentButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#924CD814")));
                    abortButton.setText(R.string.reset2);
                }
            });
        }
        else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    experimentState.setText(R.string.status_done);
                    experimentButton.setText(R.string.reset);
                    experimentButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#924CD814")));
                    abortButton.setText(R.string.reset2);
                }
            });
        }
        block++;
    }

    // Auditory oddball paradigm with frequency deviants
    private void playSounds() {

        timer = new Timer(true); // timer for auditory stimuli
        terminator = new Timer(true); // timer for block termination

        class soundTimer extends TimerTask {

            private boolean oddball;

            private soundTimer(boolean oddball) {
                this.oddball = oddball;
            }

            @Override
            public void run() {
                // 80% of the stimuli are standards, 20% oddballs, two oddballs are never presented successively
                int stimuliRatio = random(1, 10);
                if (stimuliRatio <= 2 && !oddball){
                    // play oddball stimulus (1000 Hz)
                    auditoryStimuli_time.add(System.currentTimeMillis() - start_watch);
                    soundPool.play(stimulusOddball, 1, 1, 1,0, 1);
                    auditoryStimuli.add(1); // 1 denotes oddball stimulus
                    oddball = true;
                    Log.d(TAG, "Oddball played");
                }
                else {
                    // play standard stimulus (500 Hz)
                    auditoryStimuli_time.add(System.currentTimeMillis() - start_watch);
                    soundPool.play(stimulusStandard, 1, 1, 1, 0, 1);
                    auditoryStimuli.add(0); // 0 denotes standard stimulus
                    oddball = false;
                    Log.d(TAG, "Standard played");
                }
                // 740 - 1225 ms because some time has passed since previous stimulus onset
                long delay = (long) random(740, 1225); // inter-stimulus interval 750 - 1250 ms
                timer.schedule(new soundTimer(oddball), delay); // schedule next auditory stimulus
            }
        }

        TimerTask killerTask = new TimerTask() {
            @Override
            public void run() {
                timer.cancel();  // terminates presentation of auditory stimuli
                endTrial();
            }
        };

        timer.schedule(new soundTimer(false), 0);  // start auditory stimuli
        long end = start_watch + 20*60*1000;  // one block lasts 20 min
        terminator.schedule(killerTask, end - System.currentTimeMillis()); // schedule block termination
    }

    public static int random(int min, int max){
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private void reset() {
        block = 1;
        fileLabelled = false;
        userInputLabel = null;
        experimentButton.setText(R.string.experiment_start);
        experimentButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#924CD814")));
        experimentState.setText(R.string.status_not_started);
        time.setText(R.string.time_default);
    }

    private void abortBlock() {
        timer.cancel();
        // IMPORTANT: Block terminator has to be cancelled as well, otherwise it keeps running in
        // the background and terminates the next block too early.
        terminator.cancel();
        endTrial();
        block--;
        fileLabelled = false;
        experimentState.setText(R.string.status_aborted);
        experimentButton.setText(R.string.restart);
        Toast.makeText(
                getApplicationContext(),
                "Block " + block + " aborted.",
                Toast.LENGTH_LONG
        ).show();
    }

    // continuously displays how long the current block is running (updated every second)
    private void updateTimer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
               while(recording) {
                   runOnUiThread(new Runnable() {
                       @Override
                       public void run() {
                           time.setText(new SimpleDateFormat("mm:ss").format(System.currentTimeMillis() - start_watch));
                       }
                   });
                   try {
                       Thread.sleep(1000);
                   } catch (InterruptedException e) {
                       e.printStackTrace();
                   }
               }
            }
        }).start();
    }

    //Stores data while session is running
    private void storeData(List<Float> data_microV) {
        /* if (dp_received.size() == 0) start_watch = System.currentTimeMillis();
         * Doesn't work anymore, since the data in dp_received is deleted after being written
         * to a csv file in order to save memory, thus, dp_received.size() may yield 0 even though
         * recording is in progress.
         * Instead, start_watch is now initialized in startTrial()
         */
        float[] f_microV = new float[data_microV.size()];
        float curr_received = System.currentTimeMillis() - start_watch;
        dp_received.add(curr_received);
        int i = 0;
        for (Float f : data_microV)
            f_microV[i++] = (f != null ? f : Float.NaN); // Or whatever default you want
        main_data.add(f_microV);
    }

    // opens new csv files and writes headers
    private void openFiles() {
        finishedWriting = false;
        final String sessionTag = userInputLabel.getText().toString();
        final String dp_header = "Time (EEG),Ch-1,Ch-2,Ch-3,Ch-4,Ch-5,Ch-6,Ch-7,Ch-8";
        final String auditory_header = "Stimulus onset (audio),Stimulus type";
        final String mw_header = "Time (meta awareness)";
        final char break_line = '\n';
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean error = false;
                // create file for EEG data
                try {
                    File formatted = new File(MainActivity.getDirSessions(),
                            date + "_" + sessionTag + "_EEG_" + block + ".csv");
                    // if file doesn't exists, then create it
                    if (!formatted.exists()) //noinspection ResultOfMethodCallIgnored
                        formatted.createNewFile();
                    fileWriterEEG = new FileWriter(formatted);
                    fileWriterEEG.append(dp_header);
                    fileWriterEEG.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "OpenFiles: Error creating file for EEG data" + e);
                    error = true;
                }
                // create file for auditory data
                try {
                    File formatted = new File(MainActivity.getDirSessions(),
                            date + "_" + sessionTag + "_auditory_" + block + ".csv");
                    // if file doesn't exists, then create it
                    if (!formatted.exists()) //noinspection ResultOfMethodCallIgnored
                        formatted.createNewFile();
                    fileWriterAuditory = new FileWriter(formatted);
                    fileWriterAuditory.append(auditory_header);
                    fileWriterAuditory.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "OpenFiles: Error creating file for auditory data" + e);
                    error = true;
                }
                // create file for mind wandering data
                try {
                    File formatted = new File(MainActivity.getDirSessions(),
                            date + "_" + sessionTag + "_mw_" + block + ".csv");
                    // if file doesn't exists, then create it
                    if (!formatted.exists()) //noinspection ResultOfMethodCallIgnored
                        formatted.createNewFile();
                    fileWriterMW = new FileWriter(formatted);
                    fileWriterMW.append(mw_header);
                    fileWriterMW.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "OpenFiles: Error creating file for mind wandering data" + e);
                    error = true;
                }
                if (!error) {
                    fileCreated = true;
                    Log.d(TAG, "CSV files have been created");
                }
            }
        }).start();
    }

    /* Continuously writes data to csv files while recording is in progress.
     * The data which is stored locally in dp_received, main_data, auditoryStimuli_time,
     * auditoryStimuli and metaAwareness_time is deleted while the data is written to the respective
     * csv files in order to optimize local memory usage.
     */
    private void writeToCSV() {
        final char delimiter = ',';
        final char break_line = '\n';
        new Thread(new Runnable() {
            @Override
            public void run() {
                // wait until csv file has been created
                while (!fileCreated){
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                int rows;
                while (recording) {
                    if (!dp_received.isEmpty() || !auditoryStimuli_time.isEmpty() || !metaAwareness_time.isEmpty()) {
                        // write EEG data
                        try {
                            rows = Math.min(dp_received.size(), main_data.size());
                            for (int i = 0; i < rows; i++) {
                                fileWriterEEG.append(String.valueOf(dp_received.remove()));
                                fileWriterEEG.append(delimiter);
                                for (float channel : main_data.remove()) {
                                    fileWriterEEG.append(String.valueOf(channel));
                                    fileWriterEEG.append(delimiter);
                                }
                                fileWriterEEG.append(break_line);
                                Log.d(TAG, "Writing EEG data");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "WriteToCSV: Error storing EEG data into a CSV file: " + e);
                        }
                        // write auditory data
                        try {
                            rows = Math.min(auditoryStimuli_time.size(), auditoryStimuli.size());
                            for (int i = 0; i < rows; i++) {
                                fileWriterAuditory.append(String.valueOf(auditoryStimuli_time.remove()));
                                fileWriterAuditory.append(delimiter);
                                fileWriterAuditory.append(String.valueOf(auditoryStimuli.remove()));
                                fileWriterAuditory.append(delimiter);
                                fileWriterAuditory.append(break_line);
                                Log.d(TAG, "Writing auditory data");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "WriteToCSV: Error storing auditory data into a CSV file: " + e);
                        }
                        // write mind wandering data
                        try {
                            rows = metaAwareness_time.size();
                            for (int i = 0; i < rows; i++) {
                                fileWriterMW.append(String.valueOf(metaAwareness_time.remove()));
                                fileWriterMW.append(delimiter);
                                fileWriterMW.append(break_line);
                                Log.d(TAG, "Writing meta awareness data");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "WriteToCSV: Error storing mind wandering data into a CSV file: " + e);
                        }
                    }
                    else {
                        try {
                            Thread.sleep(2000);  // wait if there is currently no data to write
                            Log.d(TAG, "Currently no data to write");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                finishedWriting = true;
            }
        }).start();
    }

    /* Closes csv files at the end of each block and adds meta data.
     * Meta data is added at the end of the file since the ending timestamps and the actual
     * recording resolution can only be calculated after the recording is finished.
     */
    private void closeFiles(){
        final char delimiter = ',';
        final char break_line = '\n';
        final String sessionTag = userInputLabel.getText().toString();
        final String top_header = "Session Tag,Date,Duration (ms),Starting Time,Ending Time," +
                "Resolution (ms),Resolution (Hz),Unit Measure,Starting Timestamp,Ending Timestamp";
        final String header = "Session Tag, Date, Duration (ms), Starting Time, Ending Time, " +
                "Starting Timestamp, Ending Timestamp";
        new Thread(new Runnable() {
            @Override
            public void run() {
                // wait until all data is written
                while(!finishedWriting) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // meta data for EEG data
                try {
                    fileWriterEEG.append(break_line);
                    fileWriterEEG.append(top_header);
                    fileWriterEEG.append(break_line);
                    fileWriterEEG.append(sessionTag);
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(date);
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(recording_time);
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(start_time);
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(end_time);
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(String.valueOf(res_time));
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(String.valueOf(res_freq));
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append("µV");
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(Long.toString(start_timestamp));
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(Long.toString(end_timestamp));
                    fileWriterEEG.append(delimiter);
                    fileWriterEEG.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "Error writing meta data of EEG data: " + e);
                }
                // meta data for auditory data
                try {
                    fileWriterAuditory.append(break_line);
                    fileWriterAuditory.append(header);
                    fileWriterAuditory.append(break_line);
                    fileWriterAuditory.append(sessionTag);
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(date);
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(recording_time);
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(start_time);
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(end_time);
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(Long.toString(start_timestamp));
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(Long.toString(end_timestamp));
                    fileWriterAuditory.append(delimiter);
                    fileWriterAuditory.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "Error writing meta data of auditory data: " + e);
                }
                // meta data for mind wandering data
                try {
                    fileWriterMW.append(break_line);
                    fileWriterMW.append(header);
                    fileWriterMW.append(break_line);
                    fileWriterMW.append(sessionTag);
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(date);
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(recording_time);
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(start_time);
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(end_time);
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(Long.toString(start_timestamp));
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(Long.toString(end_timestamp));
                    fileWriterMW.append(delimiter);
                    fileWriterMW.append(break_line);
                } catch (Exception e) {
                    Log.e(TAG, "Error writing meta data of mind wandering data: " + e);
                }
                // close files
                try {
                    fileWriterEEG.flush();
                    fileWriterAuditory.flush();
                    fileWriterMW.flush();
                    fileWriterEEG.close();
                    fileWriterAuditory.close();
                    fileWriterMW.close();
                } catch (IOException e) {
                    Log.e(TAG, "CloseFiles: Error storing the data into CSV files: " + e);
                }
                fileCreated = false;
            }
        }).start();
    }

    private void setConnectionStatus(boolean b) {
        MenuItem menuItem = menu.findItem(R.id.scan);
        MenuItem menuItemCast = menu.findItem(R.id.cast);
        if (b) {
            deviceConnected = true;
            menuItem.setIcon(R.drawable.ic_bluetooth_connected_blue_24dp);
            mConnectionState.setText(R.string.device_connected);
            mConnectionState.setTextColor(Color.GREEN);
            gain_spinner.setEnabled(true);
            viewDeviceAddress.setText(mDeviceAddress);
            menuItemCast.setVisible(true);

        } else {
            deviceConnected = false;
            menuItem.setIcon(R.drawable.ic_bluetooth_searching_white_24dp);
            mConnectionState.setText(R.string.no_device);
            mConnectionState.setTextColor(Color.LTGRAY);
            gain_spinner.setEnabled(false);
            viewDeviceAddress.setText(R.string.no_address);
            menuItemCast.setVisible(false);
        }
    }

    private void askForSessionLabel(){
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        final View mView = layoutInflaterAndroid.inflate(R.layout.input_dialog_string, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(ExperimentActivity.this);
        alertDialogBuilderUserInput.setView(mView);

        //userInputLabel = (EditText) mView.findViewById(R.id.input_dialog_string_Input);

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setTitle(R.string.session_label_title)
                .setMessage(getResources().getString(R.string.enter_session_label))
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {
                        userInputLabel = mView.findViewById(R.id.input_dialog_string_Input);
                        fileLabelled = true;
                        Toast.makeText(getApplicationContext(), "Session label stored successfully.", Toast.LENGTH_LONG).show();
                    }
                });

        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }


    class CastThread extends Thread {

        private volatile boolean exit = false;
        String IP = getSharedPreferences("userPreferences", 0).getString("IP", getResources().getString(R.string.default_IP));
        String port = getSharedPreferences("userPreferences", 0).getString("port", getResources().getString(R.string.default_port));

        public void run() {

            while (!exit) {
                try {
                    try {
                        socket = new Socket(IP, Integer.parseInt(port));
                        out = new PrintWriter(socket.getOutputStream(), true);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        Log.d("CastThread", "sending");

                        while (true) {
                            if (microV != null) {
                                String toSend = "";
                                //microV.size() == 8
                                for (Float value : microV) {
                                    toSend = toSend + value + ",";
                                }
                                Log.d("LENMICRO", Integer.toString(toSend.length()));
                                out.println(toSend);
                            }
                        }


                    } catch (UnknownHostException e) {
                        Log.d("CastThread", "unknown host " + e.getMessage());

                    } catch (IOException e) {
                        Log.d("CastThread", "no I/O " + e.getMessage());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        public void staph() {

            Log.d("CastThread", "Stopped");
            exit = true;
            if (out != null) {
                out.close();
            }
            //maybe when activity closes
//            try {
//                socket.close();
//            } catch (IOException e){
//            }

        }

    }

}
