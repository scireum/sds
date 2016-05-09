/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.CRC32;

/**
 * Synchronizes a local directory against a SDS server.
 * <p>
 * This client can be started as command line tool and will synchronize a local directory against an
 * artifact stored in a SDS server.
 * <p>
 * To make this as lightweight as possible, no external libraries are referenced. Therefore only this
 * class file is required and can be launched using <tt>java SDS ...</tt>.
 */
public class SDS {

    //------------------------------------------------------------------------
    // Command Line Handling
    //------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("SDS - Software Distribution System");
        System.out.println("-----------------------------------------------");
        SDS instance = parseCommandLineAndCreateInstance(args);
        instance.verifyParameters();
        instance.execute();
    }

    private static SDS parseCommandLineAndCreateInstance(String[] args) {
        SDS result = new SDS();
        result.applyParameter("server", System.getenv("SDS_SERVER"));
        result.applyParameter("identity", System.getenv("SDS_IDENTITY"));
        result.applyParameter("key", System.getenv("SDS_KEY"));

        List<String> argsList = Arrays.asList(args);
        Iterator<String> iter = argsList.iterator();
        while (iter.hasNext()) {
            String current = iter.next();
            if (current.startsWith("-")) {
                String key = current.substring(1);
                if (!result.applySwitch(key)) {
                    if (!iter.hasNext()) {
                        fail("Missing value for parameter '%s'", key);
                    }
                    String value = iter.next();
                    result.applyParameter(key, value);
                }
            } else {
                result.applyParameter("command", current);
                break;
            }
        }
        if (iter.hasNext()) {
            result.applyParameter("artifact", iter.next());
        }
        if (iter.hasNext()) {
            result.applyParameter("version", iter.next());
        }

        return result;
    }

    private boolean applySwitch(String key) {
        try {
            Field field = getClass().getDeclaredField(key);
            if (!boolean.class.equals(field.getType())) {
                return false;
            }
            field.set(this, true);
            return true;
        } catch (Exception e) {
            verbose(e);
            return false;
        }
    }

    private void applyParameter(String key, String value) {
        try {
            Field field = getClass().getDeclaredField(key);
            field.set(this, value);
        } catch (Exception e) {
            verbose(e);
            fail("Unknown parameter: %s", key);
        }
    }

    private void verifyParameters() {
        System.out.println();
        if (empty(server)) {
            fail("Please provide a server.");
        }
        if (empty(command)) {
            fail("Please specify which command to execute.");
        }
        System.out.printf("   Server:      %s%n", server);
        System.out.printf("   Identity:    %s%n", identity);
        System.out.printf("   Key present: %s%n", !empty(key));
        System.out.println();
    }

    //------------------------------------------------------------------------
    // Helper methods
    //------------------------------------------------------------------------

    private boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("UTF-8", e);
        }
    }

    private String hashMD5(String value) {
        try {
            byte[] bytesOfMessage = value.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytesOfMessage);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Throwable e) {
            verbose(e);
            fail("Cannot compute MD5 hashes...: %s", e.getMessage());
            return null;
        }
    }

    private long crc(File file) {
        try {
            CRC32 crc = new CRC32();
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read = in.read(buffer);
                while (read > 0) {
                    crc.update(buffer, 0, read);
                    read = in.read(buffer);
                }
                return crc.getValue();
            }
        } catch (IOException ignored) {
            fail("Failed to compute the CRC of %s", file.getAbsolutePath());
            return 0;
        }
    }

    private File getExpectedFile(String path) {
        File file = new File(".");
        for (String pathElement : path.split("/")) {
            file = new File(file, pathElement);
        }
        return file;
    }

    //------------------------------------------------------------------------
    // Instance variables
    //------------------------------------------------------------------------

    private String server;
    private String identity;
    private String key;

    private String command;
    private String artifact;
    private String version;
    private String filter;

    private boolean debug;
    private String timestamp =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()).replaceAll("[^0-9]", "_");

    //------------------------------------------------------------------------
    // Logging and error handling
    //------------------------------------------------------------------------

    @SuppressWarnings("CallToPrintStackTrace")
    private void verbose(Object e) {
        if (debug) {
            if (e instanceof Throwable) {
                ((Throwable) e).printStackTrace();
            } else {
                System.err.println(e);
            }
        }
    }

    private static void fail(String msg, Object... params) {
        System.err.println();
        System.err.println();
        if (params.length > 0) {
            System.err.println(String.format(msg, params));
        } else {
            System.err.println(msg);
        }
        System.err.println();
        System.err.println();
        System.err.println("Usage: sds -server <name> -identity <identity> -key <key> COMMAND [ARTIFACT] [VERSION]");
        System.err.println();
        System.err.println("You can omit the parameters if the appropriate environment variables "
                           + "(SDS_SERVER, SDS_IDENTITY, SDS_KEY) are filled.");
        System.err.println();
        System.err.println("Commands");
        System.err.println("--------");
        System.err.println();
        System.err.println("list   - Lists all versions of the given artifact, "
                           + "or all known artifacts if no artifact name is given");
        System.err.println("pull   - Synchronizes the given artifact against the current directory.");
        System.err.println("         WARNING: This will delete all files in the current directory "
                           + "if they are not part of the artifact distribution.");
        System.err.println("verify - Verifies the local directory against the given artifact on the server. "
                           + "Show all changes that would be performed.");
        System.err.println("monkey - Synchronizes the given artifact against the current directory, "
                           + "but ask if a change should be performed or not. "
                           + "(Use -filter <pattern> to limit which files to ask for).");
        System.err.println();
        System.exit(-1);
    }

    //------------------------------------------------------------------------
    // Server communication
    //------------------------------------------------------------------------

    private void download(String uri, OutputStream target, boolean showProgress) {
        try {
            URL url = makeURL(uri);
            verbose(url);
            byte[] buffer = new byte[8192];
            long bytesSoFar = 0;
            long lastBytesReported = 0;
            long lastTimeReported = System.currentTimeMillis();
            try (InputStream in = url.openConnection().getInputStream()) {
                int read = in.read(buffer);
                while (read > 0) {
                    target.write(buffer, 0, read);
                    if (showProgress) {
                        bytesSoFar += read;
                        long now = System.currentTimeMillis();
                        if (now - lastTimeReported > 5000) {
                            long timeDiff = now - lastTimeReported;
                            long bytesDiff = bytesSoFar - lastBytesReported;

                            System.out.println(String.format("Downloaded %s kB (%s kB/s)",
                                                             bytesSoFar / 1024,
                                                             (bytesDiff / 1024) / (timeDiff / 1000)));

                            lastTimeReported = now;
                            lastBytesReported = bytesSoFar;
                        }
                    }
                    read = in.read(buffer);
                }
                if (showProgress) {
                    long timeDiff = (System.currentTimeMillis() - lastTimeReported) / 1000;
                    if (timeDiff == 0) {
                        timeDiff = 1;
                    }
                    long bytesDiff = bytesSoFar - lastBytesReported;
                    System.out.println(String.format("Downloaded %s kB (%s kB/s)",
                                                     bytesSoFar / 1024,
                                                     (bytesDiff / 1024) / timeDiff));
                }
            }
        } catch (IOException e) {
            fail("An IO error occurred while calling '%s': %s", uri, e.getMessage());
        }
    }

    private URL makeURL(String uri) {
        try {
            if (!server.startsWith("http")) {
                server = "https://" + server;
            }
            if (empty(identity)) {
                return new URL(server + uri);
            }

            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String input = identity + timestamp + key;
            String hash = hashMD5(input);

            if (uri.contains("?")) {
                return new URL(server
                               + uri
                               + "&user="
                               + urlEncode(identity)
                               + "&timestamp="
                               + urlEncode(timestamp)
                               + "&hash="
                               + urlEncode(hash));
            } else {
                return new URL(server
                               + uri
                               + "?user="
                               + urlEncode(identity)
                               + "&timestamp="
                               + urlEncode(timestamp)
                               + "&hash="
                               + urlEncode(hash));
            }
        } catch (MalformedURLException e) {
            verbose(e);
            fail("Cannot create a valid url. Please specify the server without a leading '/': %s", e.getMessage());
            return null;
        }
    }

    private void downloadAndVerify(String baseURI, File file, Object expectedFile) {
        int retries = 3;

        while (retries-- > 0) {
            try {
                doDownloadFile(baseURI, expectedFile, file);
                break;
            } catch (Throwable e) {
                verbose(e);
                if (retries <= 0) {
                    fail(e.getMessage());
                }
            }
        }
    }

    private void doDownloadFile(String baseURI, Object expectedFile, File target) throws IOException {
        File buffer = File.createTempFile("sds-", ".sds");
        try {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(buffer))) {
                download(baseURI + "/" + get(expectedFile, "name"), out, true);
            }
            if (buffer.length() != (Long) get(expectedFile, "size")) {
                throw new IllegalStateException("Length of downloaded file '"
                                                + get(expectedFile, "name")
                                                + "' does not match!");
            }
            if (crc(buffer) != (Long) get(expectedFile, "crc")) {
                throw new IllegalStateException("CRC of downloaded file '"
                                                + get(expectedFile, "name")
                                                + "' does not match!");
            }
            Files.move(buffer.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            buffer.delete();
        }
    }

    private Object jsonCall(String uri) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            download(uri, buffer, debug);

            return parseJSON(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer.toByteArray()),
                                                                      "UTF-8")));
        } catch (Throwable e) {
            verbose(e);
            fail("Cannot download '%s' as JSON", uri);
            return null;
        }
    }

    //------------------------------------------------------------------------
    // Built-in JSON parser...
    //------------------------------------------------------------------------

    private Object parseJSON(Reader input) throws IOException {
        char next = readFirstNonWhiteSpace(input);
        if (next == '{') {
            return parseMap(input);
        } else if (next == '"') {
            return parseString(input);
        } else if (next == '[') {
            return parseArray(input);
        } else if (Character.isLetterOrDigit(next)) {
            StringBuilder sb = new StringBuilder();
            while (Character.isLetterOrDigit(next) || next == '-') {
                sb.append(next);
                input.mark(1);
                next = (char) input.read();
            }
            input.reset();
            String str = sb.toString();
            if ("true".equals(str)) {
                return true;
            } else if ("false".equals(str)) {
                return false;
            } else if ("null".equals(str)) {
                return null;
            } else if (str.contains("-")) {
                return LocalDate.parse(str);
            } else {
                return Long.valueOf(str);
            }
        } else {
            throw new IllegalArgumentException("Unexpected JSON character: " + next);
        }
    }

    private Object parseArray(Reader input) throws IOException {
        List<Object> result = new ArrayList<>();
        input.mark(1);
        if (input.read() == ']') {
            return result;
        }
        input.reset();
        while (true) {
            result.add(parseJSON(input));
            int next = readFirstNonWhiteSpace(input);
            if (next != ',') {
                if (next != ']') {
                    throw new IllegalArgumentException("Unexpected JSON character: "
                                                       + (char) next
                                                       + ". Expected a ']'!");
                }
                break;
            }
        }
        return result;
    }

    private String parseString(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();
        int next = input.read();
        while (next != 0 && next != '"') {
            if (next == '\\') {
                result.append((char) input.read());
            } else {
                result.append((char) next);
            }
            next = input.read();
        }
        if (next != '"') {
            throw new IllegalArgumentException("Unexpected JSON character: " + (char) next + ". Expected a '\"'!");
        }
        return result.toString();
    }

    private char readFirstNonWhiteSpace(Reader input) throws IOException {
        int next = input.read();
        while (next == ' ' || next == 13 || next == 10) {
            next = input.read();
        }

        return (char) next;
    }

    private Object parseMap(Reader input) throws IOException {
        Map<String, Object> result = new TreeMap<>();

        int next = readFirstNonWhiteSpace(input);
        while (next != '}') {
            if (next != '"') {
                throw new IllegalArgumentException("Unexpected JSON character: " + (char) next + ". Expected a '\"'!");
            }
            String key = parseString(input);
            next = readFirstNonWhiteSpace(input);
            if (next != ':') {
                throw new IllegalArgumentException("Unexpected JSON character: " + (char) next + ". Expected a ':'!");
            }
            result.put(key, parseJSON(input));
            next = readFirstNonWhiteSpace(input);
            if (next != ',') {
                if (next != '}') {
                    throw new IllegalArgumentException("Unexpected JSON character: "
                                                       + (char) next
                                                       + ". Expected a '}'!");
                }
                break;
            }
            next = readFirstNonWhiteSpace(input);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Object get(Object input, String key) {
        return ((Map<String, Object>) input).get(key);
    }

    @SuppressWarnings("unchecked")
    private List<Object> asArray(Object input) {
        return (List<Object>) input;
    }

    //------------------------------------------------------------------------
    // Commands...
    //------------------------------------------------------------------------

    private void execute() {
        try {
            Method method = getClass().getMethod(command);
            method.invoke(this);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            verbose(e);
            fail("Unknown command: %s", command);
        } catch (InvocationTargetException e) {
            verbose(e);
            fail(e.getCause().getMessage() + " (" + e.getClass().getName() + ")");
        }
    }

    public void list() {
        if (empty(artifact)) {
            listArtifacts();
        } else {
            listVersions();
        }
    }

    private void listVersions() {
        System.out.printf("Versions available for %s on: %s%n", artifact, server);
        System.out.println("-----------------------------------------------");
        System.out.println();
        Object result = jsonCall("/artifacts/" + artifact);
        if ((boolean) get(result, "error")) {
            fail((String) get(result, "message"));
        }

        List<Object> versions = asArray(get(result, "versions"));
        versions.forEach(o -> System.out.println(get(o, "name") + " (" + get(o, "date") + ", " + get(o, "size") + ")"));
        System.out.println();
    }

    private void listArtifacts() {
        System.out.printf("Available artifacts on: %s%n", server);
        System.out.println("-----------------------------------------------");
        System.out.println();
        Object result = jsonCall("/artifacts");
        if ((boolean) get(result, "error")) {
            fail((String) get(result, "message"));
        }
        List<Object> artifacts = asArray(get(result, "artifacts"));
        artifacts.forEach(o -> System.out.println(get(o, "name")));

        System.out.println();
    }

    private Function<String, Boolean> syncHandler;
    private Set<String> allowedFiles = new TreeSet<>();
    private AtomicInteger filesChecked = new AtomicInteger();
    private AtomicInteger filesDownloaded = new AtomicInteger();
    private AtomicInteger filesChanged = new AtomicInteger();
    private AtomicInteger filesAdded = new AtomicInteger();
    private AtomicInteger filesRemoved = new AtomicInteger();

    public void verify() {
        syncHandler = s -> {
            System.out.println(s);
            return false;
        };
        sync();
    }

    public void monkey() {
        syncHandler = s -> {
            try {
                if (empty(filter) || s.toLowerCase().contains(filter.toLowerCase())) {
                    System.out.println(s);
                    System.out.print("Should I perform this change (y/N)? ");
                    String answer = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    if (answer == null) {
                        System.out.println("Skipped...");
                        return false;
                    }
                    return "y".equalsIgnoreCase(answer.trim());
                } else {
                    return false;
                }
            } catch (IOException e) {
                fail(e.getMessage());
                return false;
            }
        };
        sync();
    }

    public void pull() {
        syncHandler = s -> {
            System.out.println(s);
            return true;
        };
        sync();
    }

    private void sync() {
        if (empty(artifact)) {
            fail("Please specify an artifact name!");
        }
        if (empty(version)) {
            version = "latest";
        }
        String baseURI = "/artifacts/" + artifact + "/" + version;
        System.out.printf("Synchronizing: %s (%s) from %s%n", artifact, version, server);
        System.out.println("-----------------------------------------------");
        System.out.println();
        Object result = jsonCall(baseURI + "/_index");
        if ((boolean) get(result, "error")) {
            fail((String) get(result, "message"));
        }

        allowedFiles.add("SDS.class");
        allowedFiles.add("trash/.sdsignore");
        List<Object> expectedFiles = asArray(get(result, "files"));
        for (Object expectedFile : expectedFiles) {
            filesChecked.incrementAndGet();
            String name = (String) get(expectedFile, "name");
            addAllowedPath(name);
            File file = getExpectedFile(name);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!name.endsWith(".sdsignore")) {
                if (!file.exists()) {
                    if (syncHandler.apply(" + " + name)) {
                        filesAdded.incrementAndGet();
                        filesDownloaded.incrementAndGet();
                        downloadAndVerify(baseURI, file, expectedFile);
                    }
                } else if (file.length() != (Long) get(expectedFile, "size")) {
                    if (syncHandler.apply(" > " + name)) {
                        filesChanged.incrementAndGet();
                        filesDownloaded.incrementAndGet();
                        downloadAndVerify(baseURI, file, expectedFile);
                    }
                } else if (crc(file) != (Long) get(expectedFile, "crc")) {
                    if (syncHandler.apply(" * " + name)) {
                        filesChanged.incrementAndGet();
                        filesDownloaded.incrementAndGet();
                        downloadAndVerify(baseURI, file, expectedFile);
                    }
                }
            }
        }
        scanUnexpected("", new File("."));
        System.out.println("-----------------------------------------------");
        System.out.println(String.format("Files checked......%10s", filesChecked));
        System.out.println(String.format("Files added........%10s", filesAdded));
        System.out.println(String.format("Files changed......%10s", filesChanged));
        System.out.println(String.format("Files downloaded...%10s", filesDownloaded));
        System.out.println(String.format("Files removed......%10s", filesRemoved));
        System.out.println("-----------------------------------------------");
        System.out.println();
    }

    private void addAllowedPath(String name) {
        String uriPart = null;
        for (String part : name.split("/")) {
            if (uriPart == null) {
                uriPart = part;
            } else {
                uriPart += "/" + part;
            }
            allowedFiles.add(uriPart);
        }
    }

    private void scanUnexpected(String prefix, File file) {
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile() && !allowedFiles.contains(prefix + child.getName())) {
                if (!allowedFiles.contains(prefix + child.getName() + ".sdsignore")) {
                    if (syncHandler.apply(" - " + prefix + child.getName())) {
                        filesRemoved.incrementAndGet();
                        moveToTrash(prefix, child);
                    }
                }
            } else if (child.isDirectory() && !".".equals(child.getName()) && !"..".equals(child.getName())) {
                if (!allowedFiles.contains(prefix + child.getName() + "/.sdsignore")) {
                    if (!allowedFiles.contains(prefix + child.getName())) {
                        moveToTrash(prefix, child);
                    } else {
                        scanUnexpected(prefix + child.getName() + "/", child);
                    }
                }
            }
        }
    }

    private void moveToTrash(String prefix, File child) {
        try {
            File target = getExpectedFile("trash/" + timestamp + "/" + prefix + child.getName());
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
}