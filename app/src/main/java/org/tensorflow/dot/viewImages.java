package org.tensorflow.dot;

import android.os.Bundle;
import android.util.Log;
import android.widget.GridView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class viewImages extends AppCompatActivity {


//    public File files[] = imageDir.listFiles();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File imageDir = getExternalFilesDir("uploaded images");
//        if (!imageDir.exists()) {
//            imageDir.mkdir();
//            Log.i("testresult", "ImageDir Create");
//        }
        Log.i("testresult", imageDir.toString());
        File[] fileUrls = imageDir.listFiles();

        setContentView(R.layout.activity_usage_example_gridview);
        GridView gridView = (GridView) findViewById(R.id.usage_example_gridview);

        gridView.setAdapter(
                new SimpleImageListAdapter(
                        viewImages.this,
                        fileUrls
                )
        );
    }
}