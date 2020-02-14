package com.microsoft.java.debug.plugin.internal;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

public class ResolveJavaExecutableHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
	private static final String[] candidateJavaExecs = {
        "java",
        "java.exe",
        "javaw",
        "javaw.exe",
        "j9",
        "j9.exe",
        "j9w",
        "j9w.exe"
    };
	private static final String[] candidateJavaBins = {
        File.separator,
        "bin" + File.separatorChar,
        "jre" + File.separatorChar + "bin" + File.separatorChar
    };

    public static String resolveJavaExecutable(List<Object> arguments) throws Exception {
        try {
            String mainClass = (String) arguments.get(0);
            String projectName = (String) arguments.get(1);
            IJavaProject targetProject = null;
            if (StringUtils.isNotBlank(projectName)) {
                targetProject = JdtUtils.getJavaProject(projectName);
            } else {
                List<IJavaProject> targetProjects = ResolveClasspathsHandler.getJavaProjectFromType(mainClass);
                if (!targetProjects.isEmpty()) {
                    targetProject = targetProjects.get(0);
                }
            }

            if (targetProject == null) {
                return null;
            }

            IVMInstall vmInstall = JavaRuntime.getVMInstall(targetProject);
            if (vmInstall == null || vmInstall.getInstallLocation() == null) {
                return null;
            }
            
            File exe = findJavaExecutable(vmInstall.getInstallLocation());
			if (exe == null) {
                return null;
            }

			return exe.getAbsolutePath();
        } catch (CoreException e) {
            logger.log(Level.SEVERE, "Failed to resolve java executable: " + e.getMessage(), e);
        }

        return null;
    }

    private static File findJavaExecutable(File vmInstallLocation) {
        boolean isBin = Objects.equals("bin", vmInstallLocation.getName());
		for (int i = 0; i < candidateJavaExecs.length; i++) {
			for (int j = 0; j < candidateJavaBins.length; j++) {
				if (!isBin && j == 0) {
					continue;
				}
				File javaFile = new File(vmInstallLocation, candidateJavaBins[j] + candidateJavaExecs[i]);
				if (javaFile.isFile()) {
					return javaFile;
				}
			}
		}
		return null;
    }
}
