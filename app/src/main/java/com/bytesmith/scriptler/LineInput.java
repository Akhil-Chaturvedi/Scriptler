package com.bytesmith.scriptler;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class LineInput extends EditText {
    public LineInput(Context context) {
        super(context);
        init();
    }

    public LineInput(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize the line input view
        setBackgroundResource(R.drawable.line_background);
    }
} 