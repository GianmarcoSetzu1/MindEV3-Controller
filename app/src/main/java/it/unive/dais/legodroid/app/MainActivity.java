package it.unive.dais.legodroid.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
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
    private List<Pair<Double, Double>> accXY = null;//= new ArrayList<Pair<Double, Double>>();
    private long startTime = System.currentTimeMillis();
    int index = 0;

    private EV3 ev3;
    Thread thread_repeat;
    Thread thread_register;

    private void updateStatus(@NonNull Plug p, String key, Object value) {
        Log.d(TAG, String.format("%s: %s: %s", p, key, value));
        statusMap.put(key, value);
        runOnUiThread(() -> textView.setText(statusMap.toString()));
    }

    private void setupEditable(@IdRes int id, Consumer<Integer> f) {
        EditText e = findViewById(id);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

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


    // example of custom API
    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() { /* do something special */ }
    }

    // quick wrapper for accessing the private field MainActivity.motor only when not-null; also ignores any exception thrown
    private void applyMotor(@NonNull ThrowingConsumer<TachoMotor, Throwable> f) {
        //if (motor != null)
        //    Prelude.trap(() -> f.call(motor));
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
                thread_register = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Ascolto...", "OK");
                        if (!Python.isStarted()) {
                            Python.start(new AndroidPlatform(getApplicationContext()));//context));
                        }
                        Python py = Python.getInstance();
                        PyObject osc_server = py.getModule("OSCserver");//.get("__name__");//.call();
                        PyObject obj = osc_server.callAttr("main", ipAddress);
                        accXY = null;
                        accXY = new ArrayList<Pair<Double, Double>>();
                        readText();

                    }
                });
                thread_register.start();  // wait the server shutdown
                thread_register.interrupt();
                read = false;


                //double[][] data = obj.toJava(double[][].class);
                /*

                    The method main doesn't return anything!
                    The array resulting may be returned by maker handler, but is not the main function

                    Try to write a csv file and read it from MainActivity

                 */

                //listenMind.setText("OK!");
                //readText();
                Log.d("Path", getApplication().getFilesDir().getAbsolutePath().toString());
                ready.setVisibility(View.VISIBLE);

                //listenMind.setEnabled(false);


            });

            /*
                These two rows must return upper the code
             */




            /* data must contain the average of all the stream of Muse */
            /* The resulting content must be related to the acc_x and acc_y like mean_xy*/



            //Try importing the file into the app

      /*      if (!read) {
                accXY = null;
                accXY = new ArrayList<Pair<Double, Double>>();
                readText();
                read = true;
            }

     */

            //read csv each 0.5 second





            Button stopButton = findViewById(R.id.stopButton);

            stopButton.setOnClickListener(v -> {
                thread_repeat = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        repeat();
                    }
                });

                thread_repeat.start();
                ready.setVisibility(View.INVISIBLE);
                //listenMind.setEnabled(true);
                //listenMind.setText("LISTEN MINDMONITOR");

                //ev3.cancel();   // fire cancellation signal to the EV3 task

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
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });



            // alternatively with GenEV3
//          startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, MyCustomApi::new)));

            /*setupEditable(R.id.powerEdit, (x) -> applyMotor((m) -> {
                m.setPower(x);   //m.setSpeed(x);
                m.start();      // setPower() and setSpeed() require call to start() afterwards
            }));*/





        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
    }


    private void repeat() {
        while(index < accXY.size()) {
            long n = new Date().getTime();
            long elapsedTime = n - startTime;
            if (elapsedTime >= 850) {   //500
                if(accXY.get(index).first > 0 && accXY.get(index).second > 0) {
                    if (stopped && !forward) {
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
                else if(accXY.get(index).first < 0 && accXY.get(index).second < -0.5 ) {
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
                    if (accXY.get(index).second > 0.3) {
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
                    else if (accXY.get(index).first < - 0.6) {
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
                    }

                }
                Log.d("Time :" , startTime + ", " + n);
                startTime = n;
                index ++;
            }
        }
        index = 0;
        if (!ev3.isCancelled())
            ev3.cancel();
        forward = false;
        backward = false;
        left = false;
        right = false;
        stopped = true;
        thread_repeat.interrupt();
        thread_repeat = null;
    }


    private void readText() {
        try {
            String file = getApplication().getFilesDir().getAbsolutePath() + File.separator + "mean.csv";

            //File file = new File (getExternalFilesDir(null), "mean_xy1.csv");
            Log.d("File : ", file.toString());
            InputStream inputstream = new FileInputStream(file);
            CSVReader csv = new CSVReader(inputstream);
            List<String[]> readList = csv.read();
//            int i=0;
            for (String[] scoreData : readList) {
//                if (i>0) {
                    Double first = Double.parseDouble(scoreData[0]);
                    Double second = Double.parseDouble(scoreData[1]);
                    Pair<Double, Double> p = new Pair<Double, Double>(first, second);
                    accXY.add(p);
//                }
//                i++;
            }
//            Log.d("First line : ", String.valueOf(accXY.get(150).first) + ", " + String.valueOf(accXY.get(150).second));
            Log.d("Reading", "Ok!");

        } catch (Exception e) {
            Log.d("Errore: ", "Lettura del file!");
        }
    }


    private void readMuseData() {
        InputStream inputStream = getResources().openRawResource(R.raw.mean_xy1);
        CSVReader csv = new CSVReader(inputStream);
        List<String[]> readList = csv.read();
        int i=0;
        for (String[] scoreData : readList) {
            if (i>0) {
                Double first = Double.parseDouble(scoreData[0]);
                Double second = Double.parseDouble(scoreData[1]);
                Pair<Double, Double> p = new Pair<Double, Double>(first, second);
                accXY.add(p);
            }
            i++;
        }
        Log.d("First line : ", String.valueOf(accXY.get(150).first) + ", " + String.valueOf(accXY.get(150).second));
        Log.d("Reading", "Ok!");
    }



    // main program executed by EV3

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
                    // when you need to deal with the UI, you must do it via runOnUiThread()
                    //runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    /*
                    Future<Float> pos = motor.getPosition();
                    updateStatus(motor, "motor position", pos.get());

                    Future<Float> speed = motor.getSpeed();
                    updateStatus(motor, "motor speed", speed.get());

                    motor.setStepSpeed(20, 0, 5000, 0, true);
                    motor.waitCompletion();
                    motor.setStepSpeed(-20, 0, 5000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    motor.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");   */
                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(this.speed, 0, 5000, 0, true);
                    motorC.setStepSpeed(this.speed, 0, 5000, 0, true);
                    //motorB.waitCompletion();
                    //motorB.setStepSpeed(-20, 0, 5000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    //motorB.waitUntilReady();
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
                    // when you need to deal with the UI, you must do it via runOnUiThread()
                    //runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(-this.speed, 0, 5000, 0, true);
                    motorC.setStepSpeed(-this.speed, 0, 5000, 0, true);
                    //motorB.waitCompletion();
                    //motorB.setStepSpeed(-20, 0, 5000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    //motorB.waitUntilReady();
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
        //motorC = api.getTachoMotor(EV3.OutputPort.C);

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
                    // when you need to deal with the UI, you must do it via runOnUiThread()
                    //runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorC.getPosition();
                    updateStatus(motorC, "motor position", pos.get());

                    Future<Float> speed = motorC.getSpeed();
                    updateStatus(motorC, "motor speed", speed.get());

                    motorC.setStepSpeed(this.speed, 0, 5000, 0, true);

                    Log.d(TAG, "waiting for long motor operation completed...");
                    //motorB.waitUntilReady();
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
        //motorC = api.getTachoMotor(EV3.OutputPort.C);

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
                    // when you need to deal with the UI, you must do it via runOnUiThread()
                    //runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));

                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);

                    Future<Float> pos = motorB.getPosition();
                    updateStatus(motorB, "motor position", pos.get());

                    Future<Float> speed = motorB.getSpeed();
                    updateStatus(motorB, "motor speed", speed.get());

                    motorB.setStepSpeed(this.speed, 0, 5000, 0, true);

                    Log.d(TAG, "waiting for long motor operation completed...");
                    //motorB.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }





    // alternative version of the lego main with a custom API
    private void legoMainCustomApi(MyCustomApi api) {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        // specialized methods can be safely called
        api.mySpecialCommand();
        // stub the other main
        legoMain(api);
    }


}


