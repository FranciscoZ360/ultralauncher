package cl.ultrapc.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Barra redondeada con segmentos de colores (desglose del sitio). */
public class StackBar extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private float[] values = new float[0];
    private int[] colors = new int[0];

    public StackBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setData(float[] values, int[] colors) {
        this.values = values;
        this.colors = colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(rect, paint);

        float sum = 0;
        for (float v : values) sum += v;
        if (sum > 0) {
            float gap = getResources().getDisplayMetrics().density * 2;
            float min = w * 0.015f; // como el widget: cada parte se ve aunque sea chica
            float[] widths = new float[values.length];
            float total = 0;
            for (int i = 0; i < values.length; i++) {
                widths[i] = Math.max(min, values[i] / sum * w);
                total += widths[i];
            }
            float scale = (w - gap * (values.length - 1)) / total;
            float x = 0;
            for (int i = 0; i < values.length; i++) {
                float sw = widths[i] * scale;
                paint.setColor(colors[i]);
                canvas.drawRect(x, 0, x + sw, h, paint);
                x += sw + gap;
            }
        }
        canvas.restore();
    }
}
