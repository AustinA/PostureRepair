package duhblea.me.posturerepair;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gather and store Posture Repair sensor data.  Vibrate the user if bad posture is maintained
 * for too long
 *
 * @author Austin Alderton
 * @version 12 November 2016
 */
public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss:SSS");

    private Vibrator vibrator = null;


    // Data storage and parsing
    Gson gson = new Gson();
    private List<Sample> receivedSamples = null;

    // Data reception and power management
    private PowerManager powerManager = null;
    private PowerManager.WakeLock wakeLock = null;

    // Main user interface looper for side thread posting to UI
    private Handler uiThreadHandler = new Handler(Looper.getMainLooper());


    // Thread interpreting and conidtion the data
    private Thread dataConditioner = null;

    // UI declarations
    private String savedInput = "";
    private VisualMode visualMode = VisualMode.CURRENT_POSITION;
    private boolean snapToBottom = true;

    private TextView savedOutputTextView = null;
    private ScrollView savedOutputScrollView = null;
    private LinearLayout buttom_Buttons = null;
    private Button snapButton = null;
    private Button clearHistory = null;
    private ImageView currentPosition = null;
    private FloatingActionButton fabSaveCurrentPos = null;
    private Switch bnO055StatusSwitch = null;
    private LinearLayout historyView = null;
    private TextView historic = null;
    private TextView historyLabel = null;

    private Sample savedPosition = null;
    private Sample lastReceivedPosition = null;

    private boolean bno055IsConfigured = false;

    private HistoryHelper timeCounter = null;

    Intent startServiceIntent = null;

    StringBuffer strBuff = new StringBuffer();



    private BroadcastReceiver serverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(CommsConstants.ACTION_SAMPLE_DATA))
            {
                double received[] = intent.getDoubleArrayExtra(CommsConstants.INTENT_SAMPLE_DATA);

                Sample theSample = new Sample();
                theSample.setX(received[0]);
                theSample.setY(received[1]);
                theSample.setZ(received[2]);

                synchronized (receivedSamples) {
                    receivedSamples.add(theSample);

                    receivedSamples.notifyAll();

                }

                lastReceivedPosition = theSample;

                strBuff.append(theSample.toString());

                if (visualMode == VisualMode.LIVE_INPUT)
                {
                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            savedOutputTextView.setText(strBuff.toString());


                            if (snapToBottom) {
                                savedOutputScrollView.fullScroll(View.FOCUS_DOWN);
                            }
                        }
                    });
                }

                if (!bno055IsConfigured)
                {
                    bno055IsConfigured = true;
                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            bnO055StatusSwitch.setChecked(true);
                        }
                    });
                }


            }
            else if (intent.getAction().equals(CommsConstants.ACTION_BNO055_CONF)) {
                if (bno055IsConfigured) {
                    bno055IsConfigured = false;
                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            currentPosition.setImageResource(R.drawable.a);
                            bnO055StatusSwitch.setChecked(bno055IsConfigured);
                        }
                    });

                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Basic UI functionality instantations
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


        timeCounter = new HistoryHelper(MainActivity.this);

        receivedSamples = Collections.synchronizedList(new ArrayList<Sample>());


        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PostureRepairWakeLock");

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        startServiceIntent = new Intent (MainActivity.this, BluetoothCommService.class);
        startServiceIntent.setAction(CommsConstants.ACTION_CONNECT_BT);


        dataConditioner = new Thread(new DataConditioner());
        dataConditioner.start();



        // Application specific instantation
        initializeUI();

        // Set the start up visual mode
        setVisualMode(VisualMode.CURRENT_POSITION);


    }

    @Override
    protected void onPause() {
        super.onPause();


    }

    @Override
    public void onResume() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        wakeLock.acquire();

        IntentFilter filter = new IntentFilter();
        filter.addAction(CommsConstants.ACTION_SAMPLE_DATA);
        filter.addAction(CommsConstants.ACTION_BNO055_CONF);

        LocalBroadcastManager.getInstance(this).registerReceiver(serverReceiver, filter);
        super.onResume();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_connect) {
            startService(startServiceIntent);
            return true;
        } else if (id == R.id.action_disconnect) {
            BluetoothCommService.isRunning = false;
            return true;
        } else if (id == R.id.action_clear_save_pos) {
            savedPosition = null;

            currentPosition.setImageResource(R.drawable.a);

            Toast.makeText(MainActivity.this, "Ideal posture position cleared", Toast.LENGTH_LONG).show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.current_position) {
            setVisualMode(VisualMode.CURRENT_POSITION);
        } else if (id == R.id.live_input) {
            setVisualMode(VisualMode.LIVE_INPUT);
        } else if (id == R.id.collected_data) {
            setVisualMode(VisualMode.COLLECTED_DATA);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(serverReceiver);

    if(dataConditioner!=null)

    {
        try {
            dataConditioner.notify();
            dataConditioner.interrupt();
            dataConditioner.join(1000);
        } catch (Exception e) {
            Log.e("PostureRepair", "Closing thread in onDestroy: " + e.getMessage());
        }
    }

    timeCounter.saveTimes();

    if(wakeLock.isHeld())

    {
        wakeLock.release();
    }
}




    /**
     * Set the current visual mode of the application
     *
     * @param newMode
     */
    private void setVisualMode(VisualMode newMode) {
        if (newMode == VisualMode.CURRENT_POSITION) {
            savedOutputScrollView.setVisibility(View.GONE);
            buttom_Buttons.setVisibility(View.GONE);
            historyView.setVisibility(View.GONE);
            currentPosition.setVisibility(View.VISIBLE);

            visualMode = newMode;
        } else if (newMode == VisualMode.LIVE_INPUT) {
            buttom_Buttons.setVisibility(View.VISIBLE);
            savedOutputScrollView.setVisibility(View.VISIBLE);
            currentPosition.setVisibility(View.GONE);
            historyView.setVisibility(View.GONE);

            savedOutputTextView.setText(strBuff.toString());

            visualMode = newMode;
        } else if (newMode == VisualMode.COLLECTED_DATA) {
            savedOutputScrollView.setVisibility(View.GONE);
            currentPosition.setVisibility(View.GONE);
            historyView.setVisibility(View.VISIBLE);
            buttom_Buttons.setVisibility(View.GONE);

            visualMode = newMode;
        }
    }

    /**
     * Interface instantation specific to Posture Repair and its functionality
     */
    private void initializeUI() {
        savedOutputTextView = (TextView) findViewById(R.id.terminal_out);
        savedOutputScrollView = (ScrollView) findViewById(R.id.terminal_scrollView);
        buttom_Buttons = (LinearLayout) findViewById(R.id.live_input_buttons);
        snapButton = (Button) findViewById(R.id.snap_bottom_free_btn);
        clearHistory = (Button) findViewById(R.id.clear_raw_input);
        currentPosition = (ImageView) findViewById(R.id.live_image_view);
        fabSaveCurrentPos = (FloatingActionButton) findViewById(R.id.fab);
        bnO055StatusSwitch = (Switch) findViewById(R.id.bnO055_status_switch);
        historyView = (LinearLayout) findViewById(R.id.historyLayout);
        historic = (TextView) findViewById(R.id.goodTimeTextView);
        historyLabel = (TextView) findViewById(R.id.historyLabel);

        historyLabel.setText("Posture Posture:    \n\n\n\nBad Posture:    ");

        fabSaveCurrentPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bno055IsConfigured) {
                    savedPosition = lastReceivedPosition;
                    Toast.makeText(MainActivity.this, "Position set as ideal posture", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Unable to set position, " +
                            "BNO055 may not be configured", Toast.LENGTH_LONG).show();
                }

            }
        });

        snapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snapToBottom = !snapToBottom;

                if (snapToBottom) {
                    snapButton.setText("Disable Autoscrolling");
                } else {
                    snapButton.setText("Enable Autoscrolling");
                }
            }
        });

        clearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                savedInput = "";
                savedOutputTextView.setText("");
            }
        });

    }



    /**
     * Worker thread that iterates over received synchronized Hash Map and updates the position
     * current position on the screen and determines if the position is bad enough to warrant
     * a notification vibration
     */
    class DataConditioner implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {

                synchronized (receivedSamples) {

                    try
                    {
                        receivedSamples.wait();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    if (visualMode == VisualMode.CURRENT_POSITION && savedPosition != null && receivedSamples.size() > 0)
                    {
                        Sample theSample = receivedSamples.get(receivedSamples.size() - 1);

                        final long index = Math.round(savedPosition.getY() - theSample.getY());

                        uiThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                if (index >= 0 && bno055IsConfigured) {
                                    if (index == 0) {
                                        currentPosition.setImageResource(R.drawable.a);
                                    } else if (index == 1) {
                                        currentPosition.setImageResource(R.drawable.b);
                                    } else if (index == 2) {
                                        currentPosition.setImageResource(R.drawable.c);
                                    } else if (index == 3) {
                                        currentPosition.setImageResource(R.drawable.d);
                                    } else if (index == 4) {
                                        currentPosition.setImageResource(R.drawable.e);
                                    } else if (index == 5) {
                                        currentPosition.setImageResource(R.drawable.f);
                                    } else if (index == 6) {
                                        currentPosition.setImageResource(R.drawable.g);
                                    } else if (index > 6 && index < 10) {
                                        currentPosition.setImageResource(R.drawable.h);
                                    }
                                }
                                else
                                {
                                    currentPosition.setImageResource(R.drawable.a);
                                }
                            }
                        });
                    }

                    if (receivedSamples.size() >= 20) {
                        if (savedPosition != null) {

                            int good = 0;
                            int bad = 0;

                                for (Sample theSample : receivedSamples) {
                                    double difference = savedPosition.getY() - theSample.getY();
                                    if (difference >= 0 && difference >= 5) {
                                        bad++;
                                        timeCounter.incrementBadTime();
                                    } else {
                                        good++;
                                        timeCounter.incrementGoodTime();
                                    }
                                }


                                if (bad > good) {
                                    vibrator.vibrate(500);
                                }

                                uiThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        historic.setText(timeCounter.toString());

                                    }
                                });

                            }
                        }

                        if (receivedSamples.size() >= 20) {
                            receivedSamples.clear();
                        }
                    }
                }
            }
        }

}
