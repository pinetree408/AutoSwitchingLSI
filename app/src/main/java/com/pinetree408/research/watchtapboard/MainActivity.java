package com.pinetree408.research.watchtapboard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity  {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Gesture Variable
    private int dragThreshold = 30;
    private final double angleFactor = (double) 180/Math.PI;

    private long touchDownTime;
    private float touchDownX, touchDownY;

    // View Variable
    ArrayList<String> originSourceList;
    ArrayList<String> sourceList;

    ArrayAdapter<String> adapter;
    ListView listview;

    TextView inputView;
    TextView placehoderView;
    TapBoardView tapBoardView;

    String inputString;
    View keyboardContainer;

    // Task Variable
    private Timer jobScheduler;
    Random random;
    String target;
    // 0 : tapboard
    // 1 : result based
    // 2 : input based
    // 3 : Swipe & Tap
    // 4 : TSI
    // 5 : Input TSI
    int keyboardMode;

    Toast toast;
    TextView startView;
    View taskView;

    // Exp Variable
    int listSize;
    int userNum;
    int trial;
    ViewGroup layoutSelectorView;
    long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        jobScheduler = new Timer();
        random = new Random();
        listSize = 0;
        userNum = 0;
        trial = 0;

        listview = (ListView) findViewById(R.id.list_view);
        placehoderView = (TextView) findViewById(R.id.place_holder);
        tapBoardView = (TapBoardView) findViewById(R.id.tapboard);
        keyboardContainer = findViewById(R.id.keyboard_container);

        layoutSelectorView = (ViewGroup) findViewById(R.id.layout_selector);
        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);

        initLayoutSelectorView();
        initTaskSelecotrView();
    }

    public void initSourceList() {
        sourceList = new ArrayList<>();
        originSourceList = new ArrayList<>(Arrays.asList(Source.set1));
        for (int i = 0; i < Source.set2.length; i++) {
            if (!originSourceList.contains(Source.set2[i])) {
                originSourceList.add(Source.set2[i]);
            }
        }
        for (int i = 0; i < Source.set3.length; i++) {
            if (!originSourceList.contains(Source.set3[i])) {
                originSourceList.add(Source.set3[i]);
            }
        }
        for (int i = 0; i < Source.set4.length; i++) {
            if (!originSourceList.contains(Source.set4[i])) {
                originSourceList.add(Source.set4[i]);
            }
        }
        originSourceList = new ArrayList(originSourceList.subList(0, listSize));
        Collections.sort(originSourceList);
        sourceList.addAll(originSourceList);
    }

    public void initListView() {
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sourceList){
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view.findViewById(android.R.id.text1);
                tv.setHeight(44);
                tv.setMinimumHeight(44);
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                String initText = tv.getText().toString();
                if (keyboardMode == 4) {
                    String text = "<font color=#00FF00>" + inputString + "</font>" + initText.replace(inputString, "");
                    tv.setText(Html.fromHtml(text));
                }
                return view;
            }
        };
        listview.setAdapter(adapter);
        listview.setBackgroundColor(Color.parseColor("#d3d3d3"));
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long endTime = System.currentTimeMillis();
                Log.d(TAG, trial + ":" + Long.toString(endTime - startTime));
                TextView selectedView = (TextView) view;
                String selected = selectedView.getText().toString();
                if (selected.equals(target)) {
                    selectedView.setBackgroundColor(Color.parseColor("#f0FF00"));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setNextTask();
                        }
                    }, 1000);
                } else {
                    selectedView.setBackgroundColor(Color.parseColor("#f08080"));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setNextTask();
                        }
                    }, 1000);
                }
            }
        });
        listview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int tempX = (int) event.getAxisValue(MotionEvent.AXIS_X);
                int tempY = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                long eventTime = System.currentTimeMillis();

                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownTime = eventTime;
                        touchDownX = tempX;
                        touchDownY = tempY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        long touchTime = eventTime - touchDownTime;
                        int xDir = (int) (touchDownX - tempX);
                        int yDir = (int) (touchDownY - tempY);
                        int len = (int) Math.sqrt(xDir * xDir + yDir * yDir);
                        int speed;
                        if (touchTime > 0) {
                            speed = (int) (len * 1000 / touchTime);
                        } else {
                            speed = 0;
                        }
                        if (len > dragThreshold) {
                            if (speed > 400) {
                                double angle = Math.acos((double) xDir / len) * angleFactor;
                                if (yDir < 0) {
                                    angle = 360 - angle;
                                }
                                angle += 45;
                                int id = (int) (angle / 90);
                                if (id > 3) {
                                    id = 0;
                                }
                                switch (id){
                                    case 0:
                                        // left
                                        if (keyboardMode != 3) {
                                            keyboardContainer.setVisibility(View.VISIBLE);
                                        } else {
                                            return true;
                                        }
                                        break;
                                    case 1:
                                        // top;
                                        break;
                                    case 2:
                                        // right
                                        return true;
                                    case 3:
                                        // bottom;
                                        break;
                                }
                            }
                        }
                        break;
                }
                return false;
            }
        });
        if (keyboardMode == 5) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
            );
            params.setMargins(0, 44, 0, 0);
            listview.setLayoutParams(params);

            inputView = new TextView(this);
            inputView.setHeight(44);
            inputView.setMinimumHeight(44);
            inputView.setWidth(320);
            inputView.setMinimumWidth(320);
            inputView.setTextColor(Color.parseColor("#00FF00"));
            inputView.setBackgroundColor(Color.WHITE);
            inputView.setGravity(Gravity.CENTER);
            inputView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

            ViewGroup taskViewGroup = (ViewGroup) findViewById(R.id.task);
            taskViewGroup.addView(inputView, 1);
        }
    }

    public void initKeyboardContainer() {
        inputString = "";
        keyboardContainer.bringToFront();
        switch (keyboardMode) {
            case 0:
                break;
            case 1:
            case 2:
                keyboardContainer.setBackgroundColor(Color.WHITE);
                break;
            case 3:
                keyboardContainer.setVisibility(View.GONE);
                break;
            case 4:
                tapBoardView.setBackgroundColor(Color.WHITE);
                break;
            case 5:
                tapBoardView.setBackgroundColor(Color.WHITE);
                break;
        }
        keyboardContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int tempX = (int) event.getAxisValue(MotionEvent.AXIS_X);
                int tempY = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                long eventTime = System.currentTimeMillis();

                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownTime = eventTime;
                        touchDownX = tempX;
                        touchDownY = tempY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        long touchTime = eventTime - touchDownTime;
                        int xDir = (int) (touchDownX - tempX);
                        int yDir = (int) (touchDownY - tempY);
                        int len = (int) Math.sqrt(xDir * xDir + yDir * yDir);
                        int speed;
                        if (touchTime > 0) {
                            speed = (int) (len * 1000 / touchTime);
                        } else {
                            speed = 0;
                        }
                        if (len > dragThreshold) {
                            if (speed > 400) {
                                double angle = Math.acos((double) xDir / len) * angleFactor;
                                if (yDir < 0) {
                                    angle = 360 - angle;
                                }
                                angle += 45;
                                int id = (int) (angle / 90);
                                if (id > 3) {
                                    id = 0;
                                }
                                switch (id){
                                    case 0:
                                        // left
                                        if (inputString.length() != 0) {
                                            inputString = inputString.substring(0, inputString.length() - 1);
                                        }
                                        setResultAtListView(inputString);
                                        switch (keyboardMode) {
                                            case 0:
                                                break;
                                            case 1:
                                            case 2:
                                                placehoderView.setText(inputString);
                                                break;
                                            case 3:
                                                break;
                                            case 4:
                                                if (sourceList.size() != 0) {
                                                    placehoderView.setText("");
                                                } else {
                                                    placehoderView.setText(inputString);
                                                }
                                                break;
                                            case 5:
                                                inputView.setText(inputString);
                                                break;
                                        }
                                        break;
                                    case 1:
                                        // top;
                                        break;
                                    case 2:
                                        // right
                                        keyboardContainer.setVisibility(View.GONE);
                                        break;
                                    case 3:
                                        // bottom;
                                        break;
                                }
                            }
                        } else {
                            if (touchTime < 200) {
                                // tap
                                if ((0 <= tempY) && (tempY < tapBoardView.getY())) {
                                    for (int i = 0; i < listview.getChildCount(); i++) {
                                        TextView childView = (TextView) listview.getChildAt(i);
                                        boolean under, over;
                                        if (keyboardMode != 5) {
                                            under = touchDownY <= (childView.getY() + childView.getHeight());
                                            over = touchDownY >= childView.getY();
                                        } else {
                                            under = touchDownY <= (childView.getY() + childView.getHeight() + 44);
                                            over = touchDownY >= childView.getY() + 44;
                                        }
                                        if (under && over) {
                                            long endTime = System.currentTimeMillis();
                                            Log.d(TAG, trial + ":" + Long.toString(endTime - startTime));
                                            if (childView.getText().toString().equals(target)) {
                                                childView.setBackgroundColor(Color.parseColor("#f0FF00"));
                                                new Handler().postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        setNextTask();
                                                    }
                                                }, 1000);
                                            } else {
                                                childView.setBackgroundColor(Color.parseColor("#f08080"));
                                                new Handler().postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        setNextTask();
                                                    }
                                                }, 1000);
                                            }
                                            break;
                                        }
                                    }
                                } else {
                                    String[] params = getInputInfo(event);
                                    if (params[0].equals(".")) {
                                        break;
                                    }
                                    inputString += params[0];
                                    setResultAtListView(inputString);
                                    if (keyboardMode == 0) {
                                        if (sourceList.size() == 0) {
                                            showNoItemsMessage();
                                        }
                                    } else if (keyboardMode == 1) {
                                        placehoderView.setText(inputString);
                                        if (sourceList.size() <= 7) {
                                            if (sourceList.size() != 0) {
                                                keyboardContainer.setVisibility(View.GONE);
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 2) {
                                        placehoderView.setText(inputString);
                                        if (inputString.length() > 2) {
                                            if (sourceList.size() != 0) {
                                                keyboardContainer.setVisibility(View.GONE);
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 4) {
                                        if (sourceList.size() == 0) {
                                            placehoderView.setText(inputString);
                                        }
                                    } else if (keyboardMode == 5) {
                                        inputView.setText(inputString);
                                    }
                                }
                            }
                        }
                        break;
                }
                return true;
            }
        });
    }

    public void showNoItemsMessage() {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(getApplicationContext(), "There are no itmes", Toast.LENGTH_SHORT);
        toast.show();
    }

    public void initTaskSelecotrView() {
        startView.setVisibility(View.GONE);
        taskView.setVisibility(View.GONE);
        layoutSelectorView.setVisibility(View.GONE);
        final ViewGroup taskSelectorView = (ViewGroup) findViewById(R.id.task_selector);
        final ViewGroup lengthSelecotrView = (ViewGroup) findViewById(R.id.length_selector);
        for (int i = 0; i < lengthSelecotrView.getChildCount(); i++) {
            final TextView childView = (TextView) lengthSelecotrView.getChildAt(i);
            childView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            listSize = Integer.parseInt(childView.getText().toString());
                                            for (int i = 0; i < lengthSelecotrView.getChildCount(); i++) {
                                                TextView tempChildView = (TextView) lengthSelecotrView.getChildAt(i);
                                                tempChildView.setBackgroundColor(Color.WHITE);
                                            }
                                            childView.setBackgroundColor(Color.parseColor("#f08080"));
                                        }
                                    }
                            );
                            break;
                    }
                    return true;
                }
            });
        }

        final ViewGroup userSelecotrView = (ViewGroup) findViewById(R.id.user_selector);
        for (int i = 0; i < userSelecotrView.getChildCount(); i++) {
            final TextView childView = (TextView) userSelecotrView.getChildAt(i);
            childView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (childView.getText().equals("Up")) {
                                                userNum = userNum + 1;
                                            } else if (childView.getText().equals("Down")) {
                                                userNum = userNum - 1;
                                            }
                                            TextView userNumView = (TextView) findViewById(R.id.user_number);
                                            userNumView.setText(Integer.toString(userNum));
                                        }
                                    }
                            );
                            break;
                    }
                    return true;
                }
            });
        }

        View nextView = findViewById(R.id.next_button);
        nextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (listSize != 0 && userNum >= 0) {
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            taskSelectorView.setVisibility(View.GONE);
                                            layoutSelectorView.setVisibility(View.VISIBLE);
                                            initSourceList();
                                        }
                                    }
                            );
                        }
                        break;
                }
                return true;
            }
        });

    }

    public void initLayoutSelectorView() {
        for (int i = 0; i < layoutSelectorView.getChildCount(); i++) {
            final TextView childView = (TextView) layoutSelectorView.getChildAt(i);
            childView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (childView.getText().equals("TapBoard")) {
                                            keyboardMode = 0;
                                        } else if (childView.getText().equals("ListBased")) {
                                            keyboardMode = 1;
                                        } else if (childView.getText().equals("InputBased")) {
                                            keyboardMode = 2;
                                        } else if (childView.getText().equals("SwipeAndTap")) {
                                            keyboardMode = 3;
                                        } else if (childView.getText().equals("TSI")) {
                                            keyboardMode = 4;
                                        } else if (childView.getText().equals("InputTSI")) {
                                            keyboardMode = 5;
                                        }
                                        initListView();
                                        initKeyboardContainer();
                                        setNextTask();
                                        layoutSelectorView.setVisibility(View.GONE);
                                    }
                                }
                            );
                            break;
                    }
                    return true;
                }
            });
        }
    }

    public void setResultAtListView(String inputString) {
        sourceList.clear();
        if (inputString.equals("")) {
            sourceList.addAll(originSourceList);
        } else {
            ArrayList<String> tempList = new ArrayList<>();
            for (String item : originSourceList) {
                if (item.startsWith(inputString)) {
                    tempList.add(item);
                }
            }
            sourceList.addAll(tempList);
        }
        adapter.notifyDataSetChanged();
        listview.setSelectionAfterHeaderView();
    }

    public String[] getInputInfo(MotionEvent event) {
        double tempX = (double) event.getAxisValue(MotionEvent.AXIS_X);
        double tempY = (double) event.getAxisValue(MotionEvent.AXIS_Y);

        String input = tapBoardView.getKey(tempX, tempY - tapBoardView.getY());

        return new String[] {
                String.valueOf(input),
                String.valueOf(tempX),
                String.valueOf(tempY)
        };
    }

    public void setNextTask() {
        target = originSourceList.get(random.nextInt(originSourceList.size()));
        startView.setText(target);
        startView.setVisibility(View.VISIBLE);
        startView.setOnTouchListener(null);
        taskView.setVisibility(View.GONE);
        inputString = "";
        if (keyboardMode == 5) {
            inputView.setText(inputString);
        }
        placehoderView.setText(inputString);
        setResultAtListView(inputString);
        switch (keyboardMode) {
            case 0:
                break;
            case 1:
            case 2:
                tapBoardView.setVisibility(View.VISIBLE);
                keyboardContainer.setBackgroundColor(Color.WHITE);
                break;
            case 3:
                break;
            case 4:
                break;
        }
        jobScheduler.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        startView.setBackgroundColor(Color.parseColor("#f08080"));
                                    }
                                }
                        );
                        startView.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                switch (event.getAction()) {
                                    case MotionEvent.ACTION_DOWN:
                                        runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        startView.setBackgroundColor(Color.parseColor("#ffffff"));
                                                        startView.setVisibility(View.GONE);
                                                        taskView.setVisibility(View.VISIBLE);
                                                        if (keyboardMode != 3) {
                                                            keyboardContainer.setVisibility(View.VISIBLE);
                                                        }
                                                        trial = trial + 1;
                                                        startTime = System.currentTimeMillis();
                                                    }
                                                }
                                        );
                                        break;
                                }
                                return true;
                            }
                        });
                    }
                },
                2000);
    }
}
