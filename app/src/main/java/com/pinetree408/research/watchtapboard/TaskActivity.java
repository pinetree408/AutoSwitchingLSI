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
import com.pinetree408.research.watchtapboard.exp.tasklist.ExpOneTaskList;
import com.pinetree408.research.watchtapboard.util.KeyBoardView;
import com.pinetree408.research.watchtapboard.util.Logger;
import com.pinetree408.research.watchtapboard.util.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import io.socket.client.IO;
import io.socket.client.Socket;

public class TaskActivity extends WearableActivity {
    private static final String TAG = TaskActivity.class.getSimpleName();

    static final int REQUEST_CODE_FILE = 1;

    String ip = "143.248.56.249";
    int port = 5000;

    Socket socket;

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

    int keyboardMode;
    TextView returnKeyboardView;

    TextView startView;
    View taskView;

    // Exp Variable
    int listSize;
    int userNum;
    int trial;
    int trialLimit;
    TextView taskEndView;
    long startTime;
    String sourceType;
    int taskTrial;
    Logger logger;

    boolean isSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        setAmbientEnabled();
        checkPermission();

        try {
            socket = IO.socket("http://" + ip + ":" + port + "/mynamespace");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        socket.on(Socket.EVENT_CONNECT, (Object... args) -> Log.d(TAG, "connect")
        ).on("response",  (Object... args) -> Log.d(TAG, "response")
        ).on(Socket.EVENT_DISCONNECT, (Object... args) -> {});
        socket.connect();

        Intent intent = new Intent(this.getIntent());
        userNum =intent.getIntExtra("userNum", 0);

        jobScheduler = new Timer();

        listSize = 0;
        trial = 0;
        trialLimit = 7;
        taskTrial = 0;

        isSuccess = false;

        // list Interface
        listview = (ListView) findViewById(R.id.list_view);

        // Search Interface
        keyboardContainer = findViewById(R.id.keyboard_container);
        inputView = (TextView) findViewById(R.id.input);
        searchView = (TextView) findViewById(R.id.search);

        returnKeyboardView = new TextView(this);
        placeholderContainer = (LinearLayout) findViewById(R.id.place_holder);
        placeholderTextView = new TextView(this);
        placeholderRecyclerView = new RecyclerView(this);
        setupDynamicView();

        keyBoardView = (KeyBoardView) findViewById(R.id.tapboard);

        // Task Interface
        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);
        taskEndView = (TextView) findViewById(R.id.task_end);

