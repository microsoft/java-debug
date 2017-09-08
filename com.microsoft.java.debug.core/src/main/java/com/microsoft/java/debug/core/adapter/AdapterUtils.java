/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.java.debug.core.adapter.Messages.Response;

public class AdapterUtils {
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final Pattern ENCLOSING_CLASS_REGEX = Pattern.compile("^([^\\$]*)");

    /**
     * Check if the OS is windows or not.
     */
    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    /**
     * Search the absolute path of the java file under the specified source path directory.
     * @param sourcePaths
     *                  the project source directories
     * @param sourceName
     *                  the java file path
     * @return the absolute file path
     */
    public static String sourceLookup(String[] sourcePaths, String sourceName) {
        for (String path : sourcePaths) {
            Path fullpath = Paths.get(path, sourceName);
            if (Files.isRegularFile(fullpath)) {
                return fullpath.toString();
            }
        }
        return null;
    }

    /**
     * Get the enclosing type name of the given fully qualified name.
     * <pre>
     * a.b.c        ->   a.b.c
     * a.b.c$1      ->   a.b.c
     * a.b.c$1$2    ->   a.b.c
     * </pre>
     * @param fullyQualifiedName
     *                      fully qualified name
     * @return the enclosing type name
     */
    public static String parseEnclosingType(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return null;
        }
        Matcher matcher = ENCLOSING_CLASS_REGEX.matcher(fullyQualifiedName);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * Convert the source platform's line number to the target platform's line number.
     *
     * @param line
     *           the line number from the source platform
     * @param sourceLinesStartAt1
     *           the source platform's line starts at 1 or not
     * @param targetLinesStartAt1
     *           the target platform's line starts at 1 or not
     * @return the new line number
     */
    public static int convertLineNumber(int line, boolean sourceLinesStartAt1, boolean targetLinesStartAt1) {
        if (sourceLinesStartAt1) {
            return targetLinesStartAt1 ? line : line - 1;
        } else {
            return targetLinesStartAt1 ? line + 1 : line;
        }
    }

    /**
     * Convert the source platform's path format to the target platform's path format.
     *
     * @param path
     *           the path value from the source platform
     * @param sourceIsUri
     *           the path format of the source platform is uri or not
     * @param targetIsUri
     *           the path format of the target platform is uri or not
     * @return the new path value
     */
    public static String convertPath(String path, boolean sourceIsUri, boolean targetIsUri) {
        if (path == null) {
            return null;
        }

        if (sourceIsUri) {
            if (targetIsUri) {
                return path;
            } else {
                try {
                    return Paths.get(new URI(path)).toString();
                } catch (URISyntaxException | IllegalArgumentException
                        | FileSystemNotFoundException | SecurityException e) {
                    return null;
                }
            }
        } else {
            if (targetIsUri) {
                try {
                    return Paths.get(path).toUri().toString();
                } catch (InvalidPathException e) {
                    return null;
                }
            } else {
                return path;
            }
        }
    }

    /**
     * Generate an error response with the given error message.
     *
     * @param response
     *              the response object
     * @param errorCode
     *              the error code
     * @param errorMessage
     *              the error message
     */
    public static void setErrorResponse(Response response, ErrorCode errorCode, String errorMessage) {
        response.body = new Responses.ErrorResponseBody(new Types.Message(errorCode.getId(), errorMessage));
        response.message = errorMessage;
        response.success = false;
    }

    /**
     * Generate an error response with the given exception.
     *
     * @param response
     *              the response object
     * @param errorCode
     *              the error code
     * @param e
     *              the exception
     */
    public static void setErrorResponse(Response response, ErrorCode errorCode, Exception e) {
        String errorMessage = e.toString();
        response.body = new Responses.ErrorResponseBody(new Types.Message(errorCode.getId(), errorMessage));
        response.message = errorMessage;
        response.success = false;
    }
}
