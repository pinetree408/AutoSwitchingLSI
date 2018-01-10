package com.pinetree408.research.watchtapboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends WearableActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    static final int REQUEST_CODE_FILE = 1;

    TextView userNumView;
    int userNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setAmbientEnabled();
        checkPermission();

        userNum = 0;
        userNumView = (TextView) findViewById(R.id.user_number);
        initTaskSettings();
    }

    public void initTaskSettings() {
        final ViewGroup userSelectorView = (ViewGroup) findViewById(R.id.user_selector);
        for (int i = 0; i < userSelectorView.getChildCount(); i++) {
            userSelectorView.getChildAt(i).setOnTouchListener(this::setUserNumberEventHandler);
        }

        findViewById(R.id.next_button).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Intent intent = new Intent(this, TaskActivity.class);
                    intent.putExtra("userNum", userNum);
                    startActivity(intent);
                    break;
            }
            return true;
        });
    }

    public boolean setUserNumberEventHandler(View v, MotionEvent event) {
        TextView tv = (TextView) v;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (tv.getText().equals("Up")) {
                    userNum = userNum + 1;
                } else if (tv.getText().equals("Down")) {
                    userNum = userNum - 1;
                }
                userNumView.setText(String.valueOf(userNum));
                break;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_FILE:
                if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(TAG, "Permission always deny");
                }
                break;
        }
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    ) {
                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Explain to the user why we need to write the permission.
                    Toast.makeText(this, "Read/Write external storage", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        },
                        REQUEST_CODE_FILE);
                // MY_PERMISSION_REQUEST_STORAGE is an
                // app-defined int constant
            }
        }
    }
}
