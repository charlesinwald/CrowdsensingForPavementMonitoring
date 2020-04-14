package org.tensorflow.dot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainPage extends AppCompatActivity {

    private Button button1, button2, button3, button4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainpage);
        button1 = (Button) findViewById(R.id.carBtn);
//        button2 = (Button) findViewById(R.id.personBtn);
        button3 = (Button) findViewById(R.id.contactBtn);
        button4 = (Button) findViewById(R.id.privacyBtn);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainPage.this, DetectorActivity.class);
                startActivity(intent);
            }
        });

//        button2.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent();
//                intent.setClass(MainPage.this, Profile.class);
//                startActivity(intent);
//            }
//        });

        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainPage.this, Contact.class);
                startActivity(intent);
            }
        });

        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainPage.this, Privacy.class);
                startActivity(intent);
            }
        });
    }

}