/*******************************************************************************
* Copyright (c) 2017-2021 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;

public class AdapterUtils {
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final Pattern ENCLOSING_CLASS_REGEX = Pattern.compile("^([^\\$]*)");
    public static final boolean isWin = isWindows();
    public static final boolean isMac = OS_NAME.contains("mac") || OS_NAME.contains("darwin");

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
        if (sourcePaths != null) {
            for (String path : sourcePaths) {
                Path fullpath = Paths.get(path, sourceName);
                if (Files.isRegularFile(fullpath)) {
                    return fullpath.toString();
                }
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
     * Convert the source platform's column number to the target platform's column
     * number.
     *
     * @param column
     *                              the column number from the source platform
     * @param sourceColumnsStartAt1
     *                              the source platform's column starts at 1 or not
     * @param targetColumnStartAt1
     *                              the target platform's column starts at 1 or not
     * @return the new column number
     */
    public static int convertColumnNumber(int column, boolean sourceColumnsStartAt1, boolean targetColumnStartAt1) {
        if (sourceColumnsStartAt1) {
            return targetColumnStartAt1 ? column : column - 1;
        } else {
            return targetColumnStartAt1 ? column + 1 : column;
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

        if (sourceIsUri == targetIsUri) {
            return path;
        } else if (sourceIsUri && !targetIsUri) {
            return toPath(path);
        } else {
            return toUri(path);
        }
    }

    /**
     * Convert a file uri to a file path, or null if this uri does not represent a file in the local file system.
     * @param uri
     *              the uri string
     * @return the file path
     */
    public static String toPath(String uri) {
        try {
            return Paths.get(new URI(uri)).toString();
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException
                | SecurityException e) {
            return null;
        }
    }

    /**
     * Convert a file path to an uri string.
     * @param path
     *          the file path
     * @return the uri string
     */
    public static String toUri(String path) {
        try {
            return Paths.get(path).toUri().toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Convert a file path to an url string.
     * @param path
     *              the file path
     * @return the url string
     * @throws MalformedURLException
     *              if the file path cannot be constructed to an url because of some errors.
     */
    public static String toUrl(String path) throws MalformedURLException {
        File file = new File(path);
        return file.toURI().toURL().toString();
    }

    /**
     * Check a string variable is an uri or not.
     */
    public static boolean isUri(String uriString) {
        try {
            URI uri = new URI(uriString);
            return StringUtils.isNotBlank(uri.getScheme());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException
            | SecurityException e) {
            return false;
        }
    }

    /**
     * Populate the response body with the given error message, and mark the success flag to false. At last return the response object back.
     *
     * @param response
     *              the response object
     * @param errorCode
     *              the error code
     * @param errorMessage
     *              the error message
     * @return the modified response object.
     */
    public static Response setErrorResponse(Response response, ErrorCode errorCode, String errorMessage) {
        response.body = new Responses.ErrorResponseBody(new Types.Message(errorCode.getId(), errorMessage));
        response.message = errorMessage;
        response.success = false;
        return response;
    }

    /**
     * Populate the response body with the given exception, and mark the success flag to false. At last return the response object back.
     *
     * @param response
     *              the response object
     * @param errorCode
     *              the error code
     * @param e
     *              the exception
     * @return the modified response object.
     */
    public static Response setErrorResponse(Response response, ErrorCode errorCode, Exception e) {
        String errorMessage = e.toString();
        response.body = new Responses.ErrorResponseBody(new Types.Message(errorCode.getId(), errorMessage));
        response.message = errorMessage;
        response.success = false;
        return response;
    }

    /**
     * Generate a CompletableFuture response with the given error message.
     */
    public static CompletableFuture<Response> createAsyncErrorResponse(Response response, ErrorCode errorCode, String errorMessage) {
        return CompletableFuture.completedFuture(setErrorResponse(response, errorCode, errorMessage));
    }

    /**
     * Generate a CompletableFuture response with the given exception.
     */
    public static CompletableFuture<Response> createAsyncErrorResponse(Response response, ErrorCode errorCode, Exception e) {
        return CompletableFuture.completedFuture(setErrorResponse(response, errorCode, e));
    }

    public static CompletionException createCompletionException(String message, ErrorCode errorCode, Throwable cause) {
        return new CompletionException(new DebugException(message, cause, errorCode.getId()));
    }

    public static CompletionException createCompletionException(String message, ErrorCode errorCode) {
        return new CompletionException(new DebugException(message, errorCode.getId()));
    }

    public static DebugException createUserErrorDebugException(String message, ErrorCode errorCode) {
        return new DebugException(message, errorCode.getId(), true);
    }

    /**
     * Calculate SHA-256 Digest of given string.
     * @param content
     *
     *              string to digest
     * @return      string of Hex digest
     */
    public static String getSHA256HexDigest(String content) {
        byte[] hashBytes = null;
        try {
            hashBytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // ignore it.
        }
        StringBuffer buf = new StringBuffer();
        if (hashBytes != null) {
            for (byte b : hashBytes) {
                buf.append(Integer.toHexString((b & 0xFF) + 0x100).substring(1));
            }
        }
        return buf.toString();
    }

    /**
     * Decode the uri string.
     * @param uri
     *          the uri string
     * @return the decoded uri
     */
    public static String decodeURIComponent(String uri) {
        try {
            return URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return uri;
        }
    }

    /**
     * Find the mapped lines based on the given line number.
     *
     * The line mappings format is as follows:
     * - [i]: key
     * - [i+1]: value
     */
    public static int[] binarySearchMappedLines(int[] lineMappings, int targetLine) {
        if (lineMappings == null || lineMappings.length == 0 || lineMappings.length % 2 != 0) {
            return null;
        }

        final int MAX = lineMappings.length / 2 - 1;
        int low = 0;
        int high = MAX;
        int found = -1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            int actualMid = mid * 2;
            if (lineMappings[actualMid] == targetLine) {
                found = mid;
                break;
            }

            if (lineMappings[actualMid] < targetLine) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (found == -1) {
            return null;
        }

        // Find the duplicates in the sorted array
        int left = found;
        while ((left - 1) >= 0 && lineMappings[(left - 1) * 2] == targetLine) {
            left--;
        }

        int right = found;
        while ((right + 1) <= MAX && lineMappings[(right + 1) * 2] == targetLine) {
            right++;
        }

        int[] values = new int[right - left + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = lineMappings[(left + i) * 2 + 1];
        }

        return values;
    }
}
