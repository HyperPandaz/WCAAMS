package com.example.groupprojectapplication.machine_learning;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.tensorflow.lite.support.model.Model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class ModelHandler {
    private final Executor executor;
    private final Context context;
    private Python py;
    private final File storageDir;
    private final String SPEC_FILENAME = "/spec.png";
    private Model model;
    private static final int NORMAL = 0;
    private static final int ABNORMAL = 1;
    private static final String[] CLASSES = {"normal", "abnormal"};
    private static final int UNKNOWN = CLASSES.length;
    private final double DECISION_BOUNDARY = 0.75;

    public ModelHandler(Executor executor, Context context, File sd) {
        this.executor = executor;
        this.context = context;
        storageDir = sd;

        try {
            model = Model.createModel(context, "final_model.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // given an input file to process it will return an arraylist of tags to add
    public void processRecording(String filename, final ModelCallback callback) {
        // run data processing and ML model in background, then callback when ready to return a value
        executor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("Model: running recording processing in background...");
                try {
                    createSpectrogram(filename, 45);
                    ByteBuffer bb = preprocessing();
                    int prediction = predict(bb);

                    ArrayList<String> tagsToAdd = new ArrayList<>();
                    if (prediction < CLASSES.length) {
                        tagsToAdd.add(CLASSES[prediction]);
                    } else {
                        tagsToAdd.add("unknown");
                    }

                    System.out.println("Model: model result, " + tagsToAdd.toString());
                    // remove temporary files
                    new File(storageDir, "extended.wav").delete();
                    new File(storageDir, SPEC_FILENAME).delete();
                    System.out.println(("Model: finished, terminating background process"));
                    callback.onComplete(tagsToAdd);
                } catch (RuntimeException e) {
                    System.out.println("Issue running model");
                    e.printStackTrace();
                }
            }
        });
    }

    private void createSpectrogram(String inputFile, int targetLength) {
        if (!Python.isStarted()) {
            System.out.println("Model: starting python");
            Python.start(new AndroidPlatform(context));
            py = Python.getInstance();
        }

        PyObject pyDataProcessing = py.getModule("data_processing");
        File imgDir = new File(storageDir, "/images/");
        if (!imgDir.exists()) {
            imgDir.mkdir();
        }
        String imageFilename = "/images/" + inputFile.substring(0, inputFile.lastIndexOf('.')) + ".jpg";
        String parent = storageDir.getAbsolutePath();
        pyDataProcessing.callAttr("main", targetLength, parent + "/" + inputFile, parent + "/extended.wav", parent + SPEC_FILENAME, parent + imageFilename);

    }

    private ByteBuffer preprocessing() {
        // load in image and resize to 256x256 pixels
        BitmapDrawable bit = new BitmapDrawable(context.getResources(), storageDir.getAbsolutePath() + SPEC_FILENAME);
        Bitmap scaledBit = Bitmap.createScaledBitmap(bit.getBitmap(), 256, 256, true);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 256 * 256 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < 256; y++) { // loop through each pixel in image and get rgb values, normalise, and put in buffer
            for (int x = 0; x < 256; x++) {
                int pixel = scaledBit.getPixel(x, y);
                byteBuffer.putFloat((float) Color.red(pixel) / 255);
                byteBuffer.putFloat((float) Color.green(pixel) / 255);
                byteBuffer.putFloat((float) Color.blue(pixel) / 255);
            }
        }

        return byteBuffer;
    }

    private int predict(ByteBuffer input) {
        // create output for model to put results in
        float[][] output = new float[1][2];
        Map<Integer, Object> out = new HashMap<>();
        out.put(0, output);
        System.out.println("Model: running model on generated input");
        model.run(new Object[]{input}, out); // model is capable of running on multiple inputs (hence inout as array and output as map)

        System.out.println("Model: model output [[abnormal probability], [normal probability]]: " + Arrays.deepToString(output));
        // output is an array [[abnormal probability], [normal probability]]
        float normalPred = output[0][1];
        float abnormalPred = output[0][0];

        // return classification if model is 'sure', otherwise return unknown
        if (Math.max(normalPred, abnormalPred) > DECISION_BOUNDARY) {
            if (normalPred > abnormalPred) {
                return NORMAL;
            } else if (abnormalPred > normalPred) {
                return ABNORMAL;
            }
        }
        return UNKNOWN;
    }
}
