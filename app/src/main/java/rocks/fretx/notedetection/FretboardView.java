package rocks.fretx.notedetection;

/**
 * Created by Onur Babacan on 10/3/16.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;


public class FretboardView extends View {

    private int width, height;
    private final Paint paint = new Paint();
    private final int nStrings = 6;
    private String[] stringNames = {"E","B","G","D","A","E"};
    public boolean playingCorrectly = false;

    private FretboardPosition fretboardPosition;


    public FretboardView(Context context) {
        super(context);
    }

    public FretboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FretboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private float dpToPixel(int dp){
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, getResources().getDisplayMetrics());
    }

    public void setFretboardPosition(FretboardPosition fp){
        fretboardPosition = fp;
        //if you don't invalidate your new data won't be drawn
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        float rowHeight = height/(nStrings+1);
        float halfRowHeight = rowHeight/2;
        float[] stringHeights = new float[nStrings];
        for (int i = 0; i < nStrings; i++) {
            stringHeights[i] = halfRowHeight + i * rowHeight;
        }

        paint.setStrokeWidth(6.0f);
        paint.setColor(Color.BLACK);
        for (float stringHeight: stringHeights){
            canvas.drawLine(0,stringHeight,width,stringHeight,paint);
        }

        paint.setStrokeWidth(8.0f);
        paint.setTextSize(dpToPixel(24));
        paint.setColor(Color.BLUE);
        for (int i = 0; i < stringNames.length; i++) {
            canvas.drawText(stringNames[i], dpToPixel(2), stringHeights[i]-dpToPixel(2),paint);
        }


        if(fretboardPosition != null){
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(width/2,stringHeights[fretboardPosition.getString()-1],dpToPixel(16),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            if(playingCorrectly) paint.setColor(Color.GREEN);
            canvas.drawCircle(width/2,stringHeights[fretboardPosition.getString()-1],dpToPixel(16),paint);
            paint.setColor(Color.RED);
            paint.setTextSize(dpToPixel(24));
            canvas.drawText(Integer.toString(fretboardPosition.getFret()),(width/2)-dpToPixel(6),stringHeights[fretboardPosition.getString()-1]+dpToPixel(6),paint);
        }

    }

}