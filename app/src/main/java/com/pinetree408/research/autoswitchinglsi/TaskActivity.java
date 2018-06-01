package com.pinetree408.research.autoswitchinglsi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
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
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pinetree408.research.autoswitchinglsi.databinding.ActivityTaskBinding;
import com.pinetree408.research.autoswitchinglsi.exp.source.Source;
import com.pinetree408.research.autoswitchinglsi.exp.tasklist.ExpFourTaskList;
import com.pinetree408.research.autoswitchinglsi.util.Logger;
import com.pinetree408.research.autoswitchinglsi.util.Util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class TaskActivity extends WearableActivity {
    private static final String TAG = TaskActivity.class.getSimpleName();

    static final int REQUEST_CODE_FILE = 1;

    ActivityTaskBinding binding;

    // Gesture Variable
    private int dragThreshold = 30;

    private long touchDownTime;
    private float touchDownX, touchDownY;

    // View Variable
    ArrayList<String> originSourceList;
    ArrayList<String> sourceList;

    ArrayAdapter<String> adapter;

    String inputString;

    // Task Variable
    private Timer jobScheduler;
    String target;
    int[] targetIndexList;

    static final int LI = 0;
    static final int LSI = 1;
    static final int ONE_ALSI = 3;
    static final int TWO_ALSI = 4;
    static final int THREE_ALSI = 5;

    int keyboardMode;
    View returnKeyboardView;

    // Exp Variable
    int listSize;
    int userNum;
    int trial;
    int trialLimit;
    long startTime;
    String sourceType;
    int taskTrial;
    Logger logger;
    long autoToListTime;

    int err;

    boolean isSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAmbientEnabled();
        checkPermission();

        Intent intent = new Intent(this.getIntent());
        userNum =intent.getIntExtra("userNum", 0);

        jobScheduler = new Timer();

        inputString = "";
        listSize = 0;
        trial = 0;
        trialLimit = 7;
        taskTrial = 0;
        err = 0;

        isSuccess = false;

        binding = DataBindingUtil.setContentView(this, R.layout.activity_task);

        returnKeyboardView = new TextView(this);

        setupDynamicView();

        setupLogger(userNum);

        if (userNum == 0) {
            trialLimit = 2;
        }

        setTaskList(userNum);

        sourceList = new ArrayList<>();
        initSourceList();
        setupListView();
        initListView();
        setupKeyboardContainer();
        initKeyboardContainer();

        targetIndexList = Util.predefineRandom(listSize, trialLimit + 1);

        String taskEndIndicator = listSize + "\n" + changeKeyboardModeType(keyboardMode);
        binding.taskEnd.setText(taskEndIndicator);
        binding.taskEnd.setVisibility(View.VISIBLE);
        binding.taskEnd.bringToFront();

        new Handler().postDelayed(() -> {
            binding.taskEnd.setVisibility(View.GONE);
            setNextTask();
        }, 5000);
    }

    public void setupLogger(int userNum) {
        String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile() + "/";
        String fileFormat = "block, trial, poolSize, tech, eventTime, target, inputKey, inputString, listSize, index";
        String fileName = "result_bb_" + userNum + ".csv";
        logger = new Logger(filePath, fileName);
        logger.fileOpen(userNum);
        logger.fileWriteHeader(fileFormat);
    }

    public void setupListView() {
        adapter = new ArrayAdapter<>(this, R.layout.listview_item, sourceList);
        binding.listView.setAdapter(adapter);
        binding.listView.setOnItemClickListener((parent, view, position, id) -> {
            if ((System.currentTimeMillis() - startTime) >= 200) {
                switch(keyboardMode) {
                    case LI:
                    case LSI:
                        checkSelectedItem((TextView) view);
                        break;
                    case ONE_ALSI:
                    case TWO_ALSI:
                    case THREE_ALSI:
                        if ((System.currentTimeMillis() - autoToListTime) >= 500) {
                            checkSelectedItem((TextView) view);
                        }
                        break;
                }
            }
        });

        binding.listView.setOnTouchListener((v, event) -> {
            if (trial == 0 || isSuccess) {
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    logger.fileWriteLog(
                            taskTrial,
                            trial,
                            originSourceList.size(),
                            changeKeyboardModeTypeAtLog(keyboardMode),
                            (System.currentTimeMillis() - startTime),
                            target,
                            "touch-list",
                            inputString,
                            binding.listView.getAdapter().getCount(),
                            sourceList.indexOf(target)
                    );
                    v.performClick();
                    break;
            }
            return false;
        });
    }

    public void setupKeyboardContainer() {
        binding.keyboardContainer.setOnTouchListener((v, event) -> {
            if (trial == 0 || isSuccess) {
                return true;
            }

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
                            if (tempY < binding.placeHolder.getY()) {
                                if ((binding.keyboard.getX() + (binding.keyboard.getWidth() / 2)) < tempX) {
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            originSourceList.size(),
                                            changeKeyboardModeTypeAtLog(keyboardMode),
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            "to-list",
                                            inputString,
                                            binding.listView.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    binding.keyboardContainer.setVisibility(View.GONE);
                                }
                            } else if ((binding.placeHolder.getY() <= tempY) && (tempY < binding.keyboard.getY())) {
                                checkSelectedItem(binding.placeHolder);
                            } else if (binding.keyboard.getY() + binding.keyboard.getHeight() < tempY) {
                                logger.fileWriteLog(
                                        taskTrial,
                                        trial,
                                        originSourceList.size(),
                                        changeKeyboardModeTypeAtLog(keyboardMode),
                                        (System.currentTimeMillis() - startTime),
                                        target,
                                        "delete",
                                        inputString,
                                        binding.listView.getAdapter().getCount(),
                                        sourceList.indexOf(target)
                                );
                                if (inputString.length() != 0) {
                                    inputString = inputString.substring(0, inputString.length() - 1);
                                }
                                setResultAtListView(inputString);

                                switch (keyboardMode) {
                                    case LI:
                                        break;
                                    case LSI:
                                    case ONE_ALSI:
                                    case TWO_ALSI:
                                    case THREE_ALSI:
                                        binding.input.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            binding.placeHolder.setText(sourceList.get(0));
                                        } else {
                                            binding.placeHolder.setText("");
                                        }
                                        break;
                                }

                                switch (keyboardMode) {
                                    case LI:
                                    case LSI:
                                        break;
                                    case ONE_ALSI:
                                        if (sourceList.size() <= 3 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case TWO_ALSI:
                                        if (sourceList.size() <= 6 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case THREE_ALSI:
                                        if (sourceList.size() <= 12 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                }
                            } else {
                                String[] params = getInputInfo(event);
                                if (params[0].equals(".")) {
                                    logger.fileWriteLog(
                                            taskTrial,
                                            trial,
                                            originSourceList.size(),
                                            changeKeyboardModeTypeAtLog(keyboardMode),
                                            (System.currentTimeMillis() - startTime),
                                            target,
                                            params[0],
                                            inputString,
                                            binding.listView.getAdapter().getCount(),
                                            sourceList.indexOf(target)
                                    );
                                    break;
                                }
                                inputString += params[0];
                                setResultAtListView(inputString);
                                logger.fileWriteLog(
                                        taskTrial,
                                        trial,
                                        originSourceList.size(),
                                        changeKeyboardModeTypeAtLog(keyboardMode),
                                        (System.currentTimeMillis() - startTime),
                                        target,
                                        params[0],
                                        inputString,
                                        binding.listView.getAdapter().getCount(),
                                        sourceList.indexOf(target)
                                );
                                binding.input.setText(inputString);
                                if (sourceList.size() != 0) {
                                    binding.placeHolder.setText(sourceList.get(0));
                                } else {
                                    binding.placeHolder.setText("");
                                }
                                switch (keyboardMode) {
                                    case LI:
                                    case LSI:
                                        break;
                                    case ONE_ALSI:
                                        if (sourceList.size() <= 3 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case TWO_ALSI:
                                        if (sourceList.size() <= 6 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
                                        }
                                        break;
                                    case THREE_ALSI:
                                        if (sourceList.size() <= 12 && sourceList.size() > 0) {
                                            logger.fileWriteLog(
                                                    taskTrial,
                                                    trial,
                                                    originSourceList.size(),
                                                    changeKeyboardModeTypeAtLog(keyboardMode),
                                                    (System.currentTimeMillis() - startTime),
                                                    target,
                                                    "auto-switch",
                                                    inputString,
                                                    binding.listView.getAdapter().getCount(),
                                                    sourceList.indexOf(target)
                                            );
                                            autoToListTime = System.currentTimeMillis();
                                            binding.keyboardContainer.setVisibility(View.GONE);
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

    public void initSourceList() {
        originSourceList = new ArrayList<>(Arrays.asList(Source.name));
        Collections.shuffle(originSourceList);
        originSourceList = new ArrayList<>(originSourceList.subList(listSize, listSize * 2));
        Collections.sort(originSourceList);
        sourceList.addAll(originSourceList);
    }

    public void initListView() {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        if (keyboardMode != LI) {
            if (returnKeyboardView.getParent() == null) {
                params.setMargins(0, 44, 0, 0);
                binding.listView.setLayoutParams(params);
                binding.task.addView(returnKeyboardView, 1);
            }
        } else {
            if (returnKeyboardView.getParent() !=  null) {
                params.setMargins(0, 0, 0, 0);
                binding.listView.setLayoutParams(params);
                binding.task.removeView(returnKeyboardView);
            }
        }
    }

    public void setupDynamicView() {
        returnKeyboardView.setMinimumHeight(44);
        returnKeyboardView.setMinimumWidth(320);
        returnKeyboardView.setBackgroundColor(Color.WHITE);
        ((TextView)returnKeyboardView).setHeight(44);
        ((TextView)returnKeyboardView).setWidth(320);
        ((TextView)returnKeyboardView).setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.search, 0, 0);
        ((TextView)returnKeyboardView).setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        returnKeyboardView.setOnTouchListener((v, event) -> {
            if (trial == 0 || isSuccess) {
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    logger.fileWriteLog(
                            taskTrial,
                            trial,
                            originSourceList.size(),
                            changeKeyboardModeTypeAtLog(keyboardMode),
                            (System.currentTimeMillis() - startTime),
                            target,
                            "to-keyboard",
                            inputString,
                            binding.listView.getAdapter().getCount(),
                            sourceList.indexOf(target)
                    );
                    binding.keyboardContainer.setVisibility(View.VISIBLE);
                    v.performClick();
                    break;
            }
            return true;
        });
    }

    public void checkSelectedItem(TextView selectedView) {

        if (trial == 0 || isSuccess) {
            return;
        }

        String selected = selectedView.getText().toString().replace("\n", " ");

        if (selected.equals(target)) {
            logger.fileWriteLog(
                    taskTrial,
                    trial,
                    originSourceList.size(),
                    changeKeyboardModeTypeAtLog(keyboardMode),
                    (System.currentTimeMillis() - startTime),
                    target,
                    "item-success",
                    inputString,
                    binding.listView.getAdapter().getCount(),
                    sourceList.indexOf(target)
            );
            selectedView.setBackgroundColor(Color.parseColor("#f0FF00"));
            isSuccess = true;
            new Handler().postDelayed(() -> {
                selectedView.setBackgroundColor(Color.WHITE);
                setNextTask();
            }, 1000);
        } else {
            logger.fileWriteLog(
                    taskTrial,
                    trial,
                    originSourceList.size(),
                    changeKeyboardModeTypeAtLog(keyboardMode),
                    (System.currentTimeMillis() - startTime),
                    target,
                    "item-err",
                    inputString,
                    binding.listView.getAdapter().getCount(),
                    sourceList.indexOf(target)
            );

            binding.listView.setSelectionAfterHeaderView();
            err = err + 1;
            String styledText = target + "<br/><font color='red'>" + err + "/5</font>";
            binding.err.setText(Html.fromHtml(styledText));
            binding.task.setVisibility(View.GONE);
            binding.err.setVisibility(View.VISIBLE);

            new Handler().postDelayed(() -> {
                startTime = System.currentTimeMillis();
                binding.task.setVisibility(View.VISIBLE);
                if (keyboardMode != LI) {
                    binding.keyboardContainer.setVisibility(View.VISIBLE);
                    inputString = "";
                    setResultAtListView(inputString);
                    binding.input.setText(inputString);
                    if (sourceList.size() != 0 && !inputString.equals("")) {
                        binding.placeHolder.setText(sourceList.get(0));
                    } else {
                        binding.placeHolder.setText("");
                    }
                }
                binding.err.setVisibility(View.GONE);
                if (err > 4) {
                    err = 0;
                    setNextTask();
                }
            }, 1000);
        }
    }

    public void initKeyboardContainer() {
        binding.keyboardContainer.bringToFront();
        if (keyboardMode == LI) {
            binding.keyboardContainer.setVisibility(View.GONE);
        } else {
            binding.keyboardContainer.setVisibility(View.VISIBLE);
        }

        binding.search.setVisibility(View.VISIBLE);
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
        binding.listView.setSelectionAfterHeaderView();
    }

    public String[] getInputInfo(MotionEvent event) {
        double tempX = (double) event.getAxisValue(MotionEvent.AXIS_X);
        double tempY = (double) event.getAxisValue(MotionEvent.AXIS_Y);
        String input = binding.keyboard.getKey(tempX - binding.keyboard.getX(), tempY - binding.keyboard.getY());

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
            case "LI":
                keyboardMode = LI;
                break;
            case "LSI":
                keyboardMode = LSI;
                break;
            case "1-ALSI":
                keyboardMode = ONE_ALSI;
                break;
            case "2-ALSI":
                keyboardMode = TWO_ALSI;
                break;
            case "3-ALSI":
                keyboardMode = THREE_ALSI;
                break;
        }
    }

    public void setTaskList(int userNum) {
        try {
            Field[] fields = ExpFourTaskList.class.getDeclaredFields();
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
            case LI:
                keyboardModeString = "List";
                break;
            case LSI:
                keyboardModeString = "Manual switch";
                break;
            case ONE_ALSI:
                keyboardModeString = "Auto switch on list size 3";
                break;
            case TWO_ALSI:
                keyboardModeString = "Auto switch on list size 6";
                break;
            case THREE_ALSI:
                keyboardModeString = "Auto switch on list size 12";
                break;
        }
        return keyboardModeString;
    }

    public String changeKeyboardModeTypeAtLog(int keyboardMode) {
        String keyboardModeString = "";
        switch(keyboardMode) {
            case LI:
                keyboardModeString = "LI";
                break;
            case LSI:
                keyboardModeString = "LSI";
                break;
            case ONE_ALSI:
                keyboardModeString = "1-ALSI";
                break;
            case TWO_ALSI:
                keyboardModeString = "2-ALSI";
                break;
            case THREE_ALSI:
                keyboardModeString = "3-ALSI";
                break;
        }
        return keyboardModeString;
    }

    public void setNextTask() {

        if (trial > trialLimit) {
            trial = 0;
            taskTrial = taskTrial + 1;
            setTaskList(userNum);
            targetIndexList = Util.predefineRandom(listSize, trialLimit + 1);

            String taskEndIndicator = listSize + "\n" + changeKeyboardModeType(keyboardMode);
            binding.taskEnd.setText(taskEndIndicator);
            binding.taskEnd.setVisibility(View.VISIBLE);
            binding.taskEnd.bringToFront();

            new Handler().postDelayed(() -> {
                binding.taskEnd.setVisibility(View.GONE);
                initSourceList();
                initListView();
                initKeyboardContainer();
                setNextTask();
            }, 5000);
        } else {
            target = originSourceList.get(targetIndexList[trial]);

            String taskStartIndicator = (trial + 1) + "/" + (trialLimit + 1) + "\n" + target;
            binding.start.setText(taskStartIndicator);
            binding.start.setVisibility(View.VISIBLE);
            binding.start.bringToFront();

            switch(keyboardMode) {
                case LI:
                case LSI:
                    binding.indicator.setText("");
                    break;
                case ONE_ALSI:
                    binding.indicator.setText("3");
                    break;
                case TWO_ALSI:
                    binding.indicator.setText("6");
                    break;
                case THREE_ALSI:
                    binding.indicator.setText("12");
                    break;
            }

            err = 0;

            inputString = "";
            binding.input.setText("");
            binding.search.setText("");
            binding.placeHolder.setText("");
            setResultAtListView("");

            jobScheduler.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(() -> binding.start.setBackgroundColor(Color.parseColor("#f08080")));
                            binding.start.setOnTouchListener((v, event) -> {
                                switch (event.getAction()) {
                                    case MotionEvent.ACTION_UP:
                                        runOnUiThread(() -> {
                                            isSuccess = false;
                                            trial = trial + 1;
                                            startTime = System.currentTimeMillis();

                                            binding.start.setOnTouchListener(null);
                                            binding.start.setBackgroundColor(Color.WHITE);
                                            binding.start.setVisibility(View.GONE);

                                            if (keyboardMode != LI) {
                                                binding.keyboardContainer.setVisibility(View.VISIBLE);
                                            }
                                        });
                                        v.performClick();
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

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    ) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Read/Write external storage", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_CODE_FILE);
            }
        }
    }
}
