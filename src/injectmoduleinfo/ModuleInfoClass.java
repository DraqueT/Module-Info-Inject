/*******************************************************
 * Copyright 2019 Draque Thompson
 * 
 *  Module Injector is a module injection tool used for 
 *  modularizing jar files. This allows them to be 
 *  build into runnable images via jlink.
 * 
 *  No guarantees about anything. Use with caution.
 *  This thing is very much a hack, and I hope that all
 *  dependencies will be made modular so that no one
 *  has to ever use it again..
 * 
 *******************************************************/

package injectmoduleinfo;

import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final List<File> dependencies;
    private String tmpModulePath = "";
    private final String javaStr = ".java";
    private final String classStr = ".class";
    private final String moduleInfo = "module-info";
    private final String tmpClassPath = "tmpClassPath";
    // windows uses a different module separator character for some reason...
    private final String moduleSeparator = System.getProperties().getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";

    /**
     * Use publicly facing inject() method
     */
    private ModuleInfoClass(File _target, List<File> _dependencies) {
        target = _target;
        dependencies = _dependencies;
    }
    
    private static String runAtConsole(String command) throws InterruptedException, IOException {
        String ret = "";
        Runtime run = Runtime.getRuntime();
        Process p = run.exec(command);
        System.out.println(command);
        
        // get general output
        InputStream is = p.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
           ret += line;
        }
        
        // get error output
        is = p.getErrorStream();
        reader = new BufferedReader(new InputStreamReader(is));
        while ((line = reader.readLine()) != null) {
            ret += line;
        }
        
        return ret;
    }

    /**
     * creates temporary module (must be run AFTER target jar extracted)
     *
     * @throws IOException
     */
    private void createTmpModule() throws IOException, InterruptedException, DependancyException {
        String command = "";
        String targetModulePath = target.getParent();
        String targetJar = target.getAbsolutePath();
        
        command += "jdeps -verbose:class";
        
        // if dependencies exist, build proper path for them...
        if (!dependencies.isEmpty()) {
            command += " --module-path ";
            
            for (File dependency : dependencies) {
                command += "" + dependency.getAbsolutePath() + moduleSeparator;
            }
            
            // remove trailing comma...
            command = command.substring(0, command.length() - 1);
        }
        command += " --add-modules=ALL-MODULE-PATH";
        command += " --generate-module-info";
        command += " " + targetModulePath + "";
        command += " " + targetJar + "";
        
        String result = runAtConsole(command); // TODO: HANDLE ERROR RESULT OF A DEPENDANCY ITSELF HAVING A DEPENDANCY THAT IS UNRESOLVED
        
        if (result.contains("Missing dependen")) {
            String commaListDeps = result.replaceAll("(^.*?->)", "");
            commaListDeps = commaListDeps.replaceAll("^\\s*", "");
            commaListDeps = commaListDeps.replaceAll("\\s*not found.*?->\\s*", ",");
            commaListDeps = commaListDeps.replaceAll("\\s*not found.*$", "");
            String error = "The following dependencies are missing. Please provide the jars containing them:\n";
            List<String> deps = new ArrayList<>();
            String[] arrayDeps = commaListDeps.split(",");
            
            for(String dep : arrayDeps) {
                // use list to add speed for very long series of depdndencies...
                if (!deps.contains(dep)) {
                    deps.add(dep);
                }
            }
            
            for (String dep : deps) {
                error += dep + "\n";
            }
            
            throw new DependancyException(error);
        } else if (!result.contains("writing to")) {
            throw new IOException("Something's gone wrong in the module.info creation:\n" + result);
        }
        
        // presumes that jdeps still returns "writing to " as success string...
        tmpModulePath = result.substring("writing to ".length());
    }

    /**
     * creates backup of target jar
     */
    private void backupTarget() throws FileNotFoundException, IOException {
        File copyTo = new File(target.getAbsolutePath() + ".bak");
        copyFile(target, copyTo, true);
    }

    private void copyFile(File source, File copyTo, boolean backup) throws FileNotFoundException, IOException {
        int count = 0;

        // prevent backups from being overwritten
        if (backup) {
            while (copyTo.exists()) {
                count++;
                copyTo = new File(source.getAbsolutePath() + count + ".bak");
            }
        } else if (copyTo.exists()) {
            copyTo.delete();
        }

        try (FileInputStream is = new FileInputStream(source)) {
            try (FileOutputStream os = new FileOutputStream(copyTo)) {
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
        deleteFile(new File(tmpModulePath).getParentFile());
        deleteFile(new File(target.getParent() + File.separator + tmpClassPath));
    }

    private void deleteFile(File file) {
        if (file != null) {
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
    }

    /**
     * Tests whether java archive already has a module and/or should inject
     *
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
                    message = "Package contains existing module (written by this utility)\nOverwrite?";
                }

                int dialogResult = JOptionPane.showConfirmDialog(null, message, "Warning", JOptionPane.YES_NO_OPTION);
                ret = dialogResult == JOptionPane.YES_OPTION;
                
                if (ret) {
                    removeClassFromTarget(moduleInfo + classStr);
                }
            }
        } else {
            ret = false;
            JOptionPane.showMessageDialog(null, "Target jar file does not exist.");
        }

        return ret;
    }
    
    private void removeClassFromTarget(String targetDelete) throws IOException {
         /* Define ZIP File System Properies in HashMap */    
        Map<String, String> zip_properties = new HashMap<>(); 
        /* We want to read an existing ZIP File, so we set this to False */
        zip_properties.put("create", "false"); 

        /* Specify the path to the ZIP File that you want to read as a File System */
        URI zip_disk = URI.create("jar:file:" + target.getAbsolutePath());//("jar:file:/my_zip_file.zip");

        /* Create ZIP file System */
        try (FileSystem zipfs = FileSystems.newFileSystem(zip_disk, zip_properties)) {
            Path pathInZipfile = zipfs.getPath(targetDelete);
            Files.delete(pathInZipfile); 
        } 
    }

    /**
     * compiles java file to class file
     *
     * @throws IOException
     */
    private void compileModule() throws InterruptedException, IOException {
        String command = "";
        String compileToPath = target.getParent() + File.separator + tmpClassPath;
        
        command += "javac";
        if (!dependencies.isEmpty()) {
            
            command += " --module-path ";
            for (File dependency : dependencies) {
                command += dependency.getAbsolutePath() + moduleSeparator;
            }
            
            // remove trailing separator character...
            command = command.substring(0, command.length() - 1);
        }
        command += " -d " + compileToPath + " " + tmpModulePath;
        
        String result = runAtConsole(command);
        
        File classFile = new File(compileToPath + File.separator + moduleInfo + classStr);
        if (result.contains("package is empty or does not exist")) {
            // detects empty packages which cause the compiler to throw up, then removes them from the module-info file and recursively recompiles
            String emptyExport = result.replaceAll("^.*exports\\s*", "");

            emptyExport = emptyExport.replaceAll(";.*$", "");
            removeExportFromModule(emptyExport);
            JOptionPane.showMessageDialog(null, "Empty export: " + emptyExport + " removed from path to compile. Continuing.");
            compileModule();
        } else if (!classFile.exists()) {
            throw new IOException("Class file not compiled: " + result);
        }
    }
    
    /**
     * Eliminates an export from a module-info.java instance (in case it's an empty export)
     * @param export
     * @throws IOException 
     */
    private void removeExportFromModule(String export) throws IOException{
        File f1 = new File(tmpModulePath);
        String line;
        String moduleText = "";
        try (FileReader fr = new FileReader(f1); BufferedReader br = new BufferedReader(fr)) {
            while ((line = br.readLine()) != null) {
                moduleText += line + "\n";
            }
        }
        
        String regex = "\\s*exports\\s*" + export + ";";
        moduleText = moduleText.replaceAll(regex, "");

        try (BufferedWriter out = new BufferedWriter(new FileWriter(f1))) {
            out.write(moduleText);
            out.flush();
        }
    }

    /**
     * reads java archive and returns existing module info (as string) if it
     * exists
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

    public static void inject(File target, List<File> dependencies){
        new ModuleInfoClass(target, dependencies).doInject();
    }

    private void doInject() {
        try {
            if (shouldInject()) {
                createTmpModule();
                extractTmpClasspath();
                compileModule();
                backupTarget();
                archiveTmpModulePath();
                JOptionPane.showMessageDialog(null, "Archive successfully modularized. (module-info.java added to archive for reference)\nTHERE MIGHT BE ADDITIONAL DEPENDENCIES FOR THIS MODULE. Please pay attention to error messages when you build your image with jlink.");
            }
        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, "Problems encountered: " + e.getLocalizedMessage());
        } catch (DependancyException e ) {
            JOptionPane.showMessageDialog(null, "Problems encountered: Missing Dependencies (text window)");
            TextDisplayForm.run("Missing Dependencies", e.getLocalizedMessage());
        } finally {
            cleanUp();
        }
    }

    private void extractTmpClasspath() throws IOException {
        String destDir = target.getParent() + File.separator + tmpClassPath;
        File dir = new File(destDir);
        // create output directory if it doesn't exist
        if (!dir.exists()) {
            dir.mkdirs();
        }

        //buffer for read and write data to file
        byte[] buffer = new byte[1024];

        System.out.println("Unzipping " + target.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(target);
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println(".");
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

    private void archiveTmpModulePath() throws IOException {
        target.delete();
        copyFile(new File(tmpModulePath),
                        new File(target.getParent() + File.separator + tmpClassPath + File.separator + moduleInfo + javaStr), false);
        zipDir(target.getAbsolutePath(), target.getParent() + File.separator + tmpClassPath);
    }

    private static void zipDir(String zipFileName, String dir) throws FileNotFoundException, IOException {
        File dirObj = new File(dir);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            System.out.println("Creating : " + zipFileName);
            addDir(dirObj, out, dir.length() + 1);
        }
    }
    
    private static void addDir(File dirObj, ZipOutputStream out, int pathTrim) throws IOException {
        File[] files = dirObj.listFiles();
        byte[] tmpBuf = new byte[1024];

        for (File file : files) {
            if (file.isDirectory()) {
                addDir(file, out, pathTrim);
                continue;
            }
            try (final FileInputStream in = new FileInputStream(file.getAbsolutePath())) {
                String absolutePath = file.getAbsolutePath();
                String trimmedPath = absolutePath.substring(pathTrim);
                System.out.print(".");
                out.putNextEntry(new ZipEntry(trimmedPath));
                int len;
                while ((len = in.read(tmpBuf)) > 0) {
                    out.write(tmpBuf, 0, len);
                }
                out.closeEntry();
            }
        }
    }
    
    public class DependancyException extends Exception {
        public DependancyException(String message) {
            super(message);
        }
    }
}
