package de.sindzinski.sunshine;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by steffen on 25.01.16.
 */
public class MyView extends View {
    public MyView(Context context) {
        super(context);
    }
    public MyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public MyView(Context context, AttributeSet attrs, int defaultStyle) {
        super(context, attrs, defaultStyle);
    }

    @Override
    public void onMeasure(int vMeasureSpec, int hMeasureSpec) {

    }
}
