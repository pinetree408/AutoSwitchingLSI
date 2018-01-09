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
    TextView searchView;
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

    static final int LI = 0;
    static final int LSI = 1;
    static final int ISI = 2;
    static final int ILSI = 3;
    static final int LALSI = 4;
    static final int IALSI = 5;

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
        trialLimit = 2;
        err = 0;
        taskTrial = 0;

        listview = (ListView) findViewById(R.id.list_view);
        inputView = (TextView) findViewById(R.id.input);
        searchView = (TextView) findViewById(R.id.search);
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
        searchView.setText("");

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

        if (keyboardMode == ISI) {
            placeholderAdapter = new MyRecyclerViewAdapter(this, sourceList, true, sourceType);
        } else {
            placeholderAdapter = new MyRecyclerViewAdapter(this, sourceList);
        }
        placeholderAdapter.setClickListener((view, position) -> checkSelectedItem((TextView) view));
        placeholderRecyclerView.setAdapter(placeholderAdapter);

        if (keyboardMode == LALSI || keyboardMode == IALSI || keyboardMode == LSI || keyboardMode == ILSI) {
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
                        if (keyboardMode == ILSI) {
                            searchView.setText(String.valueOf(sourceList.size()));
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
        if (keyboardMode == ISI) {
            if (sourceType.equals("person")) {
                selected = selectedView.getText().toString().replace("\n", " ");
            } else {
                selected = selectedView.getText().toString().replace("-\n", "");
            }
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

                if (keyboardMode == LALSI || keyboardMode == IALSI || keyboardMode == LSI
                        || keyboardMode == ILSI || keyboardMode == ISI) {
                    keyboardContainer.setVisibility(View.VISIBLE);
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
            case LI:
                keyboardContainer.setVisibility(View.GONE);
                break;
            case LALSI:
            case IALSI:
            case LSI:
            case ILSI:
            case ISI:
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
                                    if (keyboardMode != ISI) {
                                        keyboardContainer.setVisibility(View.GONE);
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
                                    case LI:
                                        break;
                                    case LALSI:
                                    case IALSI:
                                    case LSI:
                                    case ISI:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            placeholderTextView.setText(sourceList.get(0));
                                        } else {
                                            placeholderTextView.setText("");
                                        }
                                        break;
                                    case ILSI:
                                        searchView.setText(String.valueOf(sourceList.size()));

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
                                    case LI:
                                    case LSI:
                                    case ISI:
                                        break;
                                    case ILSI:
                                        searchView.setText(String.valueOf(sourceList.size()));
                                        break;
                                    case LALSI:
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
                                    case IALSI:
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
        ArrayList<String> correctionSet = getCorrectionSet(inputString);

        if (inputString.equals("")) {
            sourceList.addAll(originSourceList);
        } else {
            ArrayList<String> tempList = new ArrayList<>();
            for (String item : originSourceList) {
                for (String corrected : correctionSet) {
                    if (item.replace(" ", "").startsWith(corrected)) {
                        if (!tempList.contains(item)) {
                            tempList.add(item);
                        }
                    }
                }
            }
            sourceList.addAll(tempList);
        }
        adapter.notifyDataSetChanged();
        listview.setSelectionAfterHeaderView();

        placeholderAdapter.notifyDataSetChanged();
        placeholderRecyclerView.getLayoutManager().scrollToPosition(0);
    }

    public ArrayList<String> getCorrectionSet(String inputString) {
        ArrayList<String> correctionSet = new ArrayList<>();
        ArrayList<ArrayList<Character>> charSet = new ArrayList<>();
        for (int i = 0; i < inputString.length(); i++) {
            charSet.add(getCloseChar(inputString.charAt(i)));
        }

        for (int i = 0; i < charSet.size(); i++) {
            for (int j = 0; j < charSet.get(i).size(); j++) {
                StringBuilder temp = new StringBuilder(inputString);
                temp.setCharAt(i, charSet.get(i).get(j));
                correctionSet.add(String.valueOf(temp));
            }
        }

        return correctionSet;
    }

    public ArrayList<Character> getCloseChar(char character) {
        ArrayList<Character> closeSet = new ArrayList<>();
        int row_pos = -1;
        int col_pos = -1;
        for (int i = 0; i < keyBoardView.keyboardChar.length; i++) {
            char[] row = keyBoardView.keyboardChar[i];
            for (int j = 0; j < row.length; j++) {
                if (row[j] == character) {
                    row_pos = i;
                    col_pos = j;
                    break;
                }
            }
        }

        for (int i = 0; i < keyBoardView.keyboardChar.length; i++) {
            char[] row = keyBoardView.keyboardChar[i];
            for (int j = 0; j < row.length; j++) {
                if (i == row_pos - 1 || i == row_pos || i == row_pos + 1) {
                    if (j == col_pos - 1 || j == col_pos || j == col_pos + 1) {
                        closeSet.add(row[j]);
                    }
                }
            }
        }

        return closeSet;
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
            case "LALSI":
                keyboardMode = LALSI;
                break;
            case "IALSI":
                keyboardMode = IALSI;
                break;
            case "LI":
                keyboardMode = LI;
                break;
            case "LSI":
                keyboardMode = LSI;
                break;
            case "ILSI":
                keyboardMode = ILSI;
                break;
            case "ISI":
                keyboardMode = ISI;
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
            case LALSI:
                keyboardModeString = "LALSI";
                break;
            case IALSI:
                keyboardModeString = "IALSI";
                break;
            case LI:
                keyboardModeString = "LI";
                break;
            case LSI:
                keyboardModeString = "LSI";
                break;
            case ILSI:
                keyboardModeString = "ILSI";
                break;
            case ISI:
                keyboardModeString = "ISI";
                break;
        }
        return keyboardModeString;
    }

    public void setNextTask() {

        searchView.setText("");

        placeholderContainer.removeAllViews();

        if (keyboardMode != ISI) {
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

            if (keyboardMode != LSI || keyboardMode != ILSI || keyboardMode != ISI) {
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
                case LI:
                    break;
                case LALSI:
                case IALSI:
                case LSI:
                case ILSI:
                case ISI:
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
                                            if (keyboardMode != LI) {
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
