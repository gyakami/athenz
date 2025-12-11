package com.yahoo.athenz.zms;

import org.testng.annotations.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ZMSImplStartupTest {

    // Subclass ZMSImpl to isolate loadSolutionTemplates testing
    static class TestZMSImpl extends ZMSImpl {
        @Override void loadSystemProperties() {}
        @Override void loadJsonMapper() { super.loadJsonMapper(); }
        @Override void loadConfigurationSettings() {}
        @Override void loadSchemaValidator() {}
        @Override void loadAuditLogger() {}
        @Override void loadAuditRefValidator() {}
        @Override void loadAuthorities() {}
        @Override void loadPrivateKeyStore() {}
        @Override void loadDomainMetaStore() {}
        @Override void loadMetricObject() {}
        // loadSolutionTemplates is NOT overridden, so it runs the real logic
        @Override void loadAuthHistoryStore() {}
        @Override void loadObjectStore() {}
        @Override void initObjectStore() {}
        @Override void loadAuthorizedServices() {}
        @Override void loadServerPublicKeys() {}
        @Override void setAuthorityKeyStore() {}
        // setNotificationManager is private, cannot override, but shouldn't be reached
        @Override void autoApplyTemplates() {}
        @Override void loadStatusChecker() {}
        @Override void loadDomainChangePublisher() {}
        @Override void loadResourceValidator() {}
        // initializePrincipalStateUpdater is private
        // initializeServiceProviderManager is private
    }

    @Test
    public void testLoadSolutionTemplatesWithInvalidRole() throws IOException {
        // Create a temporary solution templates file with an invalid role (both trust and members)
        String invalidTemplateJson = "{\n" +
                "  \"templates\": {\n" +
                "    \"invalidTemplate\": {\n" +
                "      \"roles\": [\n" +
                "        {\n" +
                "          \"name\": \"role1\",\n" +
                "          \"trust\": \"some.domain\",\n" +
                "          \"roleMembers\": [\n" +
                "            {\n" +
                "              \"memberName\": \"user.test\"\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        Path tempFile = Files.createTempFile("solution_templates", ".json");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write(invalidTemplateJson);
        }

        // Set the system property to point to the temporary file
        System.setProperty(ZMSConsts.ZMS_PROP_SOLUTION_TEMPLATE_FNAME, tempFile.toAbsolutePath().toString());

        try {
            // Attempt to initialize TestZMSImpl
            // This calls loadSolutionTemplates() which should throw RuntimeException
            // before hitting any private methods that might fail due to uninitialized state.
            new TestZMSImpl();
            fail("Should have thrown RuntimeException due to invalid solution template");
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("has both trust and members defined")) {
                // Expected exception
                return;
            }
            throw ex; // Rethrow if it's not the expected one
        } catch (Exception ex) {
             throw new RuntimeException("Unexpected exception", ex);
        } finally {
            System.clearProperty(ZMSConsts.ZMS_PROP_SOLUTION_TEMPLATE_FNAME);
            Files.deleteIfExists(tempFile);
        }
    }
}
