package com.pinetree408.research.watchtapboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pinetree408.research.watchtapboard.exp.source.Source;
import com.pinetree408.research.watchtapboard.exp.tasklist.ExpThreeTaskList;
import com.pinetree408.research.watchtapboard.util.KeyBoardView;
import com.pinetree408.research.watchtapboard.util.Logger;
import com.pinetree408.research.watchtapboard.util.Util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by leesangyoon on 2017. 12. 22..
 */

public class TaskActivity extends WearableActivity {
    private static final String TAG = TaskActivity.class.getSimpleName();

    static final int REQUEST_CODE_FILE = 1;

    // Gesture Variable
    private int dragThreshold = 30;

    private long touchDownTime;
    private float touchDownX, touchDownY;

    // View Variable
    ArrayList<String> originSourceList;
    ArrayList<String> sourceList;

    ArrayAdapter<String> adapter;
    ListView listview;

    TextView inputView;
    LinearLayout placeholderContainer;
    TextView placeholderTextView;
    MyRecyclerViewAdapter placeholderAdapter;
    RecyclerView placeholderRecyclerView;
    KeyBoardView keyBoardView;

    String inputString;
    View keyboardContainer;

    // Task Variable
    private Timer jobScheduler;
    String target;
    int[] targetIndexList;

    static final int LTSI = 1;
    static final int ITSI = 2;
    static final int ST = 3;
    static final int TSI = 4;
    static final int HTSI = 5;
    static final int TSIS = 6;
    static final int HTSIS = 7;

    int keyboardMode;
    TextView returnKeyboardView;
    TextView shadowView;

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
    long autoToListTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        Intent intent = new Intent(this.getIntent());
        userNum =intent.getIntExtra("userNum", 0);

        setAmbientEnabled();
        checkFileWritePermission();

        jobScheduler = new Timer();

        listSize = 0;
        trial = 0;
        trialLimit = 7;
        err = 0;
        taskTrial = 0;

        listview = (ListView) findViewById(R.id.list_view);
        inputView = (TextView) findViewById(R.id.input);
        placeholderContainer = (LinearLayout) findViewById(R.id.place_holder);

        // place holder init
        placeholderTextView = new TextView(this);
        placeholderTextView.setHeight(64);
        placeholderTextView.setMinimumHeight(64);
        placeholderTextView.setWidth(320);
        placeholderTextView.setMinimumWidth(320);
        placeholderTextView.setTextColor(Color.GREEN);
        placeholderTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        placeholderRecyclerView = new RecyclerView(this);
        placeholderRecyclerView.setMinimumHeight(64);
        placeholderRecyclerView.setMinimumWidth(320);
        placeholderRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        keyBoardView = (KeyBoardView) findViewById(R.id.tapboard);
        keyboardContainer = findViewById(R.id.keyboard_container);

