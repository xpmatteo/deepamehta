package de.deepamehta.core.impl;

import de.deepamehta.core.util.JavaUtils;

import de.deepamehta.core.storage.spi.DeepaMehtaStorage;
import de.deepamehta.core.storage.spi.MehtaGraphFactory;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.logging.Logger;



public class CoreServiceTestEnvironment {

    // ------------------------------------------------------------------------------------------------------- Constants

    // ### TODO: drop this. Register as OSGi service instead.
    private static final String DATABASE_FACTORY = "de.deepamehta.storage.neo4j.Neo4jMehtaGraphFactory";
    // ### TODO: enable property access
    // System.getProperty("dm4.database.factory");

    // ---------------------------------------------------------------------------------------------- Instance Variables

    protected EmbeddedService dms;
    private File dbPath;

    protected Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Before
    public void setUp() {
        try {
            logger.info("Setting up test DB");
            dbPath = JavaUtils.createTempDirectory("dm4-");
            DeepaMehtaStorage mehtaGraph = openDB(dbPath.getAbsolutePath());
            dms = new EmbeddedService(new StorageDecorator(mehtaGraph), null);
            dms.setupDB();
        } catch (Exception e) {
            throw new RuntimeException("Setting up test DB failed", e);
        }
    }

    @After
    public void tearDown() {
        dms.shutdown();
        dbPath.delete();
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    // ### TODO: copied from CoreActivator
    private DeepaMehtaStorage openDB(String databasePath) {
        try {
            // ### TODO: wording
            logger.info("Instantiating the MehtaGraph storage engine\n    databasePath=\"" + databasePath +
                "\"\n    databaseFactory=\"" + DATABASE_FACTORY + "\"");
            MehtaGraphFactory factory = (MehtaGraphFactory) Class.forName(DATABASE_FACTORY).newInstance();
            return factory.createInstance(databasePath);
        } catch (Exception e) {
            throw new RuntimeException("Instantiating the MehtaGraph storage engine failed", e);
        }
    }
}
