package com.pinetree408.research.watchtapboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
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
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends WearableActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    static final int REQUEST_CODE_FILE = 1;

    // Gesture Variable
    private int dragThreshold = 30;
    private final double angleFactor = (double) 180 / Math.PI;

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
    int[] targetList;
    // 0 : tapboard
    // 1 : result based
    // 2 : input based
    // 3 : Swipe & Tap
    // 4 : TSI
    int keyboardMode;
    TextView returnKeyboardView;

    Toast toast;
    TextView startView;
    View taskView;

    // Exp Variable
    int listSize;
    int userNum;
    int trial;
    int trialLimit;
    int err;
    TextView errView;
    TextView taskEndView;
    ViewGroup layoutSelectorView;
    long startTime;
    String sourceType;
    int taskTrial;
    Logger logger;
    String fileFormat = "block, trial, eventTime, target, inputKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        checkFileWritePermission();

        jobScheduler = new Timer();
        random = new Random();
        listSize = 0;
        userNum = 0;
        trial = 0;
        trialLimit = 7;
        //trialLimit = 2;
        err = 0;
        taskTrial = 0;

        listview = (ListView) findViewById(R.id.list_view);
        inputView = (TextView) findViewById(R.id.input);
        placehoderView = (TextView) findViewById(R.id.place_holder);
        tapBoardView = (TapBoardView) findViewById(R.id.tapboard);
        keyboardContainer = findViewById(R.id.keyboard_container);

        layoutSelectorView = (ViewGroup) findViewById(R.id.layout_selector);
        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);
        errView = (TextView) findViewById(R.id.err);
        taskEndView = (TextView) findViewById(R.id.task_end);

        initLayoutSelectorView();
        initTaskSelecotrView();
    }

    public void initSourceList() {
        sourceList = new ArrayList<>();
        if (sourceType.equals("person")) {
            originSourceList = new ArrayList<>(Arrays.asList(Source.name));
        } else {
            originSourceList = new ArrayList<>(Arrays.asList(Source.app));
        }
        originSourceList = new ArrayList(originSourceList.subList(0, listSize));
        Collections.sort(originSourceList);
        sourceList.addAll(originSourceList);
    }

    public void initListView() {
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sourceList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view.findViewById(android.R.id.text1);
                tv.setHeight(46);
                tv.setMinimumHeight(46);
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tv.setTextSize(11);
                return view;
            }
        };
        listview.setAdapter(adapter);
        listview.setBackgroundColor(Color.parseColor("#d3d3d3"));
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long endTime = System.currentTimeMillis();
                TextView selectedView = (TextView) view;
                String selected = selectedView.getText().toString();
                if (selected.equals(target)) {
                    logger.fileWriteLog(
                            taskTrial,
                            trial,
                            (System.currentTimeMillis() - startTime),
                            target,
                            "item-success"
                    );
                    selectedView.setBackgroundColor(Color.parseColor("#f0FF00"));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setNextTask();
                        }
                    }, 1000);
                } else {
                    logger.fileWriteLog(
                            taskTrial,
                            trial,
                            (System.currentTimeMillis() - startTime),
                            target,
                            "item-err"
                    );
                    startTime = System.currentTimeMillis();
                    listview.setSelectionAfterHeaderView();
                    err = err + 1;
                    String styledText = target + "<br/><font color='red'>" + err + "/3</font>";
                    errView.setText(Html.fromHtml(styledText));
                    taskView.setVisibility(View.GONE);
                    errView.setVisibility(View.VISIBLE);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            taskView.setVisibility(View.VISIBLE);
                            if (keyboardMode == 4) {
                                keyboardContainer.setVisibility(View.VISIBLE);
                                inputString = "";
                                setResultAtListView(inputString);
                                inputView.setText(inputString);
                                if (sourceList.size() != 0 && !inputString.equals("")) {
                                    placehoderView.setText(sourceList.get(0));
                                } else {
                                    placehoderView.setText("");
                                }
                            }
                            errView.setVisibility(View.GONE);
                            if (err > 2) {
                                err = 0;
                                setNextTask();
                            }
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

                switch (event.getAction()) {
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
                                switch (id) {
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
        if (keyboardMode == 4) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
            );
            params.setMargins(0, 44, 0, 0);
            listview.setLayoutParams(params);

            returnKeyboardView = new TextView(this);
            returnKeyboardView.setHeight(44);
            returnKeyboardView.setMinimumHeight(44);
            returnKeyboardView.setWidth(320);
            returnKeyboardView.setMinimumWidth(320);
            returnKeyboardView.setTextColor(Color.parseColor("#00FF00"));
            returnKeyboardView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.search, 0, 0);
            returnKeyboardView.setBackgroundColor(Color.WHITE);
            returnKeyboardView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            returnKeyboardView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            logger.fileWriteLog(
                                    taskTrial,
                                    trial,
                                    (System.currentTimeMillis() - startTime),
                                    target,
                                    "to-keyboard"
                            );
                            keyboardContainer.setVisibility(View.VISIBLE);
                            break;
                    }
                    return false;
                }
            });

            ViewGroup taskViewGroup = (ViewGroup) findViewById(R.id.task);
            taskViewGroup.addView(returnKeyboardView, 1);
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
        }
        keyboardContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int tempX = (int) event.getAxisValue(MotionEvent.AXIS_X);
                int tempY = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                long eventTime = System.currentTimeMillis();

                switch (event.getAction()) {
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
                                switch (id) {
                                    case 0:
                                        // left
                                        break;
                                    case 1:
                                        // top;
                                        break;
                                    case 2:
                                        // right
                                        break;
                                    case 3:
                                        // bottom;
                                        break;
                                }
                            }
                        } else {
                            if (touchTime < 200) {
                                // tap
                                if (tempY < placehoderView.getY()) {
                                    if ((tapBoardView.getX() + (tapBoardView.getWidth() / 2)) < tempX) {
                                        logger.fileWriteLog(
                                                taskTrial,
                                                trial,
                                                (System.currentTimeMillis() - startTime),
                                                target,
                                                "to-list"
                                        );
                                        keyboardContainer.setVisibility(View.GONE);
                                    }
                                } else if ((placehoderView.getY() <= tempY) && (tempY < tapBoardView.getY())) {
                                    if (placehoderView.getText().toString().equals(target)) {
                                        placehoderView.setBackgroundColor(Color.parseColor("#f0FF00"));
                                        logger.fileWriteLog(
                                                taskTrial,
                                                trial,
                                                (System.currentTimeMillis() - startTime),
                                                target,
                                                "item-success"
                                        );
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                setNextTask();
                                            }
                                        }, 1000);
                                    } else {
                                        err = err + 1;
                                        logger.fileWriteLog(
                                                taskTrial,
                                                trial,
                                                (System.currentTimeMillis() - startTime),
                                                target,
                                                "item-err"
                                        );
                                        startTime = System.currentTimeMillis();
                                        String styledText = target + "<br/><font color='red'>" + err + "/3</font>";
                                        errView.setText(Html.fromHtml(styledText));
                                        taskView.setVisibility(View.GONE);
                                        errView.setVisibility(View.VISIBLE);
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                taskView.setVisibility(View.VISIBLE);
                                                inputString = "";
                                                setResultAtListView(inputString);
                                                inputView.setText(inputString);
                                                if (sourceList.size() != 0 && !inputString.equals("")) {
                                                    placehoderView.setText(sourceList.get(0));
                                                } else {
                                                    placehoderView.setText("");
                                                }
                                                errView.setVisibility(View.GONE);
                                                if (err > 2) {
                                                    err = 0;
                                                    setNextTask();
                                                }
                                            }
                                        }, 1000);
                                    }
                                    break;
                                } else if (tapBoardView.getY() + tapBoardView.getHeight() < tempY) {
                                    if (tempX < (tapBoardView.getX() + (tapBoardView.getWidth() / 2))) {
                                        inputString = inputString + " ";
                                        logger.fileWriteLog(
                                                taskTrial,
                                                trial,
                                                (System.currentTimeMillis() - startTime),
                                                target,
                                                "space"
                                        );
                                    } else {
                                        logger.fileWriteLog(
                                                taskTrial,
                                                trial,
                                                (System.currentTimeMillis() - startTime),
                                                target,
                                                "delete"
                                        );
                                        if (inputString.length() != 0) {
                                            inputString = inputString.substring(0, inputString.length() - 1);
                                        }
                                        setResultAtListView(inputString);
                                        switch (keyboardMode) {
                                            case 0:
                                                break;
                                            case 1:
                                            case 2:
                                                inputView.setText(inputString);
                                                break;
                                            case 3:
                                                break;
                                            case 4:
                                                inputView.setText(inputString);
                                                if (sourceList.size() != 0 && !inputString.equals("")) {
                                                    placehoderView.setText(sourceList.get(0));
                                                } else {
                                                    placehoderView.setText("");
                                                }
                                                break;
                                        }
                                    }
                                } else {
                                    String[] params = getInputInfo(event);
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            params[0]
                                    );
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
                                        inputView.setText(inputString);
                                        if (sourceList.size() <= 7) {
                                            if (sourceList.size() != 0) {
                                                keyboardContainer.setVisibility(View.GONE);
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 2) {
                                        inputView.setText(inputString);
                                        if (inputString.length() > 2) {
                                            if (sourceList.size() != 0) {
                                                keyboardContainer.setVisibility(View.GONE);
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 4) {
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
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
        errView.setVisibility(View.GONE);
        taskEndView.setVisibility(View.GONE);
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

        final ViewGroup sourceSelecotrView = (ViewGroup) findViewById(R.id.source_selector);
        for (int i = 0; i < sourceSelecotrView.getChildCount(); i++) {
            final TextView childView = (TextView) sourceSelecotrView.getChildAt(i);
            childView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (childView.getText().equals("person")) {
                                                sourceType = "person";
                                            } else if (childView.getText().equals("app")) {
                                                sourceType = "app";
                                            }
                                            for (int i = 0; i < sourceSelecotrView.getChildCount(); i++) {
                                                TextView tempChildView = (TextView) sourceSelecotrView.getChildAt(i);
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
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        String[] taskList;
                                        switch (userNum) {
                                            case 0:
                                                trialLimit = 2;
                                                taskList = TaskList.p0;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 1:
                                                taskList = TaskList.p1;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 2:
                                                taskList = TaskList.p2;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 3:
                                                taskList = TaskList.p3;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 4:
                                                taskList = TaskList.p4;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 5:
                                                taskList = TaskList.p5;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 6:
                                                taskList = TaskList.p6;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 7:
                                                taskList = TaskList.p7;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 8:
                                                taskList = TaskList.p8;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 9:
                                                taskList = TaskList.p9;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 10:
                                                taskList = TaskList.p10;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 11:
                                                taskList = TaskList.p11;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 12:
                                                taskList = TaskList.p12;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                            case 13:
                                                taskList = TaskList.p13;
                                                setTaskSetting(taskList, taskTrial);
                                                break;
                                        }
                                        taskSelectorView.setVisibility(View.GONE);
                                        //layoutSelectorView.setVisibility(View.VISIBLE);
                                        initSourceList();

                                        String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile() + "/";
                                        String fileName = "result_bb_" + userNum + ".csv";
                                        Log.d(TAG, filePath + fileName);
                                        logger = new Logger(filePath, fileName);
                                        logger.fileOpen(userNum);
                                        logger.fileWriteHeader(fileFormat);

                                        initListView();
                                        initKeyboardContainer();

                                        targetList = predefineRandom(listSize, trialLimit + 1);
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
                                            } else if (childView.getText().equals("ST")) {
                                                keyboardMode = 3;
                                            } else if (childView.getText().equals("TSI")) {
                                                keyboardMode = 4;
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

        String input = tapBoardView.getKey(tempX - tapBoardView.getX(), tempY - tapBoardView.getY());

        return new String[]{
                String.valueOf(input),
                String.valueOf(tempX),
                String.valueOf(tempY)
        };
    }

    public void setTaskSetting(String[] taskList, int taskTrial) {
        String taskSet = taskList[taskTrial];
        String[] data = taskSet.split(", ");

        listSize = Integer.parseInt(data[1]);
        sourceType = data[0];
        if (data[2].equals("ST")) {
            keyboardMode = 3;
        } else if (data[2].equals("TSI")) {
            keyboardMode = 4;
        }
    }

    public void setNextTask() {
        if (trial > trialLimit) {
            trial = 0;
            taskTrial = taskTrial + 1;
            String[] taskList;
            switch (userNum) {
                case 0:
                    taskList = TaskList.p0;
                    if (taskTrial >= taskList.length - 1) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 1:
                    taskList = TaskList.p1;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 2:
                    taskList = TaskList.p2;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 3:
                    taskList = TaskList.p3;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 4:
                    taskList = TaskList.p4;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 5:
                    taskList = TaskList.p5;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 6:
                    taskList = TaskList.p6;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 7:
                    taskList = TaskList.p7;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 8:
                    taskList = TaskList.p8;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 9:
                    taskList = TaskList.p9;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 10:
                    taskList = TaskList.p10;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 11:
                    taskList = TaskList.p11;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 12:
                    taskList = TaskList.p12;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
                case 13:
                    taskList = TaskList.p13;
                    if (taskTrial >= taskList.length) {
                        MainActivity.this.finish();
                        System.exit(0);
                    }
                    setTaskSetting(taskList, taskTrial);
                    break;
            }
            targetList = predefineRandom(listSize, trialLimit + 1);
            if (keyboardMode == 3) {
                taskEndView.setText(listSize + "-" + sourceType + "-ST");
            } else {
                taskEndView.setText(listSize + "-" + sourceType + "-TSI");
            }
            listview.setVisibility(View.GONE);
            if (keyboardMode != 4) {
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT
                );
                params.setMargins(0, 0, 0, 0);
                listview.setLayoutParams(params);
                listview.bringToFront();
            }
            taskEndView.setVisibility(View.VISIBLE);
            taskEndView.bringToFront();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    taskEndView.setVisibility(View.GONE);
                    listview.setVisibility(View.VISIBLE);
                    initSourceList();
                    initListView();
                    initKeyboardContainer();
                    setNextTask();
                }
            }, 10000);
            return;
        }
        target = originSourceList.get(targetList[trial]);
        if (taskTrial == 0 && trial == 0) {
            if (keyboardMode == 3) {
                startView.setText(listSize + "-" + sourceType + "-ST" + "\n" + (trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
            } else {
                startView.setText(listSize + "-" + sourceType + "-TSI" + "\n" + (trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
            }
        } else {
            startView.setText((trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
        }
        startView.setVisibility(View.VISIBLE);
        startView.setOnTouchListener(null);
        taskView.setVisibility(View.GONE);
        inputString = "";
        err = 0;
        inputView.setText(inputString);
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
                placehoderView.setBackgroundColor(Color.WHITE);
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
                                                            keyboardContainer.setBackgroundColor(Color.WHITE);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_FILE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Log.d(TAG, "Permission always deny");
                }
                break;
        }
    }

    public void checkFileWritePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Explain to the user why we need to write the permission.
                    Toast.makeText(this, "Read/Write external storage", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_FILE);
                // MY_PERMISSION_REQUEST_STORAGE is an
                // app-defined int constant
            } else {
            }
        } else {
        }

    }

    int[] predefineRandom(int n, int size){
        double goalMean = n/2.0;

        int[] ret = new int[size];

        Random random = new Random();

        int currSize = 0;
        int retSum = 0;
        while (currSize < size - 1){
            int temp = random.nextInt(n);
            if (!intContains(ret, temp)){
                ret[currSize++] = temp;
                retSum += temp;
            }
        }

        int last = (int) (goalMean * size) - retSum;

        if(last < 0 || last >= size){
            return predefineRandom(n, size);
        }
        else{
            if (intContains(ret, last))
                return predefineRandom(n, size);
            else{
                ret[size - 1] = last;
                return ret;
            }
        }
    }

    boolean intContains(int[] A, int B) {
        for (int i = 0; i < A.length; i++) {
            if (A[i] == B)
                return true;
        }
        return false;
    }
}
