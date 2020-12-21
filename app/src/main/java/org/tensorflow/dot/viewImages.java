package org.tensorflow.dot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.GridLayoutAnimationController;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.ViewSwitcher.ViewFactory;

import androidx.appcompat.app.AppCompatActivity;

public class viewImages extends AppCompatActivity {
    int[] imageIds = new int[]
            {

            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.viewimages);
        //创建一个list对象，list对象的元素是map
        List<Map<String,Object>> listItems = new ArrayList<Map<String, Object>>();

        for (int i = 0; i < imageIds.length; i++) {
            Map<String, Object> listItem = new HashMap<String, Object>();
            listItem.put("image", imageIds[i]);
            listItems.add(listItem);
        }
        //获取显示图片的ImageSwitcher
        final ImageSwitcher switcher = (ImageSwitcher) findViewById(R.id.switcher);
        //设置图片更换的动画效果
        switcher.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        switcher.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
        //为ImageSwitcher设置图片切换的动画效果
        switcher.setFactory(new ViewFactory() {

            @Override
            public View makeView() {
                ImageView imageView = new ImageView(viewImages.this);
                imageView.setBackgroundColor(0xff0000);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setLayoutParams(new ImageSwitcher.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                return imageView;
            }
        });

        //创建一个SimpleAdapter
        SimpleAdapter simpleAdapter = new SimpleAdapter(this, listItems, R.layout.cell, new String[]{"image"}, new int[]{R.id.image1});

        GridView grid = (GridView) findViewById(R.id.grid01);
        //为GridView设置Adapter
        grid.setAdapter(simpleAdapter);
        //添加列表项被选中的监听器
        grid.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position , long id) {
                //显示当前被选中的图片
                switcher.setImageResource(imageIds[position % imageIds.length]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {}
        });
        //添加列表被单击的监听
        grid.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                switcher.setImageResource(imageIds[position % imageIds.length]);
            }

        });

    }
}