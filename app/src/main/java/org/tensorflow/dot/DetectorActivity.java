///*
// * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *       http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.tensorflow.dot;
//
//import android.graphics.Bitmap;
//import android.graphics.Bitmap.Config;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Matrix;
//import android.graphics.Paint;
//import android.graphics.Paint.Style;
//import android.graphics.RectF;
//import android.graphics.Typeface;
//import android.location.Location;
//import android.media.ImageReader.OnImageAvailableListener;
//import android.net.Uri;
//import android.os.SystemClock;
//import android.util.Log;
//import android.util.Size;
//import android.util.TypedValue;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationServices;
//import com.google.android.gms.tasks.OnCompleteListener;
//import com.google.android.gms.tasks.OnSuccessListener;
//import com.google.android.gms.tasks.Task;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.storage.FirebaseStorage;
//import com.google.firebase.storage.StorageReference;
//import com.google.firebase.storage.UploadTask;
//
//import org.tensorflow.dot.OverlayView.DrawCallback;
//import org.tensorflow.dot.env.BorderedText;
//import org.tensorflow.dot.env.ImageUtils;
//import org.tensorflow.dot.env.Logger;
//import org.tensorflow.dot.tracking.MultiBoxTracker;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Vector;
//
//import static android.content.ContentValues.TAG;
//
///**
// * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
// * objects.
// */
//public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
//  private static final Logger LOGGER = new Logger();
//
//  // Configuration values for the prepackaged multibox model.
//  private static final int MB_INPUT_SIZE = 224;
//  private static final int MB_IMAGE_MEAN = 128;
//  private static final float MB_IMAGE_STD = 128;
//  private static final String MB_INPUT_NAME = "ResizeBilinear";
//  private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
//  private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
//  private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
//  private static final String MB_LOCATION_FILE =
//      "file:///android_asset/multibox_location_priors.txt";
//
//  private static final int TF_OD_API_INPUT_SIZE = 300;
//  private static final String TF_OD_API_MODEL_FILE =
//      "file:///android_asset/ssd_mobilenet_RoadDamageDetector.pb";
//  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mcr_crack_label.txt";
//
//  // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
//  // must be manually placed in the assets/ directory by the user.
//  // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
//  // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
//  // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
//  private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
//  private static final int YOLO_INPUT_SIZE = 416;
//  private static final String YOLO_INPUT_NAME = "input";
//  private static final String YOLO_OUTPUT_NAMES = "output";
//  private static final int YOLO_BLOCK_SIZE = 32;
//
//  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
//  // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
//  // or YOLO.
//  private enum DetectorMode {
//    TF_OD_API, MULTIBOX, YOLO;
//  }
//  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
//
//  // Minimum detection confidence to track a detection.
//  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
//  private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
//  private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;
//
//  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;
//
//  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
//
//  private static final boolean SAVE_PREVIEW_BITMAP = false;
//  private static final float TEXT_SIZE_DIP = 10;
//
//  private Integer sensorOrientation;
//
//  private Classifier detector;
//
//  private long lastProcessingTimeMs;
//  private Bitmap rgbFrameBitmap = null;
//  private Bitmap croppedBitmap = null;
//  private Bitmap cropCopyBitmap = null;
//
//  private boolean computingDetection = false;
//
//  private long timestamp = 0;
//
//  private Matrix frameToCropTransform;
//  private Matrix cropToFrameTransform;
//
//  private MultiBoxTracker tracker;
//
//  private byte[] luminanceCopy;
//
//  private BorderedText borderedText;
//
////parameter
//  HashMap<String, Float> map = new HashMap<>();
//  HashMap<String, String> show = new HashMap<>();
//  HashMap<byte[], HashMap<String,String>> dataMap = new HashMap<>();
//
//  private FusedLocationProviderClient mFusedLocationClient;
//
//  private float longtitude;
//  private float latitude;
//
//
//
//
//  private void getLastLocation() {
//    mFusedLocationClient.getLastLocation()
//            .addOnCompleteListener(this, new OnCompleteListener<Location>() {
//              @Override
//              public void onComplete(@NonNull Task<Location> task) {
//                if (task.isSuccessful() && task.getResult() != null) {
//                  mLastLocation = task.getResult();
//
//                  latitude = (float)mLastLocation.getLatitude();
//                  longtitude = (float)mLastLocation.getLongitude();
//                } else {
//                  Log.w(TAG, "getLastLocation:exception", task.getException());
//                }
//              }
//            });
//  }
//
//
//
//  @Override
//  public void onPreviewSizeChosen(final Size size, final int rotation) {
//    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//    final float textSizePx =
//        TypedValue.applyDimension(
//            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
//    borderedText = new BorderedText(textSizePx);
//    borderedText.setTypeface(Typeface.MONOSPACE);
//
//    tracker = new MultiBoxTracker(this);
//
//    int cropSize = TF_OD_API_INPUT_SIZE;
//    if (MODE == DetectorMode.YOLO) {
//      detector =
//          TensorFlowYoloDetector.create(
//              getAssets(),
//              YOLO_MODEL_FILE,
//              YOLO_INPUT_SIZE,
//              YOLO_INPUT_NAME,
//              YOLO_OUTPUT_NAMES,
//              YOLO_BLOCK_SIZE);
//      cropSize = YOLO_INPUT_SIZE;
//    } else if (MODE == DetectorMode.MULTIBOX) {
//      detector =
//          TensorFlowMultiBoxDetector.create(
//              getAssets(),
//              MB_MODEL_FILE,
//              MB_LOCATION_FILE,
//              MB_IMAGE_MEAN,
//              MB_IMAGE_STD,
//              MB_INPUT_NAME,
//              MB_OUTPUT_LOCATIONS_NAME,
//              MB_OUTPUT_SCORES_NAME);
//      cropSize = MB_INPUT_SIZE;
//    } else {
//      try {
//        detector = TensorFlowObjectDetectionAPIModel.create(
//            getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
//        cropSize = TF_OD_API_INPUT_SIZE;
//      } catch (final IOException e) {
//        LOGGER.e(e, "Exception initializing classifier!");
//        Toast toast =
//            Toast.makeText(
//                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
//        toast.show();
//        finish();
//      }
//    }
//
//    previewWidth = size.getWidth();
//    previewHeight = size.getHeight();
//
//    sensorOrientation = rotation - getScreenOrientation();
//    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
//
//    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
//    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
//    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);
//
//    frameToCropTransform =
//        ImageUtils.getTransformationMatrix(
//            previewWidth, previewHeight,
//            cropSize, cropSize,
//            sensorOrientation, MAINTAIN_ASPECT);
//
//    cropToFrameTransform = new Matrix();
//    frameToCropTransform.invert(cropToFrameTransform);
//
//    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
//    trackingOverlay.addCallback(
//        new DrawCallback() {
//          @Override
//          public void drawCallback(final Canvas canvas) {
//            tracker.draw(canvas);
//            if (isDebug()) {
//              tracker.drawDebug(canvas);
//            }
//          }
//        });
//
//    addCallback(
//        new DrawCallback() {
//          @Override
//          public void drawCallback(final Canvas canvas) {
//            if (!isDebug()) {
//              return;
//            }
//            final Bitmap copy = cropCopyBitmap;
//            if (copy == null) {
//              return;
//            }
//
//            final int backgroundColor = Color.argb(100, 0, 0, 0);
//            canvas.drawColor(backgroundColor);
//
//            final Matrix matrix = new Matrix();
//            final float scaleFactor = 2;
//            matrix.postScale(scaleFactor, scaleFactor);
//            matrix.postTranslate(
//                canvas.getWidth() - copy.getWidth() * scaleFactor,
//                canvas.getHeight() - copy.getHeight() * scaleFactor);
//            canvas.drawBitmap(copy, matrix, new Paint());
//
//            final Vector<String> lines = new Vector<String>();
//            if (detector != null) {
//              final String statString = detector.getStatString();
//              final String[] statLines = statString.split("\n");
//              for (final String line : statLines) {
//                lines.add(line);
//              }
//            }
//            lines.add("");
//
//            lines.add("Frame: " + previewWidth + "x" + previewHeight);
//            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
//            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
//            lines.add("Rotation: " + sensorOrientation);
//            lines.add("Inference time: " + lastProcessingTimeMs + "ms");
//
//            borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
//          }
//        });
//  }
//
//  OverlayView trackingOverlay;
//
//  @Override
//  protected HashMap<byte[] ,HashMap<String, String>> processImage() {
//
//    dataMap = new HashMap<>();
//
//    ++timestamp;
//    final long currTimestamp = timestamp;
//    byte[] originalLuminance = getLuminance();
//    tracker.onFrame(
//        previewWidth,
//        previewHeight,
//        getLuminanceStride(),
//        sensorOrientation,
//        originalLuminance,
//        timestamp);
//    trackingOverlay.postInvalidate();
//
//    // No mutex needed as this method is not reentrant.
//    if (computingDetection) {
//      return dataMap;
//    }
//    computingDetection = true;
//    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
//
//    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
//
//    if (luminanceCopy == null) {
//      luminanceCopy = new byte[originalLuminance.length];
//    }
//    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
//    readyForNextImage();
//
//    final Canvas canvas = new Canvas(croppedBitmap);
//    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
//    // For examining the actual TF input.
//    if (SAVE_PREVIEW_BITMAP) {
//      ImageUtils.saveBitmap(croppedBitmap);
//    }
//
//
//    runInBackground(
//        new Runnable() {
//          @Override
//          public void run() {
//            LOGGER.i("Running detection on image " + currTimestamp);
//            final long startTime = SystemClock.uptimeMillis();
//            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
//            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
//
//            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
//            final Canvas canvas = new Canvas(cropCopyBitmap);
//            final Paint paint = new Paint();
//            paint.setColor(Color.RED);
//            paint.setStyle(Style.STROKE);
//            paint.setStrokeWidth(2.0f);
//
//            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//            switch (MODE) {
//              case TF_OD_API:
//                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//                break;
//              case MULTIBOX:
//                minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
//                break;
//              case YOLO:
//                minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
//                break;
//            }
//            final List<Classifier.Recognition> mappedRecognitions =
//                new LinkedList<Classifier.Recognition>();
//
//            for (final Classifier.Recognition result : results) {
//              final RectF location = result.getLocation();
//              if (location != null && result.getConfidence() >= minimumConfidence) {
//                canvas.drawRect(location, paint);
//
//                cropToFrameTransform.mapRect(location);
//                result.setLocation(location);
//                mappedRecognitions.add(result);
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
//                byte[] data = baos.toByteArray();
//                getLastLocation();
////                show.put("longitude",longtitude+"");
////                show.put("latitude",latitude+"");
////                show.put("confidence", result.getConfidence()+"");
////                show.put("type",result.getTitle());
////                dataMap.put(data, show);
//
//                getUrl(data,longtitude,latitude,result.getConfidence(),result.getTitle());
//              }
//            }
//
//            tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
//            trackingOverlay.postInvalidate();
//
//            requestRender();
////            readyForNextImage();
//            computingDetection = false;
//          }
//        });
//    return dataMap;
//  }
//
//
//  private void getUrl(byte[] img, final float longtitude, final float latitude, final float confidence, final String type){
//    StorageReference storageRef = FirebaseStorage.getInstance().getReference();
//    final DatabaseReference spotRef = FirebaseDatabase.getInstance().getReference("spot");
//    final String reportId = spotRef.push().getKey();
//    final StorageReference imgRef = storageRef.child("spot").child(reportId+".jpeg");
//    UploadTask uploadTask = imgRef.putBytes(img);
//    uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
//      @Override
//      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//        imgRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
//          @Override
//          public void onSuccess(Uri uri) {
//            Log.d(TAG, "onSuccess: uri= "+ uri.toString());
//
//            Spot spot = new Spot(longtitude,latitude,confidence,uri.toString(),type);
//            spotRef.child(reportId).setValue(spot);
//
//          }
//        });
//      }
//    });
//  }
//
//
//  @Override
//  protected int getLayoutId() {
//    return R.layout.camera_connection_fragment_tracking;
//  }
//
//  @Override
//  protected Size getDesiredPreviewFrameSize() {
//    return DESIRED_PREVIEW_SIZE;
//  }
//
//  @Override
//  public void onSetDebug(final boolean debug) {
//    detector.enableStatLogging(debug);
//  }
//}


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

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
<<<<<<< HEAD
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
=======
>>>>>>> model
import android.location.Location;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.tensorflow.dot.OverlayView.DrawCallback;
import org.tensorflow.dot.env.BorderedText;
import org.tensorflow.dot.env.ImageUtils;
import org.tensorflow.dot.env.Logger;
import org.tensorflow.dot.tracking.MultiBoxTracker;
import static android.content.ContentValues.TAG;
import org.tensorflow.dot.R; // Explicit import needed for internal Google builds.

