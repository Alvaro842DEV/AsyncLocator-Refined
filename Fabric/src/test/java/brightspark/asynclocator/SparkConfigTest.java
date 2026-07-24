package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brightspark.asynclocator.SparkConfig.Category;
import brightspark.asynclocator.SparkConfig.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SparkConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void addingMissingOptionsPreservesExistingValues() throws Exception {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, """
                existingValue = 73

                ##########
                #> Flags <#
                ##########

                existingFlag = false
                """);

        TestConfig.reset();
        boolean missingEntries = SparkConfig.read(configFile, TestConfig.class);

        assertTrue(missingEntries);
        assertEquals(73, TestConfig.EXISTING_VALUE);
        assertEquals(29, TestConfig.NEW_VALUE);
        assertEquals(false, TestConfig.Flags.EXISTING_FLAG);
        assertEquals(true, TestConfig.Flags.NEW_FLAG);

        SparkConfig.write(configFile, TestConfig.class);

        TestConfig.EXISTING_VALUE = 0;
        TestConfig.NEW_VALUE = 0;
        TestConfig.Flags.EXISTING_FLAG = true;
        TestConfig.Flags.NEW_FLAG = false;
        SparkConfig.read(configFile, TestConfig.class);

        assertEquals(73, TestConfig.EXISTING_VALUE);
        assertEquals(29, TestConfig.NEW_VALUE);
        assertEquals(false, TestConfig.Flags.EXISTING_FLAG);
        assertEquals(true, TestConfig.Flags.NEW_FLAG);
    }

    private static final class TestConfig {
        @Config("existingValue")
        public static int EXISTING_VALUE = 11;

        @Config("newValue")
        public static int NEW_VALUE = 29;

        @Category("Flags")
        public static final class Flags {
            @Config("existingFlag")
            public static boolean EXISTING_FLAG = true;

            @Config("newFlag")
            public static boolean NEW_FLAG = true;
        }

        private static void reset() {
            EXISTING_VALUE = 11;
            NEW_VALUE = 29;
            Flags.EXISTING_FLAG = true;
            Flags.NEW_FLAG = true;
        }
    }
}