        shadowView = (TextView) findViewById(R.id.shadow);

        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);
        errView = (TextView) findViewById(R.id.err);
        successView = (TextView) findViewById(R.id.success);
        taskEndView = (TextView) findViewById(R.id.task_end);

        // Init
        errView.setVisibility(View.GONE);
        taskEndView.setVisibility(View.GONE);
        startView.setVisibility(View.GONE);
        taskView.setVisibility(View.GONE);
        shadowView.setVisibility(View.GONE);

        initLogger(userNum);

        if (userNum == 0) {
            trialLimit = 2;
        }

        setTaskList(userNum);

        initSourceList();
        initListView();
        initKeyboardContainer();

        targetIndexList = Util.predefineRandom(listSize, trialLimit + 1);
        setNextTask();
    }

    public void initLogger(int userNum) {
        String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile() + "/";
        String fileFormat = "block, trial, eventTime, target, inputKey, listSize, index";
        String fileName = "result_bb_" + userNum + ".csv";
        logger = new Logger(filePath, fileName);
        logger.fileOpen(userNum);
        logger.fileWriteHeader(fileFormat);
    }

    public void initSourceList() {
        sourceList = new ArrayList<>();
        if (sourceType.equals("person")) {
            originSourceList = new ArrayList<>(Arrays.asList(Source.fullName));
        } else {
            originSourceList = new ArrayList<>(Arrays.asList(Source.app));
        }
        originSourceList = new ArrayList<>(originSourceList.subList(0, listSize));
        Collections.sort(originSourceList);
        sourceList.addAll(originSourceList);
    }

    public void initListView() {
        adapter = new ArrayAdapter<String>(this, R.layout.listview_item, sourceList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                view.setBackgroundColor(Color.WHITE);
                return view;
            }
        };
        listview.setAdapter(adapter);
        listview.setOnItemClickListener((parent, view, position, id) -> {
            if ((System.currentTimeMillis() - autoToListTime) >= 500) {
                checkSelectedItem((TextView) view);
            }
        });

        if (keyboardMode == HTSIS) {
            placeholderAdapter = new MyRecyclerViewAdapter(this, sourceList, true);
        } else {
            placeholderAdapter = new MyRecyclerViewAdapter(this, sourceList);
        }
        placeholderAdapter.setClickListener((view, position) -> checkSelectedItem((TextView) view));
        placeholderRecyclerView.setAdapter(placeholderAdapter);

        if (keyboardMode == LTSI || keyboardMode == ITSI || keyboardMode == TSI || keyboardMode == TSIS) {
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
                    case MotionEvent.ACTION_UP:
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
                        if (keyboardMode == TSIS) {
                            int height;
                            if (sourceList.size() >= 7) {
                                height = keyboardContainer.getHeight();
                            } else {
                                height = keyboardContainer.getHeight() / 7 * sourceList.size();
                            }

                            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                                    RelativeLayout.LayoutParams.MATCH_PARENT,
                                    height
                            );
                            shadowView.setLayoutParams(layoutParams);
                            shadowView.setBackgroundColor(Color.parseColor("#4400ff00"));
                            shadowView.setVisibility(View.VISIBLE);
                            shadowView.bringToFront();
                        }
                        break;
                }
                return true;
            });

            ViewGroup taskViewGroup = (ViewGroup) findViewById(R.id.task);
            taskViewGroup.addView(returnKeyboardView, 1);
        }
    }

    public void checkSelectedItem(TextView selectedView) {
        String selected;
        if (keyboardMode == HTSIS) {
            selected = selectedView.getText().toString().replace("\n", " ");
        } else {
            selected = selectedView.getText().toString();
        }

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
            placeholderRecyclerView.getLayoutManager().scrollToPosition(0);

            err = err + 1;
            String styledText = target + "<br/><font color='red'>" + err + "/5</font>";
            errView.setText(Html.fromHtml(styledText));
            taskView.setVisibility(View.GONE);
            errView.setVisibility(View.VISIBLE);

            new Handler().postDelayed(() -> {
                taskView.setVisibility(View.VISIBLE);

                if (keyboardMode == LTSI || keyboardMode == ITSI || keyboardMode == TSI
                        || keyboardMode == HTSI || keyboardMode == TSIS || keyboardMode == HTSIS) {
                    keyboardContainer.setVisibility(View.VISIBLE);
                    shadowView.setVisibility(View.GONE);
                    inputString = "";
                    setResultAtListView(inputString);
                    inputView.setText(inputString);
                    if (sourceList.size() != 0 && !inputString.equals("")) {
                        placeholderTextView.setText(sourceList.get(0));
                    } else {
                        placeholderTextView.setText("");
                    }
                }

                errView.setVisibility(View.GONE);
                if (err > 4) {
                    err = 0;
                    setNextTask();
                }
            }, 1000);
        }
    }

    public void initKeyboardContainer() {
        inputString = "";
        keyboardContainer.bringToFront();

        switch (keyboardMode) {
            case ST:
                keyboardContainer.setVisibility(View.GONE);
                break;
            case LTSI:
            case ITSI:
            case TSI:
            case HTSI:
            case TSIS:
            case HTSIS:
                keyBoardView.setBackgroundColor(Color.WHITE);
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

                    if (len <= dragThreshold) {
                        if (touchTime < 200) {
                            // tap
                            if (tempY < placeholderContainer.getY()) {
                                if ((keyBoardView.getX() + (keyBoardView.getWidth() / 2)) < tempX) {
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            "to-list",
                                            listview.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    if (keyboardMode != HTSI && keyboardMode != HTSIS) {
                                        keyboardContainer.setVisibility(View.GONE);
                                        shadowView.setVisibility(View.GONE);
                                    }
                                }
                            } else if ((placeholderContainer.getY() <= tempY) && (tempY < keyBoardView.getY())) {
                                checkSelectedItem(placeholderTextView);
                            } else if (keyBoardView.getY() + keyBoardView.getHeight() < tempY) {
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
                                    case ST:
                                        break;
                                    case LTSI:
                                    case ITSI:
                                    case TSI:
                                    case HTSI:
                                    case HTSIS:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            placeholderTextView.setText(sourceList.get(0));
                                        } else {
                                            placeholderTextView.setText("");
                                        }
                                        break;
                                    case TSIS:
                                        int height;
                                        if (sourceList.size() >= 7) {
                                            height = keyboardContainer.getHeight();
                                        } else {
                                            height = keyboardContainer.getHeight() / 7 * sourceList.size();
                                        }

                                        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                                height
                                        );
                                        shadowView.setLayoutParams(layoutParams);
                                        shadowView.setBackgroundColor(Color.parseColor("#4400ff00"));
                                        shadowView.setVisibility(View.VISIBLE);
                                        shadowView.bringToFront();

                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            placeholderTextView.setText(sourceList.get(0));
                                        } else {
                                            placeholderTextView.setText("");
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
                                inputView.setText(inputString);
                                if (sourceList.size() != 0) {
                                    placeholderTextView.setText(sourceList.get(0));
                                } else {
                                    placeholderTextView.setText("");
                                }

                                switch (keyboardMode) {
                                    case ST:
                                    case TSI:
                                    case HTSI:
                                    case HTSIS:
                                        break;
                                    case TSIS:
                                        Log.d(TAG, String.valueOf(sourceList.size()));
                                        runOnUiThread(() -> {
                                            int height;
                                            if (sourceList.size() >= 7) {
                                                height = keyboardContainer.getHeight();
                                            } else {
                                                height = keyboardContainer.getHeight() / 7 * sourceList.size();
                                            }

                                            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                                                    RelativeLayout.LayoutParams.MATCH_PARENT,
                                                    height
                                            );
                                            shadowView.setLayoutParams(layoutParams);
                                            shadowView.setBackgroundColor(Color.parseColor("#4400ff00"));
                                            shadowView.setVisibility(View.VISIBLE);
                                            shadowView.bringToFront();
                                        });
                                        break;
                                    case LTSI:
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
                                    case ITSI:
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
                                }
                            }
                        }
                    }
                    v.performClick();
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
                if (item.replace(" ", "").startsWith(inputString)) {
                    tempList.add(item);
                }
            }
            sourceList.addAll(tempList);
        }
        adapter.notifyDataSetChanged();
        listview.setSelectionAfterHeaderView();

        placeholderAdapter.notifyDataSetChanged();
        placeholderRecyclerView.getLayoutManager().scrollToPosition(0);
    }

    public String[] getInputInfo(MotionEvent event) {
        double tempX = (double) event.getAxisValue(MotionEvent.AXIS_X);
        double tempY = (double) event.getAxisValue(MotionEvent.AXIS_Y);
        String input = keyBoardView.getKey(tempX - keyBoardView.getX(), tempY - keyBoardView.getY());

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
                keyboardMode = LTSI;
                break;
            case "ITSI":
                keyboardMode = ITSI;
                break;
            case "ST":
                keyboardMode = ST;
                break;
            case "TSI":
                keyboardMode = TSI;
                break;
            case "HTSI":
                keyboardMode = HTSI;
                break;
            case "TSIS":
                keyboardMode = TSIS;
                break;
            case "HTSIS":
                keyboardMode = HTSIS;
                break;
        }
    }

    public void setTaskList(int userNum) {
        try {
            Field[] fields = ExpThreeTaskList.class.getDeclaredFields();
            HashMap<String, String[]> taskListSet = new HashMap<>();
            for (Field f : fields) {
                if (f.get(null) instanceof String[]) {
                    taskListSet.put(f.getName(), (String[]) f.get(null));
                }
            }
            String[] taskList = taskListSet.get("p" + userNum);
            if (taskTrial >= taskList.length) {
                TaskActivity.this.finish();
                System.exit(0);
            }
            setTaskSetting(taskList, taskTrial);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public String changeKeyboardModeType(int keyboardMode) {
        String keyboardModeString = "";
        switch(keyboardMode) {
            case LTSI:
                keyboardModeString = "LTSI";
                break;
            case ITSI:
                keyboardModeString = "ITSI";
                break;
            case ST:
                keyboardModeString = "ST";
                break;
            case TSI:
                keyboardModeString = "TSI";
                break;
            case HTSI:
                keyboardModeString = "HTSI";
                break;
            case TSIS:
                keyboardModeString = "TSIS";
                break;
            case HTSIS:
                keyboardModeString = "HTSIS";
                break;
        }
        return keyboardModeString;
    }

    public void setNextTask() {

        shadowView.setVisibility(View.GONE);

        placeholderContainer.removeAllViews();

        if (keyboardMode != HTSI && keyboardMode != HTSIS) {
            placeholderContainer.addView(placeholderTextView);
        } else {
            placeholderContainer.addView(placeholderRecyclerView);
        }


        if (trial > trialLimit) {
            trial = 0;
            taskTrial = taskTrial + 1;
            setTaskList(userNum);
            targetIndexList = Util.predefineRandom(listSize, trialLimit + 1);
            String taskEndIndicator = listSize + "-" + sourceType + "-" + changeKeyboardModeType(keyboardMode);
            taskEndView.setText(taskEndIndicator);
            listview.setVisibility(View.GONE);

            if (keyboardMode != TSI || keyboardMode != HTSI || keyboardMode != TSIS || keyboardMode != HTSIS) {
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
        } else {
            target = originSourceList.get(targetIndexList[trial]);
            String taskStartIndicator = (trial + 1) + "/" + (trialLimit + 1) + "\n" + target;
            if (taskTrial == 0 && trial == 0) {
                taskStartIndicator = listSize + "-" + sourceType + "-" + changeKeyboardModeType(keyboardMode) + "\n" + taskStartIndicator;
            }
            startView.setText(taskStartIndicator);
            startView.setVisibility(View.VISIBLE);
            startView.setOnTouchListener(null);
            taskView.setVisibility(View.GONE);
            inputString = "";
            err = 0;
            inputView.setText(inputString);
            placeholderTextView.setText(inputString);
            setResultAtListView(inputString);

            switch (keyboardMode) {
                case ST:
                    break;
                case LTSI:
                case ITSI:
                case TSI:
                case HTSI:
                case TSIS:
                case HTSIS:
                    placeholderTextView.setBackgroundColor(Color.WHITE);
                    break;
            }

            jobScheduler.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(() -> startView.setBackgroundColor(Color.parseColor("#f08080")));
                            startView.setOnTouchListener((v, event) -> {
                                switch (event.getAction()) {
                                    case MotionEvent.ACTION_UP:
                                        runOnUiThread(() -> {
                                            startView.setBackgroundColor(Color.parseColor("#ffffff"));
                                            startView.setVisibility(View.GONE);
                                            taskView.setVisibility(View.VISIBLE);
                                            if (keyboardMode != ST) {
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
}
