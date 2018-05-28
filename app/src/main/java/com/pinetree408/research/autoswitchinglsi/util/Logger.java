package com.pinetree408.research.autoswitchinglsi.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class Logger {
    private static final String TAG = Logger.class.getSimpleName();
    String filePath;
    String fileName;
    File outputFile;

    public Logger() {

    }

    public Logger(String filePath, String fileName) {
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void fileOpen(int userNum) {
        outputFile = new File(filePath + fileName);
        boolean isSuccess = false;
        if(outputFile!=null&&!outputFile.exists()){
            Log.i(TAG , "!file.exists" );
            try {
                isSuccess = outputFile.createNewFile();
            } catch (IOException e) {
                Log.d(TAG, e.toString());
            } finally{
                Log.i(TAG, "isSuccess = " + isSuccess);
            }
        }else{
            Log.i( TAG , "file.exists" );
        }
    }

    public void fileWriteHeader(String header) {
        header = header + "\n";
        fileWrite(header);
    }
    public void fileWriteLog(
            int block, int trial, int poolSize, String tech, long eventTime,
            String target, String inputKey, String inputString, int listSize, int index) {

        String log = block + "," + trial + "," + poolSize + "," + tech + "," + eventTime + "," +
                target + "," + inputKey + "," +  inputString + "," + listSize + "," + (index + 1) + "\n";
        fileWrite(log);
    }

    private void fileWrite(String log) {
        try {
            FileOutputStream os = new FileOutputStream(filePath + fileName, true);
            os.write(log.getBytes());
            os.close();
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
    }
}
