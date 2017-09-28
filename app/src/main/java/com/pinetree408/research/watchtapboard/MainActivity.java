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
    // 1 : list based
    // 2 : input based
    // 3 : Swipe & Tap
    // 4 : TSI
    int keyboardMode;
    TextView returnKeyboardView;

    TextView startView;
    View taskView;

    // Exp Variable
    int listSize;
    int userNum;
    int trial;
    int trialLimit;
    int err;
    TextView errView;
    TextView successView;
    TextView taskEndView;
    long startTime;
    String sourceType;
    int taskTrial;
    Logger logger;
    String fileFormat = "block, trial, eventTime, target, inputKey, listSize, index";
    long autoToListTime;

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
        err = 0;
        taskTrial = 0;

        listview = (ListView) findViewById(R.id.list_view);
        inputView = (TextView) findViewById(R.id.input);
        placehoderView = (TextView) findViewById(R.id.place_holder);
        tapBoardView = (TapBoardView) findViewById(R.id.tapboard);
        keyboardContainer = findViewById(R.id.keyboard_container);

        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);
        errView = (TextView) findViewById(R.id.err);
        successView = (TextView) findViewById(R.id.success);
        taskEndView = (TextView) findViewById(R.id.task_end);

        initTaskSelecotrView();
    }

    public void initSourceList() {
        sourceList = new ArrayList<>();
        if (sourceType.equals("person")) {
            originSourceList = new ArrayList<>(Arrays.asList(Source.name));
        } else {
            originSourceList = new ArrayList<>(Arrays.asList(Source.app));
        }
        originSourceList = new ArrayList<>(originSourceList.subList(0, listSize));
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
        listview.setOnItemClickListener((parent, view, position, id) -> {
            if (System.currentTimeMillis() - autoToListTime < 500) {
                return;
            }
            TextView selectedView = (TextView) view;
            String selected = selectedView.getText().toString();
            if (selected.equals(target)) {
                logger.fileWriteLog(
                        taskTrial,
                        trial,
                        (System.currentTimeMillis() - startTime),
                        target,
                        "item-success",
                        listview.getAdapter().getCount(),
                        sourceList.indexOf(target)
                );
                selectedView.setBackgroundColor(Color.parseColor("#f0FF00"));
                successView.setVisibility(View.VISIBLE);
                successView.bringToFront();
                new Handler().postDelayed(() -> {
                    successView.setVisibility(View.GONE);
                    setNextTask();
                }, 1000);
            } else {
                logger.fileWriteLog(
                        taskTrial,
                        trial,
                        (System.currentTimeMillis() - startTime),
                        target,
                        "item-err",
                        listview.getAdapter().getCount(),
                        sourceList.indexOf(target)
                );
                startTime = System.currentTimeMillis();
                listview.setSelectionAfterHeaderView();
                err = err + 1;
                String styledText = target + "<br/><font color='red'>" + err + "/5</font>";
                errView.setText(Html.fromHtml(styledText));
                taskView.setVisibility(View.GONE);
                errView.setVisibility(View.VISIBLE);

                new Handler().postDelayed(() -> {
                    taskView.setVisibility(View.VISIBLE);
                    if (keyboardMode == 1 || keyboardMode == 2 || keyboardMode == 4) {
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
                    if (err > 4) {
                        err = 0;
                        setNextTask();
                    }
                }, 1000);
            }
        });
        listview.setOnTouchListener((v, event) -> {
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
        });
        if (keyboardMode == 1 || keyboardMode == 2 || keyboardMode == 4) {
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
            returnKeyboardView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        logger.fileWriteLog(
                                taskTrial,
                                trial,
                                (System.currentTimeMillis() - startTime),
                                target,
                                "to-keyboard",
                                listview.getAdapter().getCount(),
                                sourceList.indexOf(target)
                        );
                        keyboardContainer.setVisibility(View.VISIBLE);
                        break;
                }
                return false;
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
            case 3:
                keyboardContainer.setVisibility(View.GONE);
                break;
            case 1:
            case 2:
            case 4:
                tapBoardView.setBackgroundColor(Color.WHITE);
                break;
        }
        keyboardContainer.setOnTouchListener((v, event) -> {
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
                                            "to-list",
                                            listview.getAdapter().getCount(),
                                            sourceList.indexOf(target)
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
                                            "item-success",
                                            listview.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    successView.setVisibility(View.VISIBLE);
                                    successView.bringToFront();
                                    new Handler().postDelayed(() -> {
                                        successView.setVisibility(View.GONE);
                                        setNextTask();
                                    }, 1000);
                                } else {
                                    err = err + 1;
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            "item-err",
                                            listview.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    startTime = System.currentTimeMillis();
                                    String styledText = target + "<br/><font color='red'>" + err + "/5</font>";
                                    errView.setText(Html.fromHtml(styledText));
                                    taskView.setVisibility(View.GONE);
                                    errView.setVisibility(View.VISIBLE);
                                    new Handler().postDelayed(() -> {
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
                                        if (err > 4) {
                                            err = 0;
                                            setNextTask();
                                        }
                                    }, 1000);
                                }
                                break;
                            } else if (tapBoardView.getY() + tapBoardView.getHeight() < tempY) {
                                logger.fileWriteLog(
                                        taskTrial,
                                        trial,
                                        (System.currentTimeMillis() - startTime),
                                        target,
                                        "delete",
                                        listview.getAdapter().getCount(),
                                        sourceList.indexOf(target)
                                );
                                if (inputString.length() != 0) {
                                    inputString = inputString.substring(0, inputString.length() - 1);
                                }
                                setResultAtListView(inputString);
                                switch (keyboardMode) {
                                    case 0:
                                        break;
                                    case 3:
                                        break;
                                    case 1:
                                    case 2:
                                    case 4:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
                                        break;
                                }
                            } else {
                                String[] params = getInputInfo(event);
                                if (params[0].equals(".")) {
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            params[0],
                                            listview.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    break;
                                }
                                inputString += params[0];
                                setResultAtListView(inputString);
                                logger.fileWriteLog(
                                        taskTrial,
                                        trial,
                                        (System.currentTimeMillis() - startTime),
                                        target,
                                        params[0],
                                        listview.getAdapter().getCount(),
                                        sourceList.indexOf(target)
                                );
                                int convertSize = 0;
                                switch (keyboardMode) {
                                    case 0:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
                                        break;
                                    case 1:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
                                        if (listSize == 60) {
                                            convertSize = 4;
                                        } else if (listSize == 240) {
                                            convertSize = 9;
                                        }
                                        if (sourceList.size() <= convertSize && sourceList.size() != 0) {
                                            autoToListTime = System.currentTimeMillis();
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    listview.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case 2:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
                                        convertSize = 2;
                                        if (inputString.length() >= convertSize && sourceList.size() != 0) {
                                            autoToListTime = System.currentTimeMillis();
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    listview.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case 4:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0) {
                                            placehoderView.setText(sourceList.get(0));
                                        } else {
                                            placehoderView.setText("");
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    break;
            }
            return true;
        });
    }

    public void initTaskSelecotrView() {
        errView.setVisibility(View.GONE);
        taskEndView.setVisibility(View.GONE);
        startView.setVisibility(View.GONE);
        taskView.setVisibility(View.GONE);
        final ViewGroup taskSelectorView = (ViewGroup) findViewById(R.id.task_selector);

        final ViewGroup userSelecotrView = (ViewGroup) findViewById(R.id.user_selector);
        for (int i = 0; i < userSelecotrView.getChildCount(); i++) {
            final TextView childView = (TextView) userSelecotrView.getChildAt(i);
            childView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        runOnUiThread(() -> {
                            if (childView.getText().equals("Up")) {
                                userNum = userNum + 1;
                            } else if (childView.getText().equals("Down")) {
                                userNum = userNum - 1;
                            }
                            TextView userNumView = (TextView) findViewById(R.id.user_number);
                            userNumView.setText(userNum);
                        });
                        break;
                }
                return true;
            });
        }

        View nextView = findViewById(R.id.next_button);
        nextView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    runOnUiThread(() -> {
                        if (userNum == 0) {
                            trialLimit = 2;
                        }
                        setTaskList(userNum);
                        taskSelectorView.setVisibility(View.GONE);
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
                    });
                    break;
            }
            return true;
        });

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
        switch(data[2]) {
            case "LTSI":
                keyboardMode = 1;
                break;
            case "ITSI":
                keyboardMode = 2;
                break;
            case "ST":
                keyboardMode = 3;
                break;
            case "TSI":
                keyboardMode = 4;
                break;
        }
    }

    public void setTaskList(int userNum) {
        String[] taskList;
        switch (userNum) {
            case 0:
                taskList = ExpThreeTaskList.p0;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 1:
                taskList = ExpThreeTaskList.p1;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 2:
                taskList = ExpThreeTaskList.p2;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 3:
                taskList = ExpThreeTaskList.p3;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 4:
                taskList = ExpThreeTaskList.p4;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 5:
                taskList = ExpThreeTaskList.p5;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 6:
                taskList = ExpThreeTaskList.p6;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 7:
                taskList = ExpThreeTaskList.p7;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 8:
                taskList = ExpThreeTaskList.p8;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 9:
                taskList = ExpThreeTaskList.p9;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 10:
                taskList = ExpThreeTaskList.p10;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 11:
                taskList = ExpThreeTaskList.p11;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
            case 12:
                taskList = ExpThreeTaskList.p12;
                if (taskTrial >= taskList.length) {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                setTaskSetting(taskList, taskTrial);
                break;
        }
    }

    public void setNextTask() {
        if (trial > trialLimit) {
            trial = 0;
            taskTrial = taskTrial + 1;
            setTaskList(userNum);
            targetList = predefineRandom(listSize, trialLimit + 1);
            if (keyboardMode == 3) {
                taskEndView.setText(listSize + "-" + sourceType + "-ST");
            } else if (keyboardMode == 4) {
                taskEndView.setText(listSize + "-" + sourceType + "-TSI");
            } else if (keyboardMode == 1) {
                taskEndView.setText(listSize + "-" + sourceType + "-LTSI");
            } else if (keyboardMode == 2) {
                taskEndView.setText(listSize + "-" + sourceType + "-ITSI");
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
            new Handler().postDelayed(() -> {
                taskEndView.setVisibility(View.GONE);
                listview.setVisibility(View.VISIBLE);
                initSourceList();
                initListView();
                initKeyboardContainer();
                setNextTask();
            }, 5000);
            return;
        }
        target = originSourceList.get(targetList[trial]);
        if (taskTrial == 0 && trial == 0) {
            if (keyboardMode == 1) {
                startView.setText(listSize + "-" + sourceType + "-LTSI" + "\n" + (trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
            } else if (keyboardMode == 2) {
                startView.setText(listSize + "-" + sourceType + "-ITSI" + "\n" + (trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
            } else if (keyboardMode == 3) {
                startView.setText(listSize + "-" + sourceType + "-ST" + "\n" + (trial + 1) + "/" + (trialLimit + 1) + "\n" + target);
            } else if (keyboardMode == 4) {
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
            case 3:
                break;
            case 1:
            case 2:
            case 4:
                placehoderView.setBackgroundColor(Color.WHITE);
                break;
        }
        jobScheduler.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> startView.setBackgroundColor(Color.parseColor("#f08080")));
                        startView.setOnTouchListener((v, event) -> {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    runOnUiThread(() -> {
                                        startView.setBackgroundColor(Color.parseColor("#ffffff"));
                                        startView.setVisibility(View.GONE);
                                        taskView.setVisibility(View.VISIBLE);
                                        if (keyboardMode != 3) {
                                            keyboardContainer.setVisibility(View.VISIBLE);
                                            keyboardContainer.setBackgroundColor(Color.WHITE);
                                        }
                                        trial = trial + 1;
                                        startTime = System.currentTimeMillis();
                                    });
                                    break;
                            }
                            return true;
                        });
                    }
                },
                2000);
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
            }
        }
    }

    int[] predefineRandom(int n, int size){
        double goalMean = n/2.0;

        int[] ret = new int[size];
        int currSize = 0;
        int retSum = 0;
        while (currSize < size - 1){
            int temp = StdRandom.uniform(n);
            if (!intContains(ret, temp)){
                ret[currSize++] = temp;
                retSum += temp;
            }
        }

        int last = (int) (goalMean * size) - retSum;

        if(last < 0 || last >= n){
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
        for (int item : A) {
            if (item == B)
                return true;
        }
        return false;
    }
}
