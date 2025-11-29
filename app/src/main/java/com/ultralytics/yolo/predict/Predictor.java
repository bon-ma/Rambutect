package com.ultralytics.yolo.predict;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import androidx.annotation.Keep;

import com.ultralytics.yolo.models.YoloModel;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class Predictor {
    public static int INPUT_SIZE = 320;
    public final ArrayList<String> labels = new ArrayList<>();
    protected final Context context;

    protected Predictor(Context context) {
        this.context = context;
    }

    public abstract void loadModel(YoloModel yoloModel, boolean useGpu) throws Exception;

    protected void loadLabels(AssetManager assetManager, String metadataPath) throws IOException {
        InputStream inputStream;
        Yaml yaml = new Yaml();

        inputStream = assetManager.open(metadataPath);

        Map<String, Object> data = yaml.load(inputStream);

        // Make the casting more robust
        Object namesObj = data.get("names");
        if (namesObj instanceof Map) {
            // Handle Map<Integer, String> or similar type
            @SuppressWarnings("unchecked")
            Map<Object, Object> namesMap = (Map<Object, Object>) namesObj;

            labels.clear();
            for (Object value : namesMap.values()) {
                if (value instanceof String) {
                    labels.add((String) value);
                }
            }
        }

        Object imgszObj = data.get("imgsz");
        if (imgszObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> imgszArray = (List<Object>) imgszObj;

            if (imgszArray != null && imgszArray.size() == 2) {
                try {
                    int width = toInt(imgszArray.get(0));
                    int height = toInt(imgszArray.get(1));
                    INPUT_SIZE = Math.max(width, height);
                    System.out.println("INPUT_SIZE:" + INPUT_SIZE);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse imgsz: " + e.getMessage());
                }
            }
        }

        inputStream.close();
    }

    // Helper method to safely convert any object to int
    private int toInt(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        throw new NumberFormatException("Cannot convert to int: " + obj);
    }

    public abstract Object predict(Bitmap bitmap);

    // public abstract void predict(ImageProxy imageProxy, boolean isMirrored);

    public abstract void setConfidenceThreshold(float confidence);

    public abstract void setInferenceTimeCallback(FloatResultCallback callback);

    public abstract void setFpsRateCallback(FloatResultCallback callback);

    public interface FloatResultCallback {
        @Keep()
        void onResult(float result);
    }
}
