package de.deepamehta.datomic;

import de.deepamehta.core.impl.ModelFactoryImpl;
import de.deepamehta.core.impl.PersistenceLayer;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.model.RelatedAssociationModel;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.ModelFactory;
import de.deepamehta.core.storage.spi.DeepaMehtaStorage;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

import datomic.Attribute;
import datomic.Connection;
import datomic.Entity;
import datomic.Peer;
import static datomic.Util.list;
import static datomic.Util.read;

import clojure.lang.Keyword;
import clojure.lang.Symbol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
// import static org.junit.Assert.assertNotEquals;  // Only available in JUnit 4.11. We have 4.10.
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// import java.util.concurrent.Future;
import java.util.logging.Logger;



public class DatomicTest {

    private DatomicStorage storage;
    private ModelFactoryImpl mf;

    private final Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Before
    public void setup() {
        String databaseUri = "datomic:mem://dm4-test-" + UUID.randomUUID();
        mf = new ModelFactoryImpl();
        storage = new DatomicStorage(databaseUri, mf);
        storage.installSchema();
        new PersistenceLayer(storage);  // Note: the ModelFactory doesn't work when no PersistenceLayer is created
    }

    @After
    public void shutdown() {
        // Peer.shutdown() must NOT be called between running tests (in the same JVM instance).
        // Otherwise strange Datomic errors appear.
        /* if (storage != null) {
            storage.shutdown();
        } */
    }

    // --- Datomic API ---

    @Test
    public void deserialize() {
        assertSame(Symbol.class,  read("e").getClass());
        assertSame(Symbol.class,  read("?e").getClass());
        assertSame(Keyword.class, read(":e").getClass());
    }

    @Test
    public void keyword() {
        assertFalse(":e".equals(read(":e")));
        assertFalse(read(":e").equals(":e"));
        assertEquals(":e", read(":e").toString());
    }

    @Test
    public void entity() {
        Entity entity = storage.entity(1234);
        assertNotNull(entity);
        assertEquals(0, entity.keySet().size());
    }

    @Test
    public void attribute() {
        // retrieve attribute
        Attribute entityType = storage.attribute(":dm4/entity-type");
        assertSame(Attribute.TYPE_REF, entityType.valueType());
        //
        // retrieve unknown attribute
        Attribute attr = storage.attribute(":dm4.unknown_attr");
        assertNull(attr);
    }

    @Test
    public void query() {
        Collection result = storage.query(list(read("?e")),
            list(list(read("?e"), ":dm4/entity-type", ":dm4.entity-type/topic")));
        assertEquals(0, result.size());
    }

    @Test
    public void queryString() {
        Collection result = storage.query("[:find ?e :where [?e :dm4/entity-type :dm4.entity-type/topic]]");
        assertEquals(0, result.size());
    }

    @Test
    public void storeEntity() {
        storage.storeEntity(":dm4/entity-type", ":dm4.entity-type/topic");
        //
        Collection result = storage.query(list(read("?e")),
            list(list(read("?e"), ":dm4/entity-type", ":dm4.entity-type/topic")));
        assertEquals(1, result.size());
    }

    @Test
    public void storeEntityAndResolveTempId() {
        long id = storage.resolveTempId(storage.storeEntity(
            ":db/id", DatomicStorage.TEMP_ID,
            ":dm4.object/type", "dm4.test.type_uri"
        ));
        assertTrue(id > 0);
    }

    @Test
    public void storeEmptyEntity() throws Exception {
        Map txInfo = storage.storeEntity(":db/id", DatomicStorage.TEMP_ID).get();
        Map tempIds = (Map) txInfo.get(Connection.TEMPIDS);
        // Note: no datom was created
        assertTrue(tempIds.isEmpty());
    }

    @Test
    public void storeEntityAndQueryAsString() {
        storage.storeEntity(":dm4/entity-type", ":dm4.entity-type/topic");
        //
        Collection result = storage.query("[:find ?e :where [?e :dm4/entity-type :dm4.entity-type/topic]]");
        assertEquals(1, result.size());
    }

