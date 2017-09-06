package com.pinetree408.research.watchtapboard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity  {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Gesture Variable
    private int dragThreshold = 30;
    private final double angleFactor = (double) 180/Math.PI;

    private long touchDownTime;
    private float touchDownX, touchDownY;
    private float scrollY;

    // View Variable
    ArrayList<String> originSourceList;
    ArrayList<String> sourceList;

    ArrayAdapter<String> adapter;
    ListView listview;

    TextView placehoderView;
    TapBoardView tapBoardView;

    String inputString;
    View keyboardContainer;

    // Task Variable
    Random random;
    String target;
    // 0 : tapboard
    // 1 : result based
    // 2 : input based
    // 3 : Swipe & Tap
    // 4 : TSI
    int keyboardMode;
    Map<String, Integer> mapIndex;

    Toast toast;
    TextView startView;
    View taskView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        random = new Random();
        initSourceList();

        listview = (ListView) findViewById(R.id.list_view);
        placehoderView = (TextView) findViewById(R.id.place_holder);
        tapBoardView = (TapBoardView) findViewById(R.id.tapboard);
        keyboardContainer = findViewById(R.id.keyboard_container);

        startView = (TextView) findViewById(R.id.start);
        taskView = findViewById(R.id.task);

        initListView();
        initStartView();

        initTaskSelectorView();
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
                String text = "<font color=#00FF00>" + inputString + "</font>" + initText.replace(inputString, "");
                tv.setText(Html.fromHtml(text));
                return view;
            }
        };
        listview.setAdapter(adapter);
        listview.setBackgroundColor(Color.parseColor("#d3d3d3"));
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView selectedView = (TextView) view;
                String selected = selectedView.getText().toString();
                if (selected.equals(target)) {
                    target = originSourceList.get(random.nextInt(originSourceList.size()));
                    startView.setText(target);
                    startView.setVisibility(View.VISIBLE);
                    taskView.setVisibility(View.GONE);
                    inputString = "";
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
                            keyboardContainer.setVisibility(View.VISIBLE);
                            break;
                    }
                } else {
                    selectedView.setBackgroundColor(Color.parseColor("#f08080"));
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
                                                tapBoardView.setVisibility(View.VISIBLE);
                                                keyboardContainer.setBackgroundColor(Color.WHITE);
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
                                        boolean under = touchDownY <= (childView.getY() + childView.getHeight());
                                        boolean over = touchDownY >= childView.getY();
                                        if (under && over) {
                                            if (childView.getText().toString().equals(target)) {
                                                target = originSourceList.get(random.nextInt(originSourceList.size()));
                                                startView.setText(target);
                                                startView.setVisibility(View.VISIBLE);
                                                taskView.setVisibility(View.GONE);
                                                inputString = "";
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
                                            } else {
                                                childView.setBackgroundColor(Color.parseColor("#f08080"));
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
                                        if (sourceList.size() <= 10) {
                                            if (sourceList.size() != 0) {
                                                tapBoardView.setVisibility(View.GONE);
                                                keyboardContainer.setBackgroundColor(Color.parseColor("#00000000"));
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 2) {
                                        if (inputString.length() > 2) {
                                            if (sourceList.size() != 0) {
                                                tapBoardView.setVisibility(View.GONE);
                                                keyboardContainer.setBackgroundColor(Color.parseColor("#00000000"));
                                            } else {
                                                showNoItemsMessage();
                                            }
                                        }
                                    } else if (keyboardMode == 4) {
                                        if (sourceList.size() == 0) {
                                            placehoderView.setText(inputString);
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

    public void initTaskSelectorView() {
        startView.setVisibility(View.GONE);
        taskView.setVisibility(View.GONE);
        final ViewGroup taskSelectorView = (ViewGroup) findViewById(R.id.task_selector);
        for (int i = 0; i < taskSelectorView.getChildCount(); i++) {
            final TextView childView = (TextView) taskSelectorView.getChildAt(i);
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
                                        }
                                        if (keyboardMode == 3) {
                                            getIndexList();
                                            displayIndex();
                                        } else {
                                            View sideIndex = findViewById(R.id.side_index);
                                            sideIndex.setVisibility(View.GONE);
                                        }
                                        initKeyboardContainer();
                                        startView.setVisibility(View.VISIBLE);
                                        taskSelectorView.setVisibility(View.GONE);
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

    public void initStartView() {
        target = originSourceList.get(random.nextInt(originSourceList.size()));
        startView.setText(target);
        startView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    startView.setVisibility(View.GONE);
                                    taskView.setVisibility(View.VISIBLE);
                                }
                            }
                        );
                        break;
                }
                return true;
            }
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

        String input = tapBoardView.getKey(tempX, tempY - tapBoardView.getY());

        return new String[] {
                String.valueOf(input),
                String.valueOf(tempX),
                String.valueOf(tempY)
        };
    }

    private void getIndexList() {
        mapIndex = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < originSourceList.size(); i++) {
            String fruit = originSourceList.get(i);
            String index = fruit.substring(0, 1);

            if (mapIndex.get(index) == null)
                mapIndex.put(index, i);
        }
    }

    private void displayIndex() {
        LinearLayout indexLayout = (LinearLayout) findViewById(R.id.side_index);

        TextView textView;
        List<String> indexList = new ArrayList<>(mapIndex.keySet());
        for (String index : indexList) {
            textView = (TextView) getLayoutInflater().inflate(
                    R.layout.side_index_item, null);
            textView.setText(index.toUpperCase());
            textView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    TextView selectedIndex = (TextView) view;
                    listview.setSelection(mapIndex.get(selectedIndex.getText().toString().toLowerCase()));
                }
            });
            indexLayout.addView(textView);
        }
    }
}
