package dev.glaeos.woven.backup;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.BitSet;

public class BackupManager implements Closeable {

    // TODO file locking

    private static final int DEFAULT_MAX_BACKUPS = 10;
    private static final @Nonnull String DIRECTORY_LOCK_FILE = ".lock";

    private final @Nonnull File directory;
    private int maxBackups;

    private final @Nonnull FileInputStream directoryHandle;
    private final @Nonnull FileLock directoryLock;

    private final @Nonnull BitSet slots;

    public BackupManager(@Nonnull File directory, @Nonnegative int maxBackups) throws IOException {
        if (maxBackups < 1) {
            throw new IllegalArgumentException("Max number of backups must be at least 1 (" + maxBackups + " < 1)");
        }

        this.directory = directory;
        this.maxBackups = maxBackups;
        checkDirectory();

        directoryHandle = new FileInputStream(new File(directory, DIRECTORY_LOCK_FILE));
        try {
            directoryLock = directoryHandle.getChannel().lock();
        } catch (Exception e) {
            directoryHandle.close();
            throw e;
        }

        this.slots = new BitSet(maxBackups);
    }

    public BackupManager(@Nonnull File directory) throws IOException {
        this(directory, DEFAULT_MAX_BACKUPS);
    }

    public BackupManager(@Nonnull String directory, @Nonnegative int maxBackups) throws IOException {
        this(new File(directory), maxBackups);
    }

    public BackupManager(@Nonnull String directory) throws IOException {
        this(new File(directory), DEFAULT_MAX_BACKUPS);
    }

    private void checkDirectory() throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new NoSuchFileException("Failed to find or create backup directory \""
                    + directory.getAbsolutePath() + "\"");
            }

            return;
        }

        if (!directory.isDirectory()) {
            throw new NotDirectoryException("Backup directory \"" + directory.getAbsolutePath()
                + "\" is not a directory");
        }
    }

    public @Nonnull File getDirectory() {
        return directory;
    }

    public @Nonnegative int getNumBackups() {
        return slots.cardinality();
    }

    public @Nonnegative int getMaxBackups() {
        return maxBackups;
    }

    public void setMaxBackups(@Nonnegative int maxBackups) {
        if (maxBackups < 1) {
            throw new IllegalArgumentException("Max number of backups must be at least 1 (" + maxBackups + " < 1)");
        }

        if (maxBackups < this.maxBackups) {
            slots.clear(maxBackups, this.maxBackups);
            // TODO delete backups
        }

        this.maxBackups = maxBackups;
    }

    @Override
    public void close() throws IOException {
        try {
            directoryLock.release();
            directoryHandle.close();
        } catch (IOException ignored) {}
    }

}
