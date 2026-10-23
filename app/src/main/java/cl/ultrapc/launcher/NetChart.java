package cl.ultrapc.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/** Gráfico de descarga/subida de los últimos 2 minutos, como el widget del Escritorio. */
public class NetChart extends View {

    static final int MAX_POINTS = 60; // 2 minutos cada 2 s

    private static final int DOWN = 0xFF087CFF;
    private static final int UP = 0xFFE8590C;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private List<Double> down;
    private List<Double> up;

    public NetChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(0x22FFFFFF);
        grid.setStrokeWidth(d);
        label.setColor(0x99FFFFFF);
        label.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    }

    void setData(List<Double> down, List<Double> up) {
        this.down = down;
        this.up = up;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        double max = 1;
        if (down != null) for (double v : down) max = Math.max(max, v);
        if (up != null) for (double v : up) max = Math.max(max, v);
        max *= 1.15;

        for (int i = 1; i <= 3; i++) {
            float y = h - h * i / 3f;
            canvas.drawLine(0, y, w, y, grid);
            canvas.drawText(PanelActivity.fmtBits(max * i / 3), 4, y + label.getTextSize() + 2, label);
        }
        drawSeries(canvas, down, DOWN, max, w, h, true);
        drawSeries(canvas, up, UP, max, w, h, false);
    }

    private void drawSeries(Canvas canvas, List<Double> data, int color, double max, float w, float h, boolean area) {
        if (data == null || data.size() < 2) return;
        float step = w / (MAX_POINTS - 1);
        float x0 = w - (data.size() - 1) * step;
        path.reset();
        for (int i = 0; i < data.size(); i++) {
            float x = x0 + i * step;
            float y = (float) (h - data.get(i) / max * h);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        if (area) {
            Path a = new Path(path);
            a.lineTo(w, h);
            a.lineTo(x0, h);
            a.close();
            fill.setShader(new LinearGradient(0, 0, 0, h, (color & 0x00FFFFFF) | 0x55000000,
                    color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawPath(a, fill);
        }
        line.setColor(color);
        canvas.drawPath(path, line);
    }
}
