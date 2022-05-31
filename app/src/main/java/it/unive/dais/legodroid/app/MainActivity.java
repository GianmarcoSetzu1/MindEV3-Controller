package it.unive.dais.legodroid.app;

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.comm.Connection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.LightSensor;
import it.unive.dais.legodroid.lib.plugs.Plug;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.TouchSensor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Consumer;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.ThrowingConsumer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = Prelude.ReTAG("MainActivity");

    private TextView textView;
    private final Map<String, Object> statusMap = new HashMap<>();
    @Nullable
    //private TachoMotor motor;   // this is a class field because we need to access it from multiple methods
    private TachoMotor motorB;
    private TachoMotor motorC;
    private int speed = 20;
    boolean read = false;
    boolean forward = false;
    boolean backward = false;
    boolean left = false;
    boolean right = false;
    boolean stopped = true;
    private Pair<Double, Double> accXY = null;
    int index = 0;

    private EV3 ev3;
    Thread thread_repeat;
    Thread thread_register;

    Boolean start = false;


    private void updateStatus(@NonNull Plug p, String key, Object value) {
        Log.d(TAG, String.format("%s: %s: %s", p, key, value));
        statusMap.put(key, value);
        runOnUiThread(() -> textView.setText(statusMap.toString()));
    }


    /* From here */
    /* Legodroid's methods, left for any future developments */

    private void setupEditable(@IdRes int id, Consumer<Integer> f) {
        EditText e = findViewById(id);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable s) {
                int x = 0;
                try {
                    x = Integer.parseInt(s.toString());
                } catch (NumberFormatException ignored) {
                }
                f.call(x);
            }
        });
    }


    private static class MyCustomApi extends EV3.Api {
        private MyCustomApi(@NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }
        public void mySpecialCommand() { /* do something special */ }
    }

    /* End */



    // quick wrapper for accessing the private field MainActivity.motor only when not-null; also ignores any exception thrown
    private void applyMotor(@NonNull ThrowingConsumer<TachoMotor, Throwable> f) {
        if (motorB != null) Prelude.trap(() -> f.call(motorB));
        if (motorC != null) Prelude.trap(() -> f.call(motorC));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());

        thread_repeat = null;
        thread_register = null;


        Button bluetooth = findViewById(R.id.bluetooth);
        try {
            // connect to EV3 via bluetooth
            Connection c = new BluetoothConnection("EDITH");
            ev3 = new EV3(c.connect());    // replace with your own brick name

            bluetooth.setVisibility(View.VISIBLE);

            Button ready = findViewById(R.id.ready);

            SeekBar s = findViewById(R.id.speedEdit);

            Button listenMind = findViewById(R.id.listenButton);
            listenMind.setOnClickListener(v -> {
                start = !start;
                Log.d("Start : ", String.valueOf(start));
                    thread_register = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("Ascolto...", "OK");
                            if (!Python.isStarted()) {
                                Python.start(new AndroidPlatform(getApplicationContext()));
                            }
                            Python py = Python.getInstance();
                            PyObject osc_server = py.getModule("OSCserver");
                            while (start) {
                                PyObject obj = osc_server.callAttr("main", ipAddress);
                                repeat();
                            }
                            if (!ev3.isCancelled())
                                ev3.cancel();
                            forward = false;
                            backward = false;
                            left = false;
                            right = false;
                            stopped = true;
                        }
                    });
                    thread_register.start();  // wait the server shutdown
                    thread_register.interrupt();
                    read = false;

                    Log.d("Path", getApplication().getFilesDir().getAbsolutePath().toString());
                    ready.setVisibility(View.VISIBLE);
                    ready.setVisibility(View.INVISIBLE);
            });


            Button stopButton = findViewById(R.id.stopButton);
            stopButton.setOnClickListener(v -> {
                if (!ev3.isCancelled())
                    ev3.cancel();
            });


            Button startButton = findViewById(R.id.startButton);
            startButton.setOnClickListener(v ->  {
                if (!ev3.isCancelled())
                    ev3.cancel();
                Prelude.trap(() -> ev3.run(this::legoMain));
            });

            Button backButton = findViewById(R.id.backButton);
            backButton.setOnClickListener(v -> {
                if (!ev3.isCancelled())
                    ev3.cancel();
                Prelude.trap(() -> ev3.run(this::back));

            });

            Button leftButton = findViewById(R.id.leftButton);
            leftButton.setOnClickListener(v -> {
                if (!ev3.isCancelled())
                    ev3.cancel();
                Prelude.trap(() -> ev3.run(this::turnLeft));
            });


            Button rightButton = findViewById(R.id.rightButton);
            rightButton.setOnClickListener(v -> {
                if (!ev3.isCancelled())
                    ev3.cancel();
                Prelude.trap(() -> ev3.run(this::turnRight));
            });



            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    s.setProgress(i);
                    speed = i;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}

            });



        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
    }

    private void stop() {
        if (!ev3.isCancelled()) {
            ev3.cancel();
        }
    }


    private void repeat() {
            readText();
            Log.d("Read", "OK");
            if(accXY.first > 0.4 && accXY.second > -0.2 && accXY.second < 0.2) {
                if (stopped && !forward) {
                    Log.d("Forward : ", String.valueOf(forward));
                    Prelude.trap(() -> ev3.run(this::legoMain));
                    stopped = false;
                    forward = true;
                }
                else if (!forward) {
                    if (!ev3.isCancelled()) {
                        ev3.cancel();
                        index--;
                    }
                    stopped = true;
                    backward = false;
                    left = false;
                    right = false;
                }
            }
            else if( accXY.first > -0.2 && accXY.first < 0.3 && accXY.second < -0.4 ) {
                if (stopped && !left) {
                    Prelude.trap(() -> ev3.run(this::turnLeft));
                    stopped = false;
                    left = true;
                }
                else if (!left) {
                    if (!ev3.isCancelled()) {
                        ev3.cancel();
                        index--;
                    }
                    stopped = true;
                    forward = false;
                    backward = false;
                    right = false;
                }

            }
            else {
                if (accXY.first < 0.3 && accXY.first > -0.2 && accXY.second > 0.4) {
                    if (stopped && !right) {
                        Prelude.trap(() -> ev3.run(this::turnRight));
                        stopped = false;
                        right = true;
                    }
                    else if (!right) {
                        if (!ev3.isCancelled()) {
                            ev3.cancel();
                            index--;
                        }
                        stopped = true;
                        forward = false;
                        backward = false;
                        left = false;
                    }
                }
                else if (accXY.first < - 0.6 && accXY.second > -0.2 && accXY.second < 0.2) {
                    if (stopped && !backward) {
                        Prelude.trap(() -> ev3.run(this::back));
                        stopped = false;
                        backward = true;
                    }
                    else if (!backward) {
                        if (!ev3.isCancelled()) {
                            ev3.cancel();
                            index--;
                        }
                        stopped = true;
                        forward = false;
                        left = false;
                        right = false;
                    }
                }
                else {
                    if (!ev3.isCancelled())
                        ev3.cancel();
                    forward = false;
                    backward = false;
                    left = false;
                    right = false;
                    stopped = true;
                }

            }
    }


    private void readText() {
        try {
            String file = getApplication().getFilesDir().getAbsolutePath() + File.separator + "mean.csv";
            Log.d("File : ", file.toString());
            InputStream inputstream = new FileInputStream(file);
            CSVReader csv = new CSVReader(inputstream);
            List<String[]> readList = csv.read();
            for (String[] scoreData : readList) {
                Double first = Double.parseDouble(scoreData[0]);
                Double second = Double.parseDouble(scoreData[1]);
                Log.d("first & second", String.valueOf(first +","+ second));
                Pair<Double, Double> p = new Pair<Double, Double>(first, second);
                accXY = p;
            }
            Log.d("Reading", "Ok!");
        } catch (Exception e) {
            Log.d("Errore: ", "Lettura del file!");
        }
    }


    private void legoMain(EV3.Api api) {
        final String TAG = Prelude.ReTAG("legoMain");

        // get sensors
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._3);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        final TouchSensor touchSensor = api.getTouchSensor(EV3.InputPort._1);
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);

        // get motors
        motorB = api.getTachoMotor(EV3.OutputPort.B);
        motorC = api.getTachoMotor(EV3.OutputPort.C);

        try {
            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {    // loop until cancellation signal is fired
                try {
                    // values returned by getters are boxed within a Future object
                    Future<Float> gyro = gyroSensor.getAngle();
                    updateStatus(gyroSensor, "gyro angle", gyro.get()); // call get() for actually reading the value - this may block!

                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(this.speed, 0, 5000, 0, true);
                    motorC.setStepSpeed(this.speed, 0, 5000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }



    private void back(EV3.Api api) {
        final String TAG = Prelude.ReTAG("back");

        // get sensors
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._3);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        final TouchSensor touchSensor = api.getTouchSensor(EV3.InputPort._1);
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);

        // get motors
        motorB = api.getTachoMotor(EV3.OutputPort.B);
        motorC = api.getTachoMotor(EV3.OutputPort.C);

        try {

            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {    // loop until cancellation signal is fired
                try {
                    // values returned by getters are boxed within a Future object
                    Future<Float> gyro = gyroSensor.getAngle();
                    updateStatus(gyroSensor, "gyro angle", gyro.get()); // call get() for actually reading the value - this may block!

                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(-this.speed, 0, 5000, 0, true);
                    motorC.setStepSpeed(-this.speed, 0, 5000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }



    private void turnLeft(EV3.Api api) {
        final String TAG = Prelude.ReTAG("left");

        // get sensors
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._3);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        final TouchSensor touchSensor = api.getTouchSensor(EV3.InputPort._1);
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);

        // get motors
        motorC = api.getTachoMotor(EV3.OutputPort.C);

        try {
            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {    // loop until cancellation signal is fired
                try {
                    // values returned by getters are boxed within a Future object
                    Future<Float> gyro = gyroSensor.getAngle();
                    updateStatus(gyroSensor, "gyro angle", gyro.get()); // call get() for actually reading the value - this may block!

                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorC.getPosition();
                    updateStatus(motorC, "motor position", pos.get());

                    Future<Float> speed = motorC.getSpeed();
                    updateStatus(motorC, "motor speed", speed.get());

                    motorC.setStepSpeed(this.speed, 0, 5000, 0, true);

                    Log.d(TAG, "waiting for long motor operation completed...");
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }


    private void turnRight(EV3.Api api) {
        final String TAG = Prelude.ReTAG("left");

        // get sensors
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._3);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        final TouchSensor touchSensor = api.getTouchSensor(EV3.InputPort._1);
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);

        // get motors
        motorB = api.getTachoMotor(EV3.OutputPort.B);

        try {
            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {    // loop until cancellation signal is fired
                try {
                    // values returned by getters are boxed within a Future object
                    Future<Float> gyro = gyroSensor.getAngle();
                    updateStatus(gyroSensor, "gyro angle", gyro.get()); // call get() for actually reading the value - this may block!

                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(this.speed, 0, 5000, 0, true);

                    Log.d(TAG, "waiting for long motor operation completed...");
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }

}


