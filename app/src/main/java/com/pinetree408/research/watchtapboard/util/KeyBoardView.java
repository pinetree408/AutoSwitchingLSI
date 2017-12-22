package com.pinetree408.research.watchtapboard.util;

/**
 * Created by leesangyoon on 2017. 8. 15..
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.pinetree408.research.watchtapboard.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO: document your custom view class.
 */
public class KeyBoardView extends View {

    char[] keyboardCharList = {
            'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p',
            'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l',
            'z', 'x', 'c', 'v', 'b', 'n', 'm'
    };

    char[][] keyboardChar = {
            {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'},
            {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'},
            {'z', 'x', 'c', 'v', 'b', 'n', 'm'}
    };

    List<String> keyboardCharPos;
    double keyWidthRef;
    double keyHeightRef;

    Paint keyboardPaint;

    public KeyBoardView(Context context) {
        super(context);
        init(null, 0);
    }

    public KeyBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public KeyBoardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public String getKey(double x, double y) {
        int id = -1;

        for (int i = 0; i < keyboardCharPos.size(); i++) {
            String pos = keyboardCharPos.get(i);
            double posX = Double.parseDouble(pos.split("-")[0]);
            double posY = Double.parseDouble(pos.split("-")[1]);
            if ((Math.abs(posX - x) <= keyWidthRef) && (Math.abs(posY - y) <= keyHeightRef)) {
                id = i;
                break;
            }
        }

        if (id == -1) {
            return ".";
        }

        return String.valueOf(keyboardCharList[id]);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TapBoardView, defStyle, 0);
        a.recycle();

        keyboardPaint = new Paint();
        keyboardPaint.setAntiAlias(true);
        keyboardPaint.setTextAlign(Paint.Align.CENTER);

        keyboardCharPos = new ArrayList<>();
        keyHeightRef = -1.0;
        keyWidthRef = -1.0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        double viewWidth = getWidth();

        double keyWidth = viewWidth / 10;
        double keyHeight = keyWidth * 1.5;

        keyboardPaint.setTextSize((float) (keyHeight * 0.8));
        keyboardPaint.setColor(Color.parseColor("#80000000"));

        if (keyWidthRef == -1.0) {
            keyWidthRef = keyWidth * 0.5;
        }
        if (keyHeightRef == -1.0) {
            keyHeightRef = keyHeight * 0.5;
        }

        double keyboardPaddingLeft = (viewWidth - (10 * keyWidth)) * 0.5;
        double keyboardPaddingTop = 10;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < keyboardChar[i].length; j++) {
                String content = String.valueOf(keyboardChar[i][j]);

                float leftPadding = (float) (keyboardPaddingLeft + j * keyWidth + (keyWidth * 0.5));
                float topPadding = (float) (keyboardPaddingTop + i * keyHeight + (keyHeight * 0.5));
                if (i == 1) {
                    leftPadding += keyWidth * 0.5;
                } else if (i == 2) {
                    leftPadding += keyWidth * 1.0;
                }

                if (keyboardCharPos.size() != 26) {
                    keyboardCharPos.add(leftPadding + "-" + topPadding);
                }

                canvas.drawText(content,
                        leftPadding,
                        topPadding,
                        keyboardPaint);
            }
        }

    }

}
