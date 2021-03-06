/*
 *  ***** BEGIN LICENSE BLOCK ***** Version: MPL 1.1/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 * 
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in Java(TM), hosted at
 * https://github.com/gunterze/dcm4che.
 * 
 * The Initial Developer of the Original Code is Agfa Healthcare. Portions created by the Initial
 * Developer are Copyright (C) 2011 the Initial Developer. All Rights Reserved.
 * 
 * Contributor(s): See @authors listed below
 * 
 * Alternatively, the contents of this file may be used under the terms of either the GNU General
 * Public License Version 2 or later (the "GPL"), or the GNU Lesser General Public License Version
 * 2.1 or later (the "LGPL"), in which case the provisions of the GPL or the LGPL are applicable
 * instead of those above. If you wish to allow use of your version of this file only under the
 * terms of either the GPL or the LGPL, and not to allow others to use your version of this file
 * under the terms of the MPL, indicate your decision by deleting the provisions above and replace
 * them with the notice and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under the terms of any one of
 * the MPL, the GPL or the LGPL.
 * 
 * ***** END LICENSE BLOCK *****
 */

package org.dcm4chee.arr.cdi.cleanup.Impl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.dcm4che3.net.Device;
import org.dcm4chee.arr.cdi.AuditRecordRepositoryServiceReloaded;
import org.dcm4chee.arr.cdi.AuditRecordRepositoryServiceStartedCleanUp;
import org.dcm4chee.arr.cdi.AuditRecordRepositoryServiceStoppedCleanUp;
import org.dcm4chee.arr.cdi.cleanup.AuditRecordRepositoryCleanup;
import org.dcm4chee.arr.cdi.cleanup.CleanUpConfigurationExtension;
import org.dcm4chee.arr.cdi.cleanup.EventTypeFilterExtension;
import org.dcm4chee.arr.cdi.cleanup.EventTypeObject;
import org.dcm4chee.arr.cdi.Impl.ReloadEvent;
import org.dcm4chee.arr.cdi.Impl.StartCleanUpEvent;
import org.dcm4chee.arr.cdi.Impl.StopCleanUpEvent;
import org.dcm4chee.arr.cdi.cleanup.ejb.AuditRecordDeleteBean;

/**
 * The Class AuditRecordRepositoryCleanupImpl. implementation of a clean up
 * service initialization is provided by the singleton bean running the audit
 * record repository service
 * 
 * @author Hesham Elbadawi bsdreko@gmail.com
 */