    @Test
    public void queryWithParameter() {
        storage.storeEntity(":dm4.object/uri", "dm4.test.uri");
        Collection result = storage.query("[:find ?e :in $ ?uri :where [?e :dm4.object/uri ?uri]]", "dm4.test.uri");
        assertEquals(1, result.size());
    }

    @Test
    public void typeMismatch() {
        try {
            storage.resolveTempId(storage.storeEntity(
                ":db/id", DatomicStorage.TEMP_ID,
                ":dm4.object/uri", 1234,    // type mismatch!
                ":dm4.object/type", "dm4.test.type_uri"));
            fail("IllegalArgumentException not thrown");
            // Note: exception is thrown only by resolveTempId(), not by storeEntity()
        } catch (Exception e) {
            Throwable cause = e.getCause().getCause();
            assertTrue(cause instanceof IllegalArgumentException);
            assertEquals(cause.getMessage(), ":db.error/wrong-type-for-attribute Value 1234 " +
                "is not a valid :string for attribute :dm4.object/uri");
        }
    }

    @Test
    public void typeMismatch2() {
        storage.storeEntity(
            ":db/id", DatomicStorage.TEMP_ID,
            ":dm4.object/uri", 1234,    // type mismatch!
            ":dm4.object/type", "dm4.test.type_uri"
        );
        Collection result = storage.query("[:find ?e :in $ ?v :where [?e :dm4.object/uri ?v]]", 1234);
        assertEquals(0, result.size());
        // Note: no error occurs; just the result is empty
    }

    @Test
    public void unknownAttr() {
        try {
            storage.storeEntity(":dm4.unknown_attr", "hello");
            storage.query("[:find ?v :in $ ?e ?a :where [?e ?a ?v]]", 1234, ":dm4.unknown_attr");
            fail("IllegalArgumentException not thrown");
            // Note: exception is thrown only by query(), not by storeEntity()
        } catch (Exception e) {
            Throwable cause = e.getCause().getCause();
            assertTrue(cause instanceof IllegalArgumentException);
            assertEquals(cause.getMessage(), ":db.error/not-an-entity Unable to resolve entity: :dm4.unknown_attr");
        }
    }

    @Test
    public void createAttr() {
        final String IDENT = ":dm4.time.created";
        // Note: an attribute ident must begin with ":" when stored.
        // Otherwise the attribute can't be retrieved (null).
        Attribute attr = storage.attribute(IDENT);
        assertNull(attr);
        //
        storage.storeEntity(
            ":db/ident",       IDENT,
            ":db/valueType",   ":db.type/long",
            ":db/cardinality", ":db.cardinality/one");
        //
        attr = storage.attribute(IDENT);
        assertNotNull(attr);
        assertSame(read(IDENT), attr.ident());  // ident() returns a clojure.lang.Keyword
        //
        // Note: retrieval works also without ":" !!
        attr = storage.attribute("dm4.time.created");
        assertNotNull(attr);
        assertEquals(read(IDENT), attr.ident());
    }

    // --- DeepaMehtaStorage ---

    @Test
    public void storeTopic() {
        TopicModel topic = mf.newTopicModel("dm4.test.type_uri");
        assertEquals(-1, topic.getId());
        storage.storeTopic(topic);
        assertTrue(topic.getId() != -1);
    }

    @Test
    public void uriUniqueness() {
        try {
            TopicModel topic = mf.newTopicModel("dm4.test.uri", "dm4.test.type_uri");
            storage.storeTopic(topic);
            storage.storeTopic(topic);
            fail("\"URI not unique\" exception not thrown");
        } catch (Exception e) {
            assertEquals("URI \"dm4.test.uri\" is not unique", e.getMessage());
        }
    }

    @Test
    public void storeTopicProperty() {
        TopicModel t = mf.newTopicModel("dm4.test.type_uri");
        storage.storeTopic(t);
        long id = t.getId();
        //
        final String PROP_URI = "dm4.accesscontrol.creator";
        storage.storeTopicProperty(id, PROP_URI, "admin", false);
        //
        String creator = (String) storage.fetchProperty(id, PROP_URI);
        assertEquals("admin", creator);
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

}
