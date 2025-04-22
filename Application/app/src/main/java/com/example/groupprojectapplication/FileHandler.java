package com.example.groupprojectapplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileHandler {
    private File storageDir;

    public FileHandler(File storageDirectory) {
        storageDir = storageDirectory;
    }

    public void deleteFile(String filename) {
        System.out.println("Deleting " + filename + "...");
        File file = new File(storageDir, filename);
        file.delete();
        String imageFile = filename.substring(0, filename.lastIndexOf('.')) + ".jpg";
        imageFile = "/images/" + imageFile;
        System.out.println("Deleting " + imageFile + "...");
        file = new File(storageDir, imageFile);
        file.delete();
    }

    public void saveFile(InputStream in, String filename) {
        //copies file from 'raw' resource directory onto the device storage
        //delete after testing but will be used as reference for saving recordings when they're synced from device
        try {
            File outputfile = new File(storageDir, filename);
            String file = outputfile.getAbsolutePath();
            System.out.println("File saved to: " + file);
            FileOutputStream out = new FileOutputStream(file);
            byte[] buff = new byte[1024];
            int read = 0;

            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

