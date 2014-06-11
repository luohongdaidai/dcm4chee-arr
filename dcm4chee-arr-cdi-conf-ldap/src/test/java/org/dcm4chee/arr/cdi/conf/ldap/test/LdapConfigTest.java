package org.dcm4chee.arr.cdi.conf.ldap.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.log4j.Logger;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.ldap.LdapDicomConfiguration;
import org.dcm4che3.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.ldap.generic.LdapGenericConfigExtension;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arr.cdi.cleanup.CleanUpConfigurationExtension;
import org.dcm4chee.arr.cdi.cleanup.EventTypeFilterExtension;
import org.dcm4chee.arr.cdi.cleanup.EventTypeObject;
import org.dcm4chee.arr.cdi.conf.ldap.LdapArrEventFilterConfiguration;
import org.junit.*;

public class LdapConfigTest {
    private LdapDicomConfiguration AuditRecordRepositoryDicomConfiguration;
    private LdapAuditRecordRepositoryConfiguration AuditRecordRepositoryDicomConfigurationExtension;
    private static final Logger log = Logger.getLogger(LdapConfigTest.class);
    private static final String DEVICE_NAME_PROPERTY = "org.dcm4chee.arr.deviceName";
    private static final String DEF_DEVICE_NAME = "dcm4chee-arr";
    private ExecutorService executor = Executors.newCachedThreadPool();
    private ScheduledExecutorService scheduledExecutor = Executors
	    .newSingleThreadScheduledExecutor();
    private DicomConfiguration conf;
    private Device device;
    private CleanUpConfigurationExtension cleanup;
    private EventTypeFilterExtension eventFilter;

    private Device findDevice() throws ConfigurationException {
	return conf.findDevice(System.getProperty(DEVICE_NAME_PROPERTY,
		DEF_DEVICE_NAME));
    }

    @Test
    public void TestConfExists() throws ConfigurationException, IOException {

	assertTrue("Configuration Exists Test Passed Successfully",
		conf.configurationExists());
    }

    @Test
    public void TestGetLdapConfig() throws ConfigurationException, IOException {
	// test cleanup config
	assertNotNull("Found cleanup confguration for poll interval",
		cleanup.getArrCleanUpPollInterval());
	assertNotNull("Found cleanup confguration for max records",
		cleanup.getArrCleanUpMaxRecords());
	assertNotNull("Found cleanup confguration for default retention time",
		cleanup.getArrCleanUpRetentionTime());
	assertNotNull(
		"Found cleanup confguration for default retention time unit",
		cleanup.getArrCleanUpRetentionTime());
	assertNotNull("Found cleanup confguration for Default Cleanup policy",
		cleanup.getArrDefaultCleanUpPolicy());
	assertNotNull("Found cleanup confguration for delete per transaction",
		cleanup.getArrCleanUpDeletePerTransaction());
	assertNotNull("Found cleanup confguration for uses max per records",
		cleanup.isArrCleanUpUsesMaxRecords());
	assertNotNull("Found cleanup confguration for uses retention",
		cleanup.isArrCleanUpUsesRetention());
	// test eventfilter config
	for (Entry<String, EventTypeObject> entry : eventFilter
		.getEventTypeFilter().getEntries()) {
	    log.info("loaded eventidtype = " + entry.getKey());
	    assertNotNull("Found eventFilter confguration for poll interval",
		    entry.getValue().getCodeID());
	    assertNotNull("Found eventFilter confguration for retention time",
		    entry.getValue().getRetentionTime());
	    assertNotNull(
		    "Found eventFilter confguration for retention time unit",
		    entry.getValue().getRetentionTimeUnit());
	}
    }

    @Before
    public void setup() throws ConfigurationException, IOException {

	AuditRecordRepositoryDicomConfiguration = new LdapDicomConfiguration(
		ldapEnv());
	AuditRecordRepositoryDicomConfigurationExtension = new LdapAuditRecordRepositoryConfiguration();
	AuditRecordRepositoryDicomConfiguration
		.addDicomConfigurationExtension(AuditRecordRepositoryDicomConfigurationExtension);
	LdapGenericConfigExtension<CleanUpConfigurationExtension> ext = new LdapGenericConfigExtension<CleanUpConfigurationExtension>(
		CleanUpConfigurationExtension.class);
	LdapArrEventFilterConfiguration arrEvtFilterExt = new LdapArrEventFilterConfiguration();
	AuditRecordRepositoryDicomConfiguration
		.addDicomConfigurationExtension(ext);
	AuditRecordRepositoryDicomConfiguration
		.addDicomConfigurationExtension(arrEvtFilterExt);
	conf = AuditRecordRepositoryDicomConfiguration;
	device = findDevice();
	device.setExecutor(executor);
	device.setScheduledExecutor(scheduledExecutor);
	cleanup = device
		.getDeviceExtension(CleanUpConfigurationExtension.class);
	eventFilter = device.getDeviceExtension(EventTypeFilterExtension.class);
    }

    private Properties ldapEnv() throws ConfigurationException, IOException {
	String url = "src/test/resources/ldap.properties";

	Properties p = new Properties();
	InputStream in = null;
	try {
	    in = StreamUtils.openFileOrURL(url);

	    p.load(in);
	    Assert.assertNotNull((p
		    .getProperty("java.naming.security.credentials")));
	} catch (IOException e) {
	    throw new ConfigurationException(e);
	} finally {
	    in.close();
	}

	return p;
    }

}