@ApplicationScoped
public class AuditRecordRepositoryCleanupImpl implements
        AuditRecordRepositoryCleanup {

    @Inject
    private AuditRecordDeleteBean removeTool;

    private static final Logger log = Logger
            .getLogger(AuditRecordRepositoryCleanupImpl.class);

    private ScheduledFuture<?> scheduledCleanProcedure = null;

    private ScheduledFuture<?> scheduledBackUpProcedure = null;

    private boolean running;

    private boolean backup;
    private Device device = null;

    File backUpDir = null;

    private CleanUpConfigurationExtension cleanUpConfig = null;

    public CleanUpConfigurationExtension getCleanUpConfig() {
        return cleanUpConfig;
    }

    private EventTypeFilterExtension eventFilter = null;

    private Runnable cleanProcedure = new Runnable() {

        @Override
        public void run() {
            if (cleanUpConfig.getArrDefaultCleanUpPolicy().compareToIgnoreCase(
                    "all") == 0) {
                log.info("Default cleanup policy is used");
                if (cleanUpConfig.isArrCleanUpUsesRetention()) {
                    cleanWithDefaultRetentionPolicy();
                }
                if (cleanUpConfig.isArrCleanUpUsesMaxRecords()) {
                    cleanWithMaxRecordsPolicy();
                }
            } else if (cleanUpConfig.getArrDefaultCleanUpPolicy()
                    .compareToIgnoreCase("custom") == 0) {
                log.info("Custom cleanup policy is used");
                for (Entry<String, EventTypeObject> obj : eventFilter
                        .getEventTypeFilter().getEntries()) {
                    cleanWithCustomRetentionPolicy(obj.getValue().getCodeID(),
                            obj.getValue().getRetentionTime(), obj.getValue()
                                    .getRetentionTimeUnit(),
                            cleanUpConfig.getArrCleanUpDeletePerTransaction());
                    // log.info("Processed Entry from ldap from event filter: "
                    // + obj.getKey() + "\n with retention time = "
                    // + obj.getValue().getRetentionTime());
                }
            }

        }
    };
    private Runnable backUpProcedure = new Runnable() {

        @Override
        public void run() {
            if (cleanUpConfig.isArrSafeClean()) {
                log.info("Started back up procedure ...");
                LinkedList<File> files = new LinkedList<File>();
                for (File file : backUpDir.listFiles()) {
                    if (!file.getName().endsWith("tmp") && file.getName().startsWith("Audit") && !file.isDirectory()) {
                        files.add(file);
                        log.debug("added file " + file.getPath());
                    }
                }
                if (files.size() > 0) {

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new Date(System.currentTimeMillis()));
                    int year = cal.get(Calendar.YEAR);
                    int month = cal.get(Calendar.MONTH);
                    int day = cal.get(Calendar.DAY_OF_MONTH);
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    int minute = cal.get(Calendar.MINUTE);
                    String targetDirName = year + "-" + month + "-" + day + "-"
                            + hour + "-" + minute;
                    File targetDir = new File(backUpDir, targetDirName);
                    try {
                        if (!targetDir.exists()) {
                            targetDir.mkdir();
                        }
                    } catch (Exception e) {
                        log.error("Error creating backup directory ", e);
                    }
                    File targetFile = new File(targetDir, "AuditLogs-"
                            + targetDirName + ".zip");
                    ZipOutputStream zout = null;
                    try {
                        zout = new ZipOutputStream(new BufferedOutputStream(
                                new FileOutputStream(targetFile)));
                    } catch (FileNotFoundException e) {
                        log.error(
                                "Error creating backup zip - Probably a wrong backup directory specified ",
                                e);
                    }
                    try {
                        FileInputStream in = null;
                        byte[] buffer = new byte[1024];
                        int cnt;
                        for (int i = 0; i < files.size(); i++) {
                            log.debug("reading file for backup" + files.get(i).getPath());
                            in = new FileInputStream(files.get(i));
                            zout.putNextEntry(new ZipEntry(files.get(i)
                                    .getName()));
                            while ((cnt = in.read(buffer)) > 0) {
                                zout.write(buffer, 0, cnt);
                            }
                            zout.closeEntry();
                            in.close();
                            files.get(i).delete();
                        }

                    } catch (IOException e) {
                        log.error("Error writing backUp ", e);
                    } finally {
                        try {
                            zout.close();
                        } catch (IOException e) {
                            log.error("Unable to close Zip file - Cause ", e);
                        }
                    }
                }
            } else {
                log.info("Safe clean up is disabled unable to perform backup");
            }
        }
    };

    /**
     * Clean with max records policy. if max records policy is used, only this
     * number of records is left in the DB deletion is done older first
     */
    public void cleanWithMaxRecordsPolicy() {
        int maxRecordsAllowed = cleanUpConfig.getArrCleanUpMaxRecords();
        int deletePerTransaction = cleanUpConfig
                .getArrCleanUpDeletePerTransaction();
        List<Long> l = null;
        while ((l = removeTool.getPksByMaxRecords(maxRecordsAllowed,
                deletePerTransaction)) != null) {
            log.info("Deleting records with the following pks:");
            for (long pk : l) {
                removeTool.deleteRecord(cleanUpConfig, pk);
                log.info("pk = " + pk);

            }
        }

    }

    /**
     * Clean with custom retention policy. checks for each event id type code in
     * the configuration and according to retention time and unit deletes them
     * from the DB
     * 
     * @param code
     *            the code
     * @param retention
     *            the retention
     * @param retentionUnit
     *            the retention unit
     * @param deletePerTransaction
     *            the delete per transaction
     */
    public void cleanWithCustomRetentionPolicy(String code, long retention,
            String retentionUnit, int deletePerTransaction) {
        List<Long> l = null;
        while (!(l = removeTool.getPKsByEventIDTypeCode(code, (int) retention,
                retentionUnit, deletePerTransaction)).isEmpty()) {
            log.info("Deleting records with the following pks:");
            for (long pk : l) {
                removeTool.deleteRecord(cleanUpConfig, pk);
                log.info("pk = " + pk);
            }
        }

    }

    /**
     * Clean with default retention policy. deletes anything older than
     * retention used if the attribute arrDefaultCleanUpPolicy
     */
    public void cleanWithDefaultRetentionPolicy() {
        int retentionTime = cleanUpConfig.getArrCleanUpRetentionTime();
        String retentionTimeUnit = cleanUpConfig
                .getArrCleanUpRetentionTimeUnit();
        int deletePerTransaction = cleanUpConfig
                .getArrCleanUpDeletePerTransaction();
        List<Long> l = null;

        while (!(l = removeTool.getPKsByRetention(retentionTime,
                retentionTimeUnit, deletePerTransaction)).isEmpty()) {
            log.info("Deleting records with the following pks:");
            for (long pk : l) {
                removeTool.deleteRecord(cleanUpConfig, pk);
                log.info("pk = " + pk);
            }
        }
    }

    /**
     * Startup.
     * 
     * @param o
     *            the o
     */
    static void startup(@Observes @Initialized(ApplicationScoped.class) Object o) {
    }

    /**
     * Initialize procedure.
     */
    public void initializeProcedure() {
        if (this.cleanUpConfig.getArrCleanUpPollInterval() > 0) {
            log.info("Initializing cleanup thread");
            scheduledCleanProcedure = device.scheduleWithFixedDelay(
                    cleanProcedure, 0,
                    (long) this.cleanUpConfig.getArrCleanUpPollInterval(),
                    TimeUnit.SECONDS);
            if (this.cleanUpConfig.isArrSafeClean()) {
                backUpDir = new File(cleanUpConfig.getArrBackUpDir());
                if (!backUpDir.exists())
                    backUpDir.mkdir();
                scheduledBackUpProcedure = device.scheduleWithFixedDelay(
                        backUpProcedure, 0,
                        (long) this.cleanUpConfig.getArrBackUpPollInterval(),
                        TimeUnit.SECONDS);

                log.info("Initialized BackUp thread with poll interval of "
                        + (long) this.cleanUpConfig.getArrBackUpPollInterval()
                        + " SECONDS");
            }
            log.info("Initialized cleanup thread with poll interval of "
                    + (long) this.cleanUpConfig.getArrCleanUpPollInterval()
                    + " SECONDS");
        }
    }

    /**
     * Start clean up.
     * 
     * @param start
     *            the start
     */
    public void startCleanUp(
            @Observes @AuditRecordRepositoryServiceStartedCleanUp StartCleanUpEvent start) {
        this.device = start.getDevice();
        this.cleanUpConfig = device
                .getDeviceExtension(CleanUpConfigurationExtension.class);
        this.eventFilter = device
                .getDeviceExtension(EventTypeFilterExtension.class);
        if (scheduledCleanProcedure == null
                && (this.cleanUpConfig.isArrCleanUpUsesMaxRecords() || this.cleanUpConfig
                        .isArrCleanUpUsesRetention())) {
            initializeProcedure();
            running = true;
            log.info("Started cleanup service");
            if (this.cleanUpConfig.isArrSafeClean()) {
                backup = true;
                log.info("Started backup service");
            }
        }

    }

    /**
     * Stop clean up.
     * 
     * @param stop
     *            the stop
     */
    public void stopCleanUp(
            @Observes @AuditRecordRepositoryServiceStoppedCleanUp StopCleanUpEvent stop) {
        if (scheduledCleanProcedure != null) {
            scheduledCleanProcedure.cancel(false);
            scheduledCleanProcedure = null;
            running = false;
            log.info("Stopped cleanup service");
        }
        if (scheduledBackUpProcedure != null) {
            scheduledBackUpProcedure.cancel(false);
            scheduledBackUpProcedure = null;
            backup = false;
            log.info("Stopped backup service");
        }

    }

    /**
     * Reconfigure clean up.
     * 
     * @param reload
     *            the reload
     */
    public void reconfigureCleanUp(
            @Observes @AuditRecordRepositoryServiceReloaded ReloadEvent reload) {
        this.device = reload.getDevice();
        this.cleanUpConfig = device
                .getDeviceExtension(CleanUpConfigurationExtension.class);
        this.eventFilter = device
                .getDeviceExtension(EventTypeFilterExtension.class);
        if (reload.isState()) {
            log.info("Reloaded cleanup service configuration");
            if (running)
                scheduledCleanProcedure.cancel(false);
            if (backup)
                scheduledBackUpProcedure.cancel(false);
            initializeProcedure();
        }
    }

}