<<<<<<< HEAD

import java.util.Stack;



=======
>>>>>>> model
/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
<<<<<<< HEAD
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, SensorEventListener {

  class SizedStack<T> extends Stack<T> {
    private int maxSize;

    public SizedStack(int size) {
      super();
      this.maxSize = size;
    }

    @Override
    public T push(T object) {
      //If the stack is too big, remove elements until it's the right size.
      while (this.size() >= maxSize) {
        this.remove(0);
      }
      return super.push(object);
    }
  }

  Stack<Double[]> acclerometerData = new SizedStack<Double[]>(30);

=======
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
>>>>>>> model
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged multibox model.
  private static final int MB_INPUT_SIZE = 224;
  private static final int MB_IMAGE_MEAN = 128;
  private static final float MB_IMAGE_STD = 128;
  private static final String MB_INPUT_NAME = "ResizeBilinear";
  private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
  private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
  private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
  private static final String MB_LOCATION_FILE =
          "file:///android_asset/multibox_location_priors.txt";

  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final String TF_OD_API_MODEL_FILE =
      "file:///android_asset/ssd_mobilenet_RoadDamageDetector.pb";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mcr_crack_label.txt";

  // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
  // must be manually placed in the assets/ directory by the user.
  // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
  // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
  // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
  private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
  private static final int YOLO_INPUT_SIZE = 416;
  private static final String YOLO_INPUT_NAME = "input";
  private static final String YOLO_OUTPUT_NAMES = "output";
  private static final int YOLO_BLOCK_SIZE = 32;

<<<<<<< HEAD

=======
>>>>>>> model
  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
  // or YOLO.
  private enum DetectorMode {
<<<<<<< HEAD
    TF_OD_API, MULTIBOX, YOLO
=======
    TF_OD_API, MULTIBOX, YOLO;
>>>>>>> model
  }
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
  private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
  private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

<<<<<<< HEAD
  private SensorManager sensorManager;
  private static double x = 0;
  private static double y = 0;
  private static double z = 0;


=======
>>>>>>> model
  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;

  //parameter
  HashMap<String, Float> map = new HashMap<>();
  HashMap<String, String> show = new HashMap<>();
  HashMap<byte[], HashMap<String,String>> dataMap = new HashMap<>();

  private FusedLocationProviderClient mFusedLocationClient;

  private float longtitude;
  private float latitude;

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
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

<<<<<<< HEAD
    sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);
    sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);


