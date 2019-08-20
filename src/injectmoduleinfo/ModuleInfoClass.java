/*
 * Copyright 2019 Draque Thompson
 */
package injectmoduleinfo;

import java.awt.HeadlessException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;

/**
 *
 * @author draque
 */
public class ModuleInfoClass {
    
    private final File target;
    private String tmpModulePath;
    private final String[] exports;
    private final String moduleName;
    private final String javaStr = ".java";
    private final String classStr = ".class";
    private final String moduleInfo = "module-info";
    private final String tmpClassPath = "tmpClassPath";
    
    /**
     * Use publicly facing inject() method
     */
    private ModuleInfoClass(File _target, String _moduleName, String exportList) {
        target = _target;
        moduleName = _moduleName;
        exports = exportList.split(",");
    }
    
    public static void addFilesToExistingZip(File zipFile, File[] files) throws IOException {
        // get a temp file
        File tempFile = File.createTempFile(zipFile.getName(), null);
        // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        boolean renameOk = zipFile.renameTo(tempFile);
        if (!renameOk) {
            throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[1024];

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile))) {
                ZipEntry entry = zin.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    boolean notInFiles = true;
                    for (File f : files) {
                        if (f.getName().equals(name)) {
                            notInFiles = false;
                            break;
                        }
                    }
                    // if this if in files to be inserted, skip (effective overwrite)
                    if (notInFiles) {
                        // Add ZIP entry to output stream.
                        out.putNextEntry(new ZipEntry(name));
                        // Transfer bytes from the ZIP file to the output file
                        int len;
                        while ((len = zin.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    entry = zin.getNextEntry();
                }   // Close the streams        
            }
            
            // Compress the provided files
            for (File file : files) {
                try (InputStream in = new FileInputStream(file)) {
                    // Add ZIP entry to output stream.
                    out.putNextEntry(new ZipEntry(file.getName()));
                    // Transfer bytes from the file to the ZIP file
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.closeEntry();
                }
            }
        }
        tempFile.delete();
    }
    
    /**
     * creates temporary dummy module (must be run AFTER target jar extracted)
     * @throws IOException 
     */
    private void createTmpModule() throws IOException {
        String moduleString = "module " + moduleName + " { ";
        
        for (String export : exports) {
            moduleString += "exports " + export + ";";
        }
        
        moduleString += "}";
        //String moduleString = String.format("module %s {}", txtModuleName.getText());
        tmpModulePath = target.getParent() + File.separator + moduleInfo;

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpModulePath + javaStr), "utf-8"))) {
            writer.write(moduleString);
        }
    }
    
    /**
     * creates backup of target jar
     */
    private void backupTarget() throws FileNotFoundException, IOException {
        File copyTo = new File(target.getAbsolutePath() + ".bak");
        int count = 0;
        
        // prevent backups from being overwritten
        while (copyTo.exists()) {
            count++;
            copyTo = new File(target.getAbsolutePath() + count + ".bak");
        }
        
        try (FileInputStream is = new FileInputStream(target)) {
            try (FileOutputStream os  = new FileOutputStream(copyTo)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            }
        }
    }
    
    /**
     * removes remaining temporary files
     */
    private void cleanUp() {
        deleteFile(new File(tmpModulePath + javaStr));
        deleteFile(new File(target.getParent() + File.separator + tmpClassPath));
    }
    
    private void deleteFile(File file) {
        if (file.isDirectory()) {
            for (String subFileName : file.list()) {
                String path = file.getAbsolutePath();
                String subFilePath = path + File.separator + subFileName;
                File subFile = new File(subFilePath);
                deleteFile(subFile);
            }
        }
        
        file.delete();
    }
    
    /**
     * Tests whether java archive already has a module and/or should inject
     * @return
     */
    private boolean shouldInject() throws IOException {
        boolean ret = true;
        boolean moduleFound = false;

        if (target.exists()) {
            try (FileInputStream iStream = new FileInputStream(target)) {
                try (ZipInputStream zin = new ZipInputStream(iStream)) {
                    ZipEntry entry = zin.getNextEntry();
                    while (entry != null) {
                        if (entry.getName().equals(moduleInfo + classStr)) {
                            moduleFound = true;
                            break;
                        }

                        entry = zin.getNextEntry();
                    }
                }
            }

            if (moduleFound) {
                String moduleInfoContents = moduleContents();
                String message;

                if (moduleInfoContents.isEmpty()) {
                    message = "Package contains existing module (not written by this utility). Overwrite?";
                } else {
                    message = "Package contains existing module (written by this utility):\n" + moduleInfoContents + "\nOverwrite?";
                }

                int dialogResult = JOptionPane.showConfirmDialog (null, message, "Warning", JOptionPane.YES_NO_OPTION);
                ret = dialogResult == JOptionPane.YES_OPTION;
            }   
        } else {
            ret = false;
            JOptionPane.showMessageDialog(null, "Target jar file does not exist.");
        }
        
        return ret;
    }
    
    /**
     *  compiles java file to class file
     * @throws IOException 
     */
    private void compileModule() throws InterruptedException, IOException {
        String compileToPath = target.getParent() + File.separator + tmpClassPath;
        String toRun = "javac -d " + compileToPath + " " + tmpModulePath + javaStr;
        Runtime run = Runtime.getRuntime();
        Process p = run.exec(toRun);
        
        File classFile = new File(compileToPath + File.separator + moduleInfo + classStr);
        
        if (!classFile.exists()) {
            throw new IOException("Class file not compiled. Check that your module name is legal and that all export paths exist.");
        }
        
        p.waitFor();
    }
    
    /**
     * reads java archive and returns existing module info (as string) if it exists
     */
    private String moduleContents() {
        String moduleString = "";
        
        try {
            ZipFile zip = new ZipFile(target);

            for (Enumeration e = zip.entries(); e.hasMoreElements();) {
                ZipEntry entry = (ZipEntry) e.nextElement();

                if (entry.getName().equals(moduleInfo + javaStr)) {
                    InputStream is = zip.getInputStream(entry);
                    InputStreamReader isr = new InputStreamReader(is);

                    char[] buffer = new char[1024];
                    while (isr.read(buffer, 0, buffer.length) != -1) {
                        moduleString += new String(buffer);
                    }
                    break;
                }
            }
        } catch (HeadlessException | IOException e) {
            JOptionPane.showMessageDialog(null, "IO error while reading module info: " + e.getLocalizedMessage());
        }
        
        return moduleString;
    }
    
    public static void inject(File target, String moduleName, String exportList) {
        new ModuleInfoClass(target, moduleName, exportList).doInject();
    }
    
    private void doInject() {
        try {
            if (shouldInject()) {
                createTmpModule();
                extractTmpClasspath();
                compileModule();
                backupTarget();
                addFilesToExistingZip(target, new File[]{
                    new File(target.getParent() + File.separator + tmpClassPath + File.separator + moduleInfo + classStr),
                    new File(tmpModulePath + javaStr)});
                JOptionPane.showMessageDialog(null, "Dummy module info injected!");
            }
        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, "Problems encountered: " + e.getLocalizedMessage());
        } finally {
            cleanUp();
        }
    }
    
    private void extractTmpClasspath() throws IOException {
        String destDir = target.getParent() + File.separator + tmpClassPath;
        File dir = new File(destDir);
        // create output directory if it doesn't exist
        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;
        //buffer for read and write data to file
        byte[] buffer = new byte[1024];

        fis = new FileInputStream(target);
        try (ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                File parent = new File(newFile.getParent());
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                
                if (!ze.isDirectory()) {
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();
        }
        fis.close();
    }
     
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
         
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
         
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
         
        return destFile;
    }
    
}
