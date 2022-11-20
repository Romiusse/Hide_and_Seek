package ru.yandex.romiusse.hideandseek;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;


public class RadarView extends View {

    public void setPlayerDistance(int[] playerDistance) {
        this.playerDistance = playerDistance;
    }

    private class DevCircle{

        private float[] devRadiuses;

        private String name;

        private float angle;
        private int radius;

        public int getRandomIntegerBetweenRange(int min, int max){
            return (int)(Math.random()*((max-min)+1))+min;
        }

        public DevCircle(String name, float angle, int radius){

            int random = getRandomIntegerBetweenRange(0, 50);
            devRadiuses = new float[] { random, (random + 20) % 50, (random + 40) % 50};


            this.name = name;
            this.angle = angle;
            this.radius = radius;


        }

        public void process(Canvas canvas){

            int width = getWidth();
            int height = getHeight();
            int r = Math.min(width, height);
            int center = r / 2;

            double angle = Math.toRadians(this.angle);
            int offsetX =  (int) (center + (float)(this.radius * Math.cos(angle)));
            int offsetY = (int) (center - (float)(this.radius * Math.sin(angle)));

            for(int i = 0; i < 3; i++){
                devRadiuses[i] = cutDevRadius(devRadiuses[i]);
                Paint np = new Paint();
                np.setColor(Color.parseColor("#71b718"));


                int alpha = (int)(100f - (0.2f * devRadiuses[i]) * (0.2f * devRadiuses[i]));
                np.setAlpha(alpha);
                canvas.drawCircle(offsetX, offsetY, devRadiuses[i], np);
                devRadiuses[i] += 0.2f;
                Paint textPaint = new Paint();
                textPaint.setColor(Color.parseColor("#4b711b"));
                textPaint.setTextSize(32);
                canvas.drawText(name, offsetX - 35, offsetY - 45, textPaint);
            }

        }

        public void setRadius(int radius){
            this.radius = radius;
        }

        public float cutDevRadius(float devRadius){
            if(devRadius > 50) return 10;
            return devRadius;
        }


    }


    private int fps = 100;
    private boolean showCircles = true;
    private int[] playerDistance = new int[] {-1, -1, -1, -1, -1};

    float alpha = 0;


    DevCircle devCircle1 = new DevCircle("Игрок1", 0, 150);
    DevCircle devCircle2 = new DevCircle("Игрок2", 75, 150);
    DevCircle devCircle3 = new DevCircle("Игрок3", 144, 150);
    DevCircle devCircle4 = new DevCircle("Игрок4", 216, 150);
    DevCircle devCircle5 = new DevCircle("Игрок5", 288, 150);

    public RadarView(Context context) {
        this(context, null);
    }

    public RadarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

    }


    android.os.Handler mHandler = new android.os.Handler();
    Runnable mTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            mHandler.postDelayed(this, 1000 / fps);
        }
    };


    public void startAnimation() {
        mHandler.removeCallbacks(mTick);
        mHandler.post(mTick);
    }

    public void stopAnimation() {
        mHandler.removeCallbacks(mTick);
    }

    public void setFrameRate(int fps) { this.fps = fps; }
    public int getFrameRate() { return this.fps; };

    public void setShowCircles(boolean showCircles) { this.showCircles =     showCircles; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int r = Math.min(width, height);



        int i = r / 2;
        int j = i - 1;
        @SuppressLint("DrawAllocation") Paint localPaint = new Paint();
        localPaint.setColor(Color.parseColor("#5e18b7"));
        localPaint.setAntiAlias(true);
        localPaint.setStyle(Paint.Style.STROKE);
        localPaint.setStrokeWidth(2.0F);
        localPaint.setAlpha(200);

        if (showCircles) {
            canvas.drawCircle(i, i, j, localPaint);
            canvas.drawCircle(i, i, j, localPaint);
            canvas.drawCircle(i, i, j * 3 / 4, localPaint);
            canvas.drawCircle(i, i, j >> 1, localPaint);
            canvas.drawCircle(i, i, j >> 2, localPaint);
        }


        //Рисуем движущийся радар

        alpha += 0.25;
        if (alpha > 360) alpha = 0;
        @SuppressLint("DrawAllocation") float[] stopsGradient = new float[] { 0, 1 }; // the 3 points correspond to the 3 colors
        @SuppressLint("DrawAllocation") int[] colorsGradient  = new int[] { Color.argb(50, 94, 24, 183),
                Color.argb(0, 94, 24, 183)};
        @SuppressLint("DrawAllocation") SweepGradient radialGradient = new SweepGradient(0, 0,colorsGradient, stopsGradient);
        Matrix matrix = new Matrix();
        matrix.postRotate(alpha);
        matrix.postTranslate(i,i);
        radialGradient.setLocalMatrix(matrix);
        Paint paint = new Paint();
        paint.setDither(true);
        paint.setAntiAlias(true);
        paint.setShader(radialGradient);
        canvas.drawCircle(i, i, j, paint);

        if(playerDistance[0] == -1 ) devCircle1.setRadius(5000);
        else devCircle1.setRadius(playerDistance[0]);

        if(playerDistance[1] == -1 ) devCircle2.setRadius(5000);
        else devCircle2.setRadius(playerDistance[1]);

        if(playerDistance[2] == -1 ) devCircle3.setRadius(5000);
        else devCircle3.setRadius(playerDistance[2]);

        if(playerDistance[3] == -1 ) devCircle4.setRadius(5000);
        else devCircle4.setRadius(playerDistance[3]);

        if(playerDistance[4] == -1 ) devCircle5.setRadius(5000);
        else devCircle5.setRadius(playerDistance[4]);


        devCircle1.process(canvas);
        devCircle2.process(canvas);
        devCircle3.process(canvas);
        devCircle4.process(canvas);
        devCircle5.process(canvas);


    }

}