        // Init
        taskEndView.setVisibility(View.GONE);

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
        setNextTask();
    }

    public void setupLogger(int userNum) {
        String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile() + "/";
        String fileFormat = "block, trial, eventTime, target, inputKey, listSize, index";
        String fileName = "result_bb_" + userNum + ".csv";
        logger = new Logger(filePath, fileName);
        logger.fileOpen(userNum);
        logger.fileWriteHeader(fileFormat);
    }

    public void setupListView() {
        adapter = new ArrayAdapter<String>(this, R.layout.listview_item, sourceList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tempView = (TextView) view;
                String tempString = tempView.getText().toString();
                if (inputString.length() > 0) {
                    if (tempString.split(" ")[0].startsWith(inputString)) {
                        String sourceString = "<font color='green'><b>" + inputString + "</b></font>" + tempString.substring(inputString.length(), tempString.length());
                        tempView.setText(Html.fromHtml(sourceString));
                    } else if (tempString.split(" ")[1].startsWith(inputString)) {
                        String sourceString = tempString.split(" ")[0] + " <font color='green'><b>" + inputString + "</b></font>" + tempString.split(" ")[1].substring(inputString.length(), tempString.split(" ")[1].length());
                        tempView.setText(Html.fromHtml(sourceString));
                    }
                }
                return view;
            }
        };
        listview.setAdapter(adapter);
        listview.setOnItemClickListener((parent, view, position, id) -> {
            if ((System.currentTimeMillis() - startTime) >= 200) {
                checkSelectedItem((TextView) view);
            }
        });

        placeholderAdapter = new MyRecyclerViewAdapter(this, sourceList);
        placeholderAdapter.setClickListener((view, position) -> checkSelectedItem((TextView) view));
        placeholderRecyclerView.setAdapter(placeholderAdapter);
    }

    public void setupKeyboardContainer() {
        keyboardContainer.setOnTouchListener((v, event) -> {

            if (isSuccess) {
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
                                    case LSI:
                                    case ISI:
                                        inputView.setText(inputString);
                                        if (sourceList.size() != 0 && !inputString.equals("")) {
                                            String tempString = sourceList.get(0);
                                            if (tempString.split(" ")[0].startsWith(inputString)) {
                                                String sourceString = "<font color='green'><b>" + inputString + "</b></font>" + tempString.substring(inputString.length(), tempString.length());
                                                placeholderTextView.setText(Html.fromHtml(sourceString));
                                            } else if (tempString.split(" ")[1].startsWith(inputString)) {
                                                String sourceString = tempString.split(" ")[0] + " <font color='green'><b>" + inputString + "</b></font>" + tempString.split(" ")[1].substring(inputString.length(), tempString.split(" ")[1].length());
                                                placeholderTextView.setText(Html.fromHtml(sourceString));
                                            } else {
                                                placeholderTextView.setText(sourceList.get(0));
                                            }
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
                                inputView.setText(inputString);
                                if (sourceList.size() != 0) {
                                    String tempString = sourceList.get(0);
                                    if (tempString.split(" ")[0].startsWith(inputString)) {
                                        String sourceString = "<font color='green'><b>" + inputString + "</b></font>" + tempString.substring(inputString.length(), tempString.length());
                                        placeholderTextView.setText(Html.fromHtml(sourceString));
                                    } else if (tempString.split(" ")[1].startsWith(inputString)) {
                                        String sourceString = tempString.split(" ")[0] + " <font color='green'><b>" + inputString + "</b></font>" + tempString.split(" ")[1].substring(inputString.length(), tempString.split(" ")[1].length());
                                        placeholderTextView.setText(Html.fromHtml(sourceString));
                                    } else {
                                        placeholderTextView.setText(sourceList.get(0));
                                    }
                                } else {
                                    placeholderTextView.setText("");
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
        originSourceList = new ArrayList<>(Arrays.asList(Source.fullName));
        originSourceList = new ArrayList<>(originSourceList.subList(listSize, listSize * 2));
        Collections.sort(originSourceList);
        sourceList.addAll(originSourceList);
    }

    public void setSentence() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("data", target);
            obj.put("type", "Sentence");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("request", obj);
    }

    public void initListView() {
        if (keyboardMode != LI) {
            if (returnKeyboardView.getParent() == null) {
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        80,
                        44
                );
                params.setMargins(240, 0, 0, 0);

                ViewGroup taskViewGroup = (ViewGroup) findViewById(R.id.task);
                taskViewGroup.addView(returnKeyboardView, 1, params);
                returnKeyboardView.bringToFront();
            }
        } else {
            if (returnKeyboardView.getParent() !=  null) {
                ((ViewGroup) returnKeyboardView.getParent()).removeView(returnKeyboardView);
            }
        }
    }

    public void setupDynamicView() {
        returnKeyboardView.setHeight(44);
        returnKeyboardView.setMinimumHeight(44);
        returnKeyboardView.setWidth(80);
        returnKeyboardView.setMinimumWidth(80);
        returnKeyboardView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.search, 0, 0);
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
                    break;
            }
            return true;
        });

        placeholderTextView.setHeight(64);
        placeholderTextView.setMinimumHeight(64);
        placeholderTextView.setWidth(320);
        placeholderTextView.setMinimumWidth(320);
        placeholderTextView.setTextColor(Color.BLACK);
        placeholderTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        placeholderRecyclerView.setMinimumHeight(64);
        placeholderRecyclerView.setMinimumWidth(320);
        placeholderRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    }

    public void checkSelectedItem(TextView selectedView) {

        if (isSuccess) {
            return;
        }

        String selected = selectedView.getText().toString().replace("\n", " ");

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
            isSuccess = true;
            new Handler().postDelayed(() -> {
                selectedView.setBackgroundColor(Color.WHITE);
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
            selectedView.setBackgroundColor(Color.parseColor("#ff0F00"));
            new Handler().postDelayed(() -> selectedView.setBackgroundColor(Color.WHITE), 500);
        }
    }

    public void initKeyboardContainer() {
        keyboardContainer.bringToFront();
        if (keyboardMode == LI) {
            keyboardContainer.setVisibility(View.GONE);
        } else {
            keyboardContainer.setVisibility(View.VISIBLE);
        }

        placeholderContainer.removeAllViews();
        if (keyboardMode != ISI) {
            placeholderContainer.addView(placeholderTextView);
            searchView.setVisibility(View.VISIBLE);
        } else {
            placeholderContainer.addView(placeholderRecyclerView);
            searchView.setVisibility(View.INVISIBLE);
        }
    }

    public void setResultAtListView(String inputString) {
        sourceList.clear();

        if (inputString.equals("")) {
            sourceList.addAll(originSourceList);
        } else {
            ArrayList<String> tempList = new ArrayList<>();
            for (String item : originSourceList) {
                if (item.replace(" ", "").startsWith(inputString)) {
                    if (!tempList.contains(item)) {
                        tempList.add(item);
                    }
                }
            }
            for (String item : originSourceList) {
                if (item.split(" ")[1].startsWith(inputString)) {
                    if (!tempList.contains(item)) {
                        tempList.add(item);
                    }
                }
            }
            ArrayList<String> correctionSet = getCorrectionSet(inputString);
            for (String item : originSourceList) {
                for (String corrected : correctionSet) {
                    if (item.replace(" ", "").startsWith(corrected)
                            || item.split(" ")[1].startsWith(corrected)) {
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

        placeholderAdapter.setInputString(inputString);
        placeholderAdapter.notifyDataSetChanged();
        placeholderRecyclerView.getLayoutManager().scrollToPosition(0);
    }

    public ArrayList<String> getCorrectionSet(String inputString) {
        ArrayList<String> correctionSet = new ArrayList<>();
        ArrayList<Character[]> charSet = new ArrayList<>();
        for (int i = 0; i < inputString.length(); i++) {
            charSet.add(getCloseCharFromKeyboard(inputString.charAt(i)));
        }

        for (int i = 0; i < charSet.size(); i++) {
            for (int j = 0; j < charSet.get(i).length; j++) {
                StringBuilder temp = new StringBuilder(inputString);
                temp.setCharAt(i, charSet.get(i)[j]);
                correctionSet.add(String.valueOf(temp));
            }
        }

        Collections.sort(correctionSet);

        return correctionSet;
    }

    public Character[] getCloseCharFromKeyboard(char character) {
        Character[] closeSet = new Character[]{};
        switch (character) {
            case 'a':
                closeSet = new Character[] {'q', 'w', 's', 'z'};
                break;
            case 'b':
                closeSet = new Character[] {'n', 'h', 'g', 'v'};
                break;
            case 'c':
                closeSet = new Character[] {'x', 'd', 'f', 'v'};
                break;
            case 'd':
                closeSet = new Character[] {'e', 'r', 's', 'f', 'x', 'c'};
                break;
            case 'e':
                closeSet = new Character[] {'w', 'r', 's', 'd'};
                break;
            case 'f':
                closeSet = new Character[] {'r', 't', 'g', 'v', 'c', 'd'};
                break;
            case 'g':
                closeSet = new Character[] {'t', 'y', 'h', 'b', 'v', 'f'};
                break;
            case 'h':
                closeSet = new Character[] {'y', 'u', 'j', 'n', 'b', 'g'};
                break;
            case 'i':
                closeSet = new Character[] {'u', 'j', 'k', 'o'};
                break;
            case 'j':
                closeSet = new Character[] {'u', 'i', 'k', 'm', 'n', 'h'};
                break;
            case 'k':
                closeSet = new Character[] {'i', 'o', 'l', 'm', 'j'};
                break;
            case 'l':
                closeSet = new Character[] {'p', 'o', 'k'};
                break;
            case 'n':
                closeSet = new Character[] {'b', 'h', 'j', 'm'};
                break;
            case 'm':
                closeSet = new Character[] {'n', 'j', 'k', 'l'};
                break;
            case 'o':
                closeSet = new Character[] {'i', 'k', 'l', 'p'};
                break;
            case 'p':
                closeSet = new Character[] {'o', 'l'};
                break;
            case 'q':
                closeSet = new Character[] {'w', 'a'};
                break;
            case 'r':
                closeSet = new Character[] {'e', 'd', 'f', 't'};
                break;
            case 's':
                closeSet = new Character[] {'w', 'e', 'a', 'd', 'z', 'x'};
                break;
            case 't':
                closeSet = new Character[] {'r', 'f', 'g', 'y'};
                break;
            case 'u':
                closeSet = new Character[] {'y', 'h', 'j', 'i'};
                break;
            case 'v':
                closeSet = new Character[] {'c', 'f', 'g', 'b'};
                break;
            case 'w':
                closeSet = new Character[] {'q', 'a', 's', 'e'};
                break;
            case 'x':
                closeSet = new Character[] {'z', 's', 'd', 'c'};
                break;
            case 'y':
                closeSet = new Character[] {'t', 'g', 'h', 'u'};
                break;
            case 'z':
                closeSet = new Character[] {'a', 's', 'x'};
                break;
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
            case "LI":
                keyboardMode = LI;
                break;
            case "LSI":
                keyboardMode = LSI;
                break;
            case "ISI":
                keyboardMode = ISI;
                break;
        }
    }

    public void setTaskList(int userNum) {
        try {
            Field[] fields = ExpOneTaskList.class.getDeclaredFields();
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
                keyboardModeString = "List with search field";
                break;
            case ISI:
                keyboardModeString = "Integrated search";
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
            taskEndView.setText(taskEndIndicator);
            taskEndView.setVisibility(View.VISIBLE);
            taskEndView.bringToFront();

            new Handler().postDelayed(() -> {
                taskEndView.setVisibility(View.GONE);
                initSourceList();
                initListView();
                initKeyboardContainer();
                setNextTask();
            }, 5000);
        } else {
            taskView.setVisibility(View.GONE);

            target = originSourceList.get(targetIndexList[trial]);
            setSentence();

            String taskStartIndicator = (trial + 1) + "/" + (trialLimit + 1) + "\n" + target;
            if (taskTrial == 0 && trial == 0) {
                taskStartIndicator = listSize + "\n" + changeKeyboardModeType(keyboardMode) + "\n" + taskStartIndicator;
            }
            startView.setText(taskStartIndicator);
            startView.setVisibility(View.VISIBLE);
            startView.bringToFront();

            inputString = "";
            inputView.setText("");
            searchView.setText("");
            placeholderTextView.setText("");
            setResultAtListView("");

            jobScheduler.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(() -> startView.setBackgroundColor(Color.parseColor("#f08080")));
                            startView.setOnTouchListener((v, event) -> {
                                switch (event.getAction()) {
                                    case MotionEvent.ACTION_UP:
                                        runOnUiThread(() -> {
                                            startView.setOnTouchListener(null);
                                            startView.setBackgroundColor(Color.WHITE);
                                            startView.setVisibility(View.GONE);

                                            taskView.setVisibility(View.VISIBLE);
                                            if (keyboardMode != LI) {
                                                keyboardContainer.setVisibility(View.VISIBLE);
                                            }
                                            isSuccess = false;
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
