package com.froggylord.musicoverlay.media;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;

/**
 * Owns the platform helper process. Each OS gets a small bundled script that
 * reads its native media session (MPRIS on Linux, SMTC on Windows, the
 * now-playing centre on macOS) and writes {@code nowplaying.json}. This class
 * extracts the right one, launches it, drains its log output, and restarts it if
 * it dies unexpectedly.
 */
public final class HelperProcessManager {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final String RESOURCE_DIR = "/assets/musicoverlay/bin/";

    private enum Os { LINUX, WINDOWS, MAC, OTHER }

    private volatile Process process;
    private volatile boolean stopping = false;
    private Path scriptPath;
    private String[] launchCommand;

    public void start() {
        Os os = detectOs();
        try {
            switch (os) {
                case LINUX -> prepareLinux();
                case WINDOWS -> prepareWindows();
                case MAC -> prepareMac();
                default -> {
                    LOG.warn("[MusicOverlay] no helper for OS '{}'; overlay will stay empty.",
                            System.getProperty("os.name"));
                    return;
                }
            }
            if (launchCommand == null) return;
            spawn();
            startSupervisor();
        } catch (Exception e) {
            LOG.error("[MusicOverlay] failed to start helper", e);
        }
    }

    public void stop() {
        stopping = true;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            LOG.info("[MusicOverlay] helper stopped.");
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    // ---- per-OS preparation ----

    private void prepareLinux() throws IOException {
        if (!commandExists("playerctl")) {
            LOG.error("[MusicOverlay] 'playerctl' not found. Install it so the overlay can read "
                    + "your media session (e.g. 'sudo pacman -S playerctl' on CachyOS/Arch, "
                    + "or 'sudo apt install playerctl' on Debian/Ubuntu).");
            return;
        }
        String python = firstPresent("python3", "python");
        if (python == null) {
            LOG.error("[MusicOverlay] python3 not found; cannot run the Linux helper.");
            return;
        }
        scriptPath = extract("nowplaying-linux.py");
        if (scriptPath == null) return;
        makeExecutable(scriptPath);
        launchCommand = new String[]{python, scriptPath.toString()};
    }

    private void prepareWindows() throws IOException {
        scriptPath = extract("nowplaying-windows.ps1");
        if (scriptPath == null) return;
        launchCommand = new String[]{
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", scriptPath.toString()
        };
    }

    private void prepareMac() throws IOException {
        scriptPath = extract("nowplaying-macos.sh");
        if (scriptPath == null) return;
        makeExecutable(scriptPath);
        launchCommand = new String[]{"/bin/bash", scriptPath.toString()};
    }

    // ---- process lifecycle ----

    private void spawn() throws IOException {
        Files.createDirectories(BridgeFiles.DIR);
        Files.createDirectories(BridgeFiles.ART_DIR);

        ProcessBuilder pb = new ProcessBuilder(launchCommand);
        pb.redirectErrorStream(true);
        pb.directory(scriptPath.getParent().toFile());
        pb.environment().put("MC_NOWPLAYING_DIR", BridgeFiles.DIR.toString());
        process = pb.start();

        Thread drain = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    LOG.info("[helper] {}", line);
                }
            } catch (IOException ignored) {}
        }, "MusicOverlay-HelperLog");
        drain.setDaemon(true);
        drain.start();

        LOG.info("[MusicOverlay] helper started: {}", String.join(" ", launchCommand));
    }

    private void startSupervisor() {
        Thread sup = new Thread(() -> {
            while (!stopping) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                if (stopping) return;
                if (process != null && !process.isAlive()) {
                    LOG.warn("[MusicOverlay] helper exited (code {}); restarting.", process.exitValue());
                    try {
                        spawn();
                    } catch (IOException e) {
                        LOG.error("[MusicOverlay] helper restart failed", e);
                        return;
                    }
                }
            }
        }, "MusicOverlay-HelperSupervisor");
        sup.setDaemon(true);
        sup.start();
    }

    // ---- helpers ----

    private Path extract(String name) {
        Path targetDir = FabricLoader.getInstance().getGameDir().resolve("musicoverlay").resolve("bin");
        Path target = targetDir.resolve(name);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = getClass().getResourceAsStream(RESOURCE_DIR + name)) {
                if (in == null) {
                    LOG.error("[MusicOverlay] bundled helper {} missing from jar", name);
                    return null;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            LOG.error("[MusicOverlay] failed to extract {}", name, e);
            return null;
        }
    }

    private static Os detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return Os.WINDOWS;
        if (name.contains("mac") || name.contains("darwin")) return Os.MAC;
        if (name.contains("nux") || name.contains("nix") || name.contains("aix")) return Os.LINUX;
        return Os.OTHER;
    }

    private static String firstPresent(String... cmds) {
        for (String c : cmds) if (commandExists(c)) return c;
        return null;
    }

    private static boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "command -v " + cmd)
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void makeExecutable(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            // non-POSIX filesystem; the interpreter is invoked explicitly anyway.
        }
    }
}
