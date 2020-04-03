/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.dot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.tensorflow.dot.OverlayView.DrawCallback;
import org.tensorflow.dot.env.BorderedText;
import org.tensorflow.dot.env.ImageUtils;
import org.tensorflow.dot.env.Logger;
import org.tensorflow.dot.R; // Explicit import needed for internal Google builds.
import org.tensorflow.dot.Classifier.Recognition;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.ContentValues.TAG;


public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    protected static final boolean SAVE_PREVIEW_BITMAP = false;

    private static ArrayList<Location> locations = new ArrayList<>();

    public static FusedLocationProviderClient fusedLocationClient;


    private ResultsView resultsView;

    private Bitmap rgbFrameBitmap = null;
    public Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private long lastProcessingTimeMs;

    private GoogleApiClient mGoogleApiClient;

    // These are the settings for the original v1 Inception model. If you want to
    // use a model that's been produced from the TensorFlow for Poets codelab,
    // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
    // INPUT_NAME = "Mul", and OUTPUT_NAME = "final_result".
    // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
    // the ones you produced.
    //
    // To use v3 Inception model, strip the DecodeJpeg Op from your retrained
    // model first:
    //
    // python strip_unused.py \
    // --input_graph=<retrained-pb-file> \
    // --output_graph=<your-stripped-pb-file> \
    // --input_node_names="Mul" \
    // --output_node_names="final_result" \
    // --input_binary=true
    private static final int INPUT_SIZE = 299;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128;
    private static final String INPUT_NAME = "Placeholder";
    private static final String OUTPUT_NAME = "final_result";

//    private static final int INPUT_SIZE = 299;
//    private static final int IMAGE_MEAN = 128;
//    private static final float IMAGE_STD = 128;
//    private static final String INPUT_NAME = "Mul";
//    private static final String OUTPUT_NAME = "final_result";


//    private static final String MODEL_FILE = "file:///android_asset/output_android.pb";
//    private static final String LABEL_FILE =
//            "file:///android_asset/output_labels.txt";


    private static final String MODEL_FILE = "file:///android_asset/output.pb";
    private static final String LABEL_FILE =
            "file:///android_asset/output_labels2.txt";


    private static final boolean MAINTAIN_ASPECT = true;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);


    private Integer sensorOrientation;
    private Classifier classifier;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    HashMap<String, Float> map = new HashMap<>();
    HashMap<String, Float> show = new HashMap<>();
    HashMap<byte[], HashMap<String,Float>> dataMap = new HashMap<>();

    private FusedLocationProviderClient mFusedLocationClient;

    private float longtitude;
    private float latitude;

    private BorderedText borderedText;


    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    private static final float TEXT_SIZE_DIP = 10;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        final float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        classifier =
                TensorFlowImageClassifier.create(
                        getAssets(),
                        MODEL_FILE,
                        LABEL_FILE,
                        INPUT_SIZE,
                        IMAGE_MEAN,
                        IMAGE_STD,
                        INPUT_NAME,
                        OUTPUT_NAME);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

        sensorOrientation = rotation + screenOrientation;

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                INPUT_SIZE, INPUT_SIZE,
                sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });
    }

//    protected void getLocation() {
//        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
//
//        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
//            //Execute location service call if user has explicitly granted ACCESS_FINE_LOCATION..
//            fusedLocationClient.getLastLocation()
//                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
//                        @Override
//                        public void onSuccess(Location location) {
//                            // Got last known location. In some rare situations this can be null.
//                            if (location != null) {
//                                // Logic to handle location object
//                                locations.add(location);
//                                runInBackground(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Log.d("POST","About to send post request");
////                                        OkHttpClient client = new OkHttpClient();
//                                        MediaType mediaType = MediaType.parse("application/json");
//                                        String lat = String.valueOf(locations.get(locations.size() - 1).getLatitude());
//                                        String long2 = String.valueOf(locations.get(locations.size() - 1).getLongitude());
////                                        RequestBody body = RequestBody.create(mediaType, "{\n  \"lat\": " + lat + ",\n  \"long\": " + long2 + "\n}");
////                                        Request request = new Request.Builder()
////                                                .url("http://root@vps263488.vps.ovh.ca:4000/potholes?lat=" + lat + "&long=" + long2)
////                                                .post(body)
////                                                .addHeader("Content-Type", "application/json")
////                                                .addHeader("cache-control", "no-cache")
////                                                .addHeader("Postman-Token", "a52a8869-4573-49dd-9937-6f77208f2ebd")
////                                                .build();
//
////                                        try {
////                                            Response response = client.newCall(request).execute();
////                                        } catch (IOException e) {
////                                            e.printStackTrace();
////                                        }
//                                    }
//
//                                });
//                            }
//                        }
//                    });
//        }
//    }

    private void getLastLocation() {
        mFusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            mLastLocation = task.getResult();

                                    latitude = (float)mLastLocation.getLatitude();
                                    longtitude = (float)mLastLocation.getLongitude();
                        } else {
                            Log.w(TAG, "getLastLocation:exception", task.getException());
                        }
                    }
                });
    }

    @Override
    protected HashMap<byte[] ,HashMap<String, Float>> processImage() {

//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);



        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);

                        Recognition result = results.get(0);


//                        if(map.containsKey("confidence")){
//                            map.put("confidence", Math.max(map.get("confidence"),result.getConfidence()));
//                        }else{
//                            map.put("confidence",result.getConfidence());
//                        }

                        getLastLocation();

//                        map.put("longitude",longtitude);
//                        map.put("latitude",latitude);

//                        if(result.getConfidence()>0.9 && !(latitude ==0.0 && longtitude==0.0 )){
                        if(result.getConfidence()>1.0){
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            byte[] data = baos.toByteArray();

                            show.put("longitude",longtitude);
                            show.put("latitude",latitude);
                            show.put("confidence", result.getConfidence());
                            dataMap.put(data, show);

                        }

                        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

                        sensorManager.registerListener(new SensorEventListener() {
                                                           @Override
                                                           public void onSensorChanged(SensorEvent event) {
                                                               float x = event.values[0];
                                                               float y = event.values[1];
                                                               float z = event.values[2];
                                                               System.out.println(x);
                                                               System.out.println(y);
                                                               System.out.println(z);
                                                           }
                                                               @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                            }

                        }, sensor, SensorManager.SENSOR_DELAY_FASTEST);


//                                if(show.containsKey("confidence")){
//                                    show.put("x", x);
//                                    show.put("y", y);
//                                    show.put("z", z);
//                                }
//
//
////                                if(map.containsKey("xmax")){
////                                    map.put("xmax", Math.max(map.get("xmax"),x));
////                                }else{
////                                    map.put("xmax",x);
////                                }
////
////                                if(map.containsKey("ymax")){
////                                    map.put("ymax", Math.max(map.get("ymax"),y));
////                                }else{
////                                    map.put("ymax",x);
////                                }
////
////                                if(map.containsKey("zmax")){
////                                    map.put("zmax", Math.max(map.get("zmax"),z));
////                                }else{
////                                    map.put("zmax",x);
////                                }
////
////                                if(map.containsKey("xmin")){
////                                    map.put("xmin", Math.min(map.get("xmin"),x));
////                                }else{
////                                    map.put("xmin",x);
////                                }
////
////                                if(map.containsKey("ymin")){
////                                    map.put("ymin", Math.min(map.get("ymin"),y));
////                                }else{
////                                    map.put("ymin",x);
////                                }
////
////                                if(map.containsKey("zmin")){
////                                    map.put("zmin", Math.min(map.get("zmin"),z));
////                                }else{
////                                    map.put("zmin",x);
////                                }
//                            }
//
//                            @Override
//                            public void onAccuracyChanged(Sensor sensor, int accuracy) {
//                            }
//
//                        }, sensor, SensorManager.SENSOR_DELAY_FASTEST);


//                        if (result.getId().equals(String.valueOf(1))) {
////                            Log.d("test", "We have a pothole sarge");
////                            getLocation();
//
//                        }

//                        Log.d("test", results.get(0).getId());
//                        if (results.get(0).getId().equals(String.valueOf(3))) {
//                            Log.d("test", "We have a pothole sarge");
//                            getLocation();
//                        }
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        LOGGER.i("Detect: %s", results);
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        if (resultsView == null) {
                            resultsView = (ResultsView) findViewById(R.id.results);
                        }
                        resultsView.setResults(result);
                        requestRender();
                        readyForNextImage();
                    }
        });


//        return map;
        return dataMap;
}

    @Override
    public void onSetDebug ( boolean debug){
        classifier.enableStatLogging(debug);
    }

    private void renderDebug ( final Canvas canvas){
        if (!isDebug()) {
            return;
        }
        final Bitmap copy = cropCopyBitmap;
        if (copy != null) {
            final Matrix matrix = new Matrix();
                final float scaleFactor = 2;
                matrix.postScale(scaleFactor, scaleFactor);
                matrix.postTranslate(
                        canvas.getWidth() - copy.getWidth() * scaleFactor,
                        canvas.getHeight() - copy.getHeight() * scaleFactor);
                canvas.drawBitmap(copy, matrix, new Paint());

                final Vector<String> lines = new Vector<String>();
                if (classifier != null) {
                    String statString = classifier.getStatString();
                    String[] statLines = statString.split("\n");
                    for (String line : statLines) {
                        lines.add(line);
                    }
                }

                lines.add("Frame: " + previewWidth + "x" + previewHeight);
                lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                lines.add("Rotation: " + sensorOrientation);
                lines.add("Inference time: " + lastProcessingTimeMs + "ms");

                borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
            }
    }
}
