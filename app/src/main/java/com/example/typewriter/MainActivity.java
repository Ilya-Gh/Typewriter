package com.example.typewriter;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.example.typewrite.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new RecyclerViewFragment())
                    .commit();
        }
    }
}