=======
>>>>>>> model
    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;
    if (MODE == DetectorMode.YOLO) {
      detector =
              TensorFlowYoloDetector.create(
                      getAssets(),
                      YOLO_MODEL_FILE,
                      YOLO_INPUT_SIZE,
                      YOLO_INPUT_NAME,
                      YOLO_OUTPUT_NAMES,
                      YOLO_BLOCK_SIZE);
      cropSize = YOLO_INPUT_SIZE;
    } else if (MODE == DetectorMode.MULTIBOX) {
      detector =
              TensorFlowMultiBoxDetector.create(
                      getAssets(),
                      MB_MODEL_FILE,
                      MB_LOCATION_FILE,
                      MB_IMAGE_MEAN,
                      MB_IMAGE_STD,
                      MB_INPUT_NAME,
                      MB_OUTPUT_LOCATIONS_NAME,
                      MB_OUTPUT_SCORES_NAME);
      cropSize = MB_INPUT_SIZE;
    } else {
      try {
        detector = TensorFlowObjectDetectionAPIModel.create(
                getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        cropSize = TF_OD_API_INPUT_SIZE;
      } catch (final IOException e) {
        LOGGER.e(e, "Exception initializing classifier!");
        Toast toast =
                Toast.makeText(
                        getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
        toast.show();
        finish();
      }
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropSize, cropSize,
                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

<<<<<<< HEAD
    trackingOverlay = findViewById(R.id.tracking_overlay);
=======
    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
>>>>>>> model
    trackingOverlay.addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              }
            });

    addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                if (!isDebug()) {
                  return;
                }
                final Bitmap copy = cropCopyBitmap;
                if (copy == null) {
                  return;
                }

                final int backgroundColor = Color.argb(100, 0, 0, 0);
                canvas.drawColor(backgroundColor);

                final Matrix matrix = new Matrix();
                final float scaleFactor = 2;
                matrix.postScale(scaleFactor, scaleFactor);
                matrix.postTranslate(
                        canvas.getWidth() - copy.getWidth() * scaleFactor,
                        canvas.getHeight() - copy.getHeight() * scaleFactor);
                canvas.drawBitmap(copy, matrix, new Paint());

                final Vector<String> lines = new Vector<String>();
                if (detector != null) {
                  final String statString = detector.getStatString();
                  final String[] statLines = statString.split("\n");
                  for (final String line : statLines) {
                    lines.add(line);
                  }
                }
                lines.add("");

                lines.add("Frame: " + previewWidth + "x" + previewHeight);
                lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                lines.add("Rotation: " + sensorOrientation);
                lines.add("Inference time: " + lastProcessingTimeMs + "ms");

                borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
              }
            });
  }

  OverlayView trackingOverlay;

  @Override
  protected HashMap<byte[] ,HashMap<String, String>> processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
            previewWidth,
            previewHeight,
            getLuminanceStride(),
            sensorOrientation,
            originalLuminance,
            timestamp);
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();

      return dataMap;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

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
                LOGGER.i("Running detection on image " + currTimestamp);
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                  case TF_OD_API:
                    minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    break;
                  case MULTIBOX:
                    minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                    break;
                  case YOLO:
                    minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                    break;
                }

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                  final RectF location = result.getLocation();
                  if (location != null && result.getConfidence() >= minimumConfidence) {
                    canvas.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                    byte[] data = baos.toByteArray();
                    getLastLocation();
<<<<<<< HEAD


=======
>>>>>>> model
    //                show.put("longitude",longtitude+"");
    //                show.put("latitude",latitude+"");
    //                show.put("confidence", result.getConfidence()+"");
    //                show.put("type",result.getTitle());
    //                dataMap.put(data, show);
<<<<<<< HEAD
                    Log.d("XYZ","XYZ here?");
                    Log.d("stack", acclerometerData.get(0) + " " + acclerometerData.get(1) + " " + acclerometerData.get(0));
=======
>>>>>>> model

                    getUrl(data,longtitude,latitude,result.getConfidence(),result.getTitle());
                  }
                }

                tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                trackingOverlay.postInvalidate();

                requestRender();
                computingDetection = false;
              }
            });
    return dataMap;
  }


    private void getUrl(byte[] img, final float longtitude, final float latitude, final float confidence, final String type){
    StorageReference storageRef = FirebaseStorage.getInstance().getReference();
    final DatabaseReference spotRef = FirebaseDatabase.getInstance().getReference("spot");
    final String reportId = spotRef.push().getKey();
    final StorageReference imgRef = storageRef.child("spot").child(reportId+".jpeg");
    UploadTask uploadTask = imgRef.putBytes(img);
    uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
        imgRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
          @Override
          public void onSuccess(Uri uri) {
            Log.d(TAG, "onSuccess: uri= "+ uri.toString());

            Spot spot = new Spot(longtitude,latitude,confidence,uri.toString(),type);
            spotRef.child(reportId).setValue(spot);

<<<<<<< HEAD
            //TODO send the x,y,z and confidence level to backend

=======
>>>>>>> model
          }
        });
      }
    });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
<<<<<<< HEAD

  @Override
  public void onAccuracyChanged(Sensor arg0, int arg1) {
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
      x=event.values[0];
      y=event.values[1];
      z=event.values[2];
      acclerometerData.push(new Double[]{x,y,z});
    }

  }
=======
>>>>>>> model
}
