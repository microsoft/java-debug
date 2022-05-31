/*******************************************************************************
* Copyright (c) 2021-2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.AdapterUtils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

public class LaunchUtils {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
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

    public static ProcessHandle findJavaProcessInTerminalShell(long shellPid, String javaCommand, int timeout/*ms*/) {
        ProcessHandle shellProcess = ProcessHandle.of(shellPid).orElse(null);
        if (shellProcess != null) {
            int retry = 0;
            final int INTERVAL = 20/*ms*/;
            final int maxRetries = timeout / INTERVAL;
            final boolean isCygwinShell = isCygwinShell(shellProcess.info().command().orElse(null));
            while (retry <= maxRetries) {
                Optional<ProcessHandle> subProcessHandle = shellProcess.descendants().filter(proc -> {
                    String command = proc.info().command().orElse("");
                    return Objects.equals(command, javaCommand) || command.endsWith("\\java.exe") || command.endsWith("/java");
                }).findFirst();

                if (subProcessHandle.isPresent()) {
                    if (retry > 0) {
                        logger.info("Retried " + retry + " times to find Java subProcess.");
                    }
                    logger.info("shellPid: " + shellPid + ", javaPid: " + subProcessHandle.get().pid());
                    return subProcessHandle.get();
                } else if (isCygwinShell) {
                    long javaPid = findJavaProcessByCygwinPsCommand(shellProcess, javaCommand);
                    if (javaPid > 0) {
                        if (retry > 0) {
                            logger.info("Retried " + retry + " times to find Java subProcess.");
                        }
                        logger.info("[Cygwin Shell] shellPid: " + shellPid + ", javaPid: " + javaPid);
                        return ProcessHandle.of(javaPid).orElse(null);
                    }
                }

                retry++;
                if (retry > maxRetries) {
                    break;
                }

                try {
                    Thread.sleep(INTERVAL);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            logger.info("Retried " + retry + " times but failed to find Java subProcess of shell pid " + shellPid);
        }

        return null;
    }

    private static long findJavaProcessByCygwinPsCommand(ProcessHandle shellProcess, String javaCommand) {
        String psCommand = detectPsCommandPath(shellProcess.info().command().orElse(null));
        if (psCommand == null) {
            return -1;
        }

        BufferedReader psReader = null;
        List<PsProcess> psProcs = new ArrayList<>();
        List<PsProcess> javaCandidates = new ArrayList<>();
        try {
            String[] headers = null;
            int pidIndex = -1;
            int ppidIndex = -1;
            int winpidIndex = -1;
            String line;
            String javaExeName = Paths.get(javaCommand).toFile().getName().replaceFirst("\\.exe$", "");

            Process p = Runtime.getRuntime().exec(new String[] {psCommand, "-l"});
            psReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            /**
             * Here is a sample output when running ps command in Cygwin/MINGW64 shell.
             *       PID    PPID    PGID     WINPID   TTY         UID    STIME COMMAND
             *      1869       1    1869       7852  cons2       4096 15:29:27 /usr/bin/bash
             *      2271       1    2271      30820  cons4       4096 19:38:30 /usr/bin/bash
             *      1812       1    1812      21540  cons1       4096 15:05:03 /usr/bin/bash
             *      2216       1    2216      11328  cons3       4096 19:38:18 /usr/bin/bash
             *      1720       1    1720       5404  cons0       4096 13:46:42 /usr/bin/bash
             *      2269    2216    2269       6676  cons3       4096 19:38:21 /c/Program Files/Microsoft/jdk-11.0.14.9-hotspot/bin/java
             *      1911    1869    1869      29708  cons2       4096 15:29:31 /c/Program Files/nodejs/node
             *      2315    2271    2315      18064  cons4       4096 19:38:34 /usr/bin/ps
             */
            while ((line = psReader.readLine()) != null) {
                String[] cols = line.strip().split("\\s+");
                if (headers == null) {
                    headers = cols;
                    pidIndex = ArrayUtils.indexOf(headers, "PID");
                    ppidIndex = ArrayUtils.indexOf(headers, "PPID");
                    winpidIndex = ArrayUtils.indexOf(headers, "WINPID");
                    if (pidIndex < 0 || ppidIndex < 0 || winpidIndex < 0) {
                        logger.warning("Failed to find Java process because ps command is not the standard Cygwin ps command.");
                        return -1;
                    }
                } else if (cols.length >= headers.length) {
                    long pid = Long.parseLong(cols[pidIndex]);
                    long ppid = Long.parseLong(cols[ppidIndex]);
                    long winpid = Long.parseLong(cols[winpidIndex]);
                    PsProcess process = new PsProcess(pid, ppid, winpid);
                    psProcs.add(process);
                    if (cols[cols.length - 1].endsWith("/" + javaExeName) || cols[cols.length - 1].endsWith("/java")) {
                        javaCandidates.add(process);
                    }
                }
            }
        } catch (Exception err) {
            logger.log(Level.WARNING, "Failed to find Java process by Cygwin ps command.", err);
        } finally {
            if (psReader != null) {
                try {
                    psReader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (!javaCandidates.isEmpty()) {
            Set<Long> descendantWinpids = shellProcess.descendants().map(proc -> proc.pid()).collect(Collectors.toSet());
            long shellWinpid = shellProcess.pid();
            for (PsProcess javaCandidate: javaCandidates) {
                if (descendantWinpids.contains(javaCandidate.winpid)) {
                    return javaCandidate.winpid;
                }

                for (PsProcess psProc : psProcs) {
                    if (javaCandidate.ppid != psProc.pid) {
                        continue;
                    }

                    if (descendantWinpids.contains(psProc.winpid) || psProc.winpid == shellWinpid) {
                        return javaCandidate.winpid;
                    }

                    break;
                }
            }
        }

        return -1;
    }

    private static boolean isCygwinShell(String shellPath) {
        if (!SystemUtils.IS_OS_WINDOWS || shellPath == null) {
            return false;
        }

        String lowerShellPath = shellPath.toLowerCase();
        return lowerShellPath.endsWith("git\\bin\\bash.exe")
            || lowerShellPath.endsWith("git\\usr\\bin\\bash.exe")
            || lowerShellPath.endsWith("mintty.exe")
            || lowerShellPath.endsWith("cygwin64\\bin\\bash.exe")
            || (lowerShellPath.endsWith("bash.exe") && detectPsCommandPath(shellPath) != null)
            || (lowerShellPath.endsWith("sh.exe") && detectPsCommandPath(shellPath) != null);
    }

    private static String detectPsCommandPath(String shellPath) {
        if (shellPath == null) {
            return null;
        }

        Path psPath = Paths.get(shellPath, "..\\ps.exe");
        if (!Files.exists(psPath)) {
            psPath = Paths.get(shellPath, "..\\..\\usr\\bin\\ps.exe");
            if (!Files.exists(psPath)) {
                psPath = null;
            }
        }

        if (psPath == null) {
            return null;
        }

        return psPath.normalize().toString();
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

    private static class PsProcess {
        long pid;
        long ppid;
        long winpid;

        public PsProcess(long pid, long ppid, long winpid) {
            this.pid = pid;
            this.ppid = ppid;
            this.winpid = winpid;
        }
    }
}
