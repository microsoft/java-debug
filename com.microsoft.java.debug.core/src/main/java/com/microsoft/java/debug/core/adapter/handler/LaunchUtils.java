/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.microsoft.java.debug.core.adapter.AdapterUtils;

import org.apache.commons.lang3.ArrayUtils;

public class LaunchUtils {
    private static Set<Path> tempFilesInUse = new HashSet<>();

    /**
     * Generate the classpath parameters to a temporary classpath.jar.
     * @param classPaths - the classpath parameters
     * @return the file path of the generate classpath.jar
     * @throws IOException Some errors occur during generating the classpath.jar
     */
    public static synchronized Path generateClasspathJar(String[] classPaths) throws IOException {
        List<String> classpathUrls = new ArrayList<>();
        for (String classpath : classPaths) {
            classpathUrls.add(AdapterUtils.toUrl(classpath));
        }

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        // In jar manifest, the absolute path C:\a.jar should be converted to the url style file:///C:/a.jar
        String classpathValue = String.join(" ", classpathUrls);
        attributes.put(Attributes.Name.CLASS_PATH, classpathValue);
        String baseName = "cp_" + getMd5(classpathValue);
        cleanupTempFiles(baseName, ".jar");
        Path tempfile = createTempFile(baseName, ".jar");
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(tempfile.toFile()), manifest);
        jar.close();
        lockTempLaunchFile(tempfile);

        return tempfile;
    }

    /**
     * Generate the classpath parameters to a temporary argfile file.
     * @param classPaths - the classpath parameters
     * @param modulePaths - the modulepath parameters
     * @return the file path of the generated argfile
     * @throws IOException Some errors occur during generating the argfile
     */
    public static synchronized Path generateArgfile(String[] classPaths, String[] modulePaths) throws IOException {
        String argfile = "";
        if (ArrayUtils.isNotEmpty(classPaths)) {
            argfile = "-cp \"" + String.join(File.pathSeparator, classPaths) + "\"";
        }

        if (ArrayUtils.isNotEmpty(modulePaths)) {
            argfile += " --module-path \"" + String.join(File.pathSeparator, modulePaths) + "\"";
        }

        argfile = argfile.replace("\\", "\\\\");
        String baseName = "cp_" + getMd5(argfile);
        cleanupTempFiles(baseName, ".argfile");
        Path tempfile = createTempFile(baseName, ".argfile");
        Files.write(tempfile, argfile.getBytes());
        lockTempLaunchFile(tempfile);

        return tempfile;
    }

    public static void lockTempLaunchFile(Path tempFile) {
        if (tempFile != null) {
            tempFilesInUse.add(tempFile);
        }
    }

    public static void releaseTempLaunchFile(Path tempFile) {
        if (tempFile != null) {
            tempFilesInUse.remove(tempFile);
        }
    }

    private static Path tmpdir = null;

    private static synchronized Path getTmpDir() throws IOException {
        if (tmpdir == null) {
            Path tmpfile = Files.createTempFile("", UUID.randomUUID().toString());
            tmpdir = tmpfile.getParent();
            try {
                Files.deleteIfExists(tmpfile);
            } catch (Exception ex) {
                // do nothing
            }
        }

        return tmpdir;
    }

    private static void cleanupTempFiles(String baseName, String suffix) throws IOException {
        for (int i = 0; ; i++) {
            Path tempFile = getTmpDir().resolve(baseName + (i == 0 ? "" : i) + suffix);
            if (tempFilesInUse.contains(tempFile)) {
                continue;
            } else if (!Files.exists(tempFile)) {
                break;
            } else {
                try {
                    // delete the old temp file
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    }

    private static Path createTempFile(String baseName, String suffix) throws IOException {
        // loop until the temp file can be created
        for (int i = 0; ; i++) {
            Path tempFile = getTmpDir().resolve(baseName + (i == 0 ? "" : i) + suffix);
            if (!Files.exists(tempFile)) {
                return Files.createFile(tempFile);
            }
        }
    }

    private static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger md5 = new BigInteger(1, messageDigest);
            return md5.toString(Character.MAX_RADIX);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toString(input.hashCode(), Character.MAX_RADIX);
        }
    }
}
