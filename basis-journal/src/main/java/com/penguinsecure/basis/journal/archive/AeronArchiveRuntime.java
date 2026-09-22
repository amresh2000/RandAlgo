package com.penguinsecure.basis.journal.archive;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.core.time.system.SystemMonotonicClock;
import com.penguinsecure.basis.journal.config.JournalConfiguration;
import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.ExclusivePublication;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.IOException;
import java.nio.file.Files;

/** Embedded Media Driver + Archive production-v1 runtime. Launch from a cold lifecycle thread. */
public final class AeronArchiveRuntime implements ArchiveRuntime {
    private final ArchivingMediaDriver driver;
    private final Aeron aeron;
    private final AeronArchive archive;
    private final long recordingSubscriptionId;
    private final AeronEventJournal journal;
    private final MonotonicClock clock;

    private AeronArchiveRuntime(
            final ArchivingMediaDriver driver,
            final Aeron aeron,
            final AeronArchive archive,
            final long recordingSubscriptionId,
            final AeronEventJournal journal,
            final MonotonicClock clock) {
        this.driver = driver;
        this.aeron = aeron;
        this.archive = archive;
        this.recordingSubscriptionId = recordingSubscriptionId;
        this.journal = journal;
        this.clock = clock;
    }

    public static AeronArchiveRuntime launch(
            final JournalConfiguration configuration, final boolean deleteOnStart)
            throws IOException {
        return launch(configuration, deleteOnStart, new SystemMonotonicClock());
    }

    public static AeronArchiveRuntime launch(
            final JournalConfiguration configuration,
            final boolean deleteOnStart,
            final MonotonicClock clock)
            throws IOException {
        if (clock == null) throw new IllegalArgumentException("monotonic clock required");
        Files.createDirectories(configuration.mediaDriverDirectory());
        Files.createDirectories(configuration.archiveDirectory());
        final MediaDriver.Context driverContext =
                new MediaDriver.Context()
                        .aeronDirectoryName(configuration.mediaDriverDirectory().toString())
                        .dirDeleteOnStart(deleteOnStart)
                        .threadingMode(ThreadingMode.DEDICATED)
                        .spiesSimulateConnection(true);
        final Archive.Context archiveContext =
                new Archive.Context()
                        .archiveDir(configuration.archiveDirectory().toFile())
                        .markFileDir(configuration.archiveDirectory().toFile())
                        .deleteArchiveOnStart(deleteOnStart)
                        .threadingMode(ArchiveThreadingMode.DEDICATED)
                        .recordingEventsEnabled(false)
                        .controlChannel("aeron:udp?endpoint=localhost:0")
                        .controlChannelEnabled(false)
                        .replicationChannel("aeron:udp?endpoint=localhost:0")
                        .localControlChannel(CommonContext.IPC_CHANNEL)
                        .archiveClientContext(
                                new AeronArchive.Context()
                                        .controlResponseChannel(CommonContext.IPC_CHANNEL));
        ArchivingMediaDriver driver = null;
        Aeron aeron = null;
        AeronArchive archive = null;
        try {
            driver = ArchivingMediaDriver.launch(driverContext, archiveContext);
            aeron =
                    Aeron.connect(
                            new Aeron.Context()
                                    .aeronDirectoryName(
                                            configuration.mediaDriverDirectory().toString()));
            archive =
                    AeronArchive.connect(
                            new AeronArchive.Context()
                                    .aeron(aeron)
                                    .controlRequestChannel(CommonContext.IPC_CHANNEL)
                                    .controlResponseChannel(CommonContext.IPC_CHANNEL));
            final long subscriptionId =
                    archive.startRecording(
                            configuration.channel(),
                            configuration.streamId(),
                            SourceLocation.LOCAL);
            final ExclusivePublication publication =
                    aeron.addExclusivePublication(
                            configuration.channel(), configuration.streamId());
            return new AeronArchiveRuntime(
                    driver,
                    aeron,
                    archive,
                    subscriptionId,
                    new AeronEventJournal(
                            archive,
                            publication,
                            configuration.channel(),
                            configuration.streamId()),
                    clock);
        } catch (RuntimeException exception) {
            if (archive != null) archive.close();
            if (aeron != null) aeron.close();
            if (driver != null) driver.close();
            throw exception;
        }
    }

    @Override
    public EventJournal journal() {
        return journal;
    }

    @Override
    public void close() {
        final long targetPosition = journal.publicationPosition();
        final long deadline = clock.nanoTime() + 5_000_000_000L;
        while (targetPosition > 0
                && journal.recordingPosition() < targetPosition
                && clock.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        archive.tryStopRecording(recordingSubscriptionId);
        journal.close();
        archive.close();
        aeron.close();
        driver.close();
    }
}
