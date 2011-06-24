package de.deepamehta.core.impl.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationType;
import de.deepamehta.core.DeepaMehtaTransaction;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.AssociationTypeModel;
import de.deepamehta.core.model.ClientContext;
import de.deepamehta.core.model.CommandParams;
import de.deepamehta.core.model.CommandResult;
import de.deepamehta.core.model.Composite;
import de.deepamehta.core.model.PluginInfo;
import de.deepamehta.core.model.RelatedAssociationModel;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.model.TopicTypeModel;
import de.deepamehta.core.model.TopicValue;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.service.Directive;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.Plugin;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.storage.DeepaMehtaStorage;
import de.deepamehta.core.util.JSONHelper;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.DELETE;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;



/**
 * Implementation of the DeepaMehta core service. Embeddable into Java applications.
 */
@Path("/")
@Consumes("application/json")
@Produces("application/json")
public class EmbeddedService implements DeepaMehtaService {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String CORE_MIGRATIONS_PACKAGE = "de.deepamehta.core.migrations";
    private static final int REQUIRED_CORE_MIGRATION = 2;

    // ---------------------------------------------------------------------------------------------- Instance Variables

            DeepaMehtaStorage storage;
            TypeCache typeCache;

    private PluginCache pluginCache;
    private BundleContext bundleContext;

    private enum Hook {

        // Note: this hook is triggered only by the plugin itself
        // (see {@link de.deepamehta.core.service.Plugin#initPlugin}).
        // It is declared here for documentation purpose only.
        POST_INSTALL_PLUGIN("postInstallPluginHook"),
        ALL_PLUGINS_READY("allPluginsReadyHook"),

        // Note: this hook is triggered only by the plugin itself
        // (see {@link de.deepamehta.core.service.Plugin#createServiceTracker}).
        // It is declared here for documentation purpose only.
        SERVICE_ARRIVED("serviceArrived", PluginService.class),
        // Note: this hook is triggered only by the plugin itself
        // (see {@link de.deepamehta.core.service.Plugin#createServiceTracker}).
        // It is declared here for documentation purpose only.
        SERVICE_GONE("serviceGone", PluginService.class),

         PRE_CREATE_TOPIC("preCreateHook",  TopicModel.class, ClientContext.class),
        POST_CREATE_TOPIC("postCreateHook", Topic.class, ClientContext.class),
        // ### PRE_UPDATE_TOPIC("preUpdateHook",  Topic.class, Properties.class),
        // ### POST_UPDATE_TOPIC("postUpdateHook", Topic.class, Properties.class),

        POST_RETYPE_ASSOCIATION("postRetypeAssociationHook", Association.class, String.class, Directives.class),

         PRE_DELETE_ASSOCIATION("preDeleteAssociationHook",  Association.class, Directives.class),
        POST_DELETE_ASSOCIATION("postDeleteAssociationHook", Association.class, Directives.class),

        PROVIDE_TOPIC_PROPERTIES("providePropertiesHook", Topic.class),
        PROVIDE_RELATION_PROPERTIES("providePropertiesHook", Association.class),

        ENRICH_TOPIC("enrichTopicHook", Topic.class, ClientContext.class),
        ENRICH_TOPIC_TYPE("enrichTopicTypeHook", TopicType.class, ClientContext.class),

        // Note: besides regular triggering (see {@link #createTopicType})
        // this hook is triggered by the plugin itself
        // (see {@link de.deepamehta.core.service.Plugin#introduceTypesToPlugin}).
        MODIFY_TOPIC_TYPE("modifyTopicTypeHook", TopicType.class, ClientContext.class),

        EXECUTE_COMMAND("executeCommandHook", String.class, CommandParams.class, ClientContext.class);

        private final String methodName;
        private final Class[] paramClasses;

        private Hook(String methodName, Class... paramClasses) {
            this.methodName = methodName;
            this.paramClasses = paramClasses;
        }
    }

    private enum MigrationRunMode {
        CLEAN_INSTALL, UPDATE, ALWAYS
    }

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    public EmbeddedService(DeepaMehtaStorage storage, BundleContext bundleContext) {
        this.storage = storage;
        this.bundleContext = bundleContext;
        this.pluginCache = new PluginCache();
        this.typeCache = new TypeCache(this);
        bootstrapTypeCache();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** DeepaMehtaService Implementation ***
    // ****************************************



    // === Topics ===

    @GET
    @Path("/topic/{id}")
    @Override
    public AttachedTopic getTopic(@PathParam("id") long topicId,
                                  @QueryParam("fetch_composite") @DefaultValue("true") boolean fetchComposite,
                                  @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedTopic topic = attach(storage.getTopic(topicId), fetchComposite);
            triggerHook(Hook.ENRICH_TOPIC, topic, clientContext);
            tx.success();
            return topic;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving topic " + topicId + " failed", e);
        } finally {
            tx.finish();
        }
    }

    @GET
    @Path("/topic/by_value/{key}/{value}")
    @Override
    public AttachedTopic getTopic(@PathParam("key") String key, @PathParam("value") TopicValue value,
                                  @QueryParam("fetch_composite") @DefaultValue("true") boolean fetchComposite) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            TopicModel topic = storage.getTopic(key, value);
            AttachedTopic attachedTopic = topic != null ? attach(topic, fetchComposite) : null;
            tx.success();
            return attachedTopic;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving topic failed (key=\"" + key + "\", value=" + value + ")", e);
        } finally {
            tx.finish();
        }
    }

    @GET
    @Path("/topic/by_type/{type_uri}")
    @Override
    public Set<RelatedTopic> getTopics(@PathParam("type_uri") String typeUri) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            Set<RelatedTopic> topics = getTopicType(typeUri, null).getRelatedTopics("dm3.core.instantiation",
                "dm3.core.type", "dm3.core.instance", null, false);   // othersTopicTypeUri=null, fetchComposite=false
            /*
            for (Topic topic : topics) {
                triggerHook(Hook.PROVIDE_TOPIC_PROPERTIES, topic);
            }
            */
            tx.success();
            return topics;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving topics by type failed (typeUri=\"" + typeUri + "\")", e);
        } finally {
            tx.finish();
        }
    }

    @GET
    @Path("/topic")
    @Override
    public Set<Topic> searchTopics(@QueryParam("search")    String searchTerm,
                                   @QueryParam("field")     String fieldUri,
                                   @QueryParam("wholeword") boolean wholeWord,
                                   @HeaderParam("Cookie")   ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            Set<Topic> topics = attach(storage.searchTopics(searchTerm, fieldUri, wholeWord), false);
            tx.success();
            return topics;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Searching topics failed (searchTerm=\"" + searchTerm + "\", fieldUri=\"" +
                fieldUri + "\", wholeWord=" + wholeWord + ", clientContext=" + clientContext + ")", e);
        } finally {
            tx.finish();
        }
    }

    @POST
    @Path("/topic")
    @Override
    public AttachedTopic createTopic(TopicModel model, @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            triggerHook(Hook.PRE_CREATE_TOPIC, model, clientContext);
            //
            storage.createTopic(model);
            associateWithTopicType(model);
            AttachedTopic topic = attach(model, false);
            //
            topic.update(model);
            //
            triggerHook(Hook.POST_CREATE_TOPIC, topic, clientContext);
            triggerHook(Hook.ENRICH_TOPIC, topic, clientContext);
            //
            tx.success();
            return topic;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Creating topic failed (" + model + ")", e);
        } finally {
            tx.finish();
        }
    }

    @PUT
    @Path("/topic")
    @Override
    public Topic updateTopic(TopicModel model, @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedTopic topic = getTopic(model.getId(), true, clientContext);   // fetchComposite=true ### false?
            //
            // Properties oldProperties = new Properties(topic.getProperties());   // copy old properties for comparison
            // ### triggerHook(Hook.PRE_UPDATE_TOPIC, topic, properties);
            //
            topic.update(model);
            // ### FIXME: avoid refetching. Required is updating the topic model for aggregations:
            // replacing $id composite entries with actual values. See AttachedTopic.storeComposite()
            topic = getTopic(model.getId(), true, clientContext);  // fetchComposite=true
            //
            // ### triggerHook(Hook.POST_UPDATE_TOPIC, topic, oldProperties);
            //
            tx.success();
            return topic;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Updating topic failed (" + model + ")", e);
        } finally {
            tx.finish();
        }
    }

    @DELETE
    @Path("/topic/{id}")
    @Override
    public void deleteTopic(@PathParam("id") long topicId, @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        Topic topic = null;
        try {
            topic = getTopic(topicId, true, clientContext);   // fetchComposite=true ### false?
            topic.delete();
            tx.success();
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Deleting topic " + topicId + " failed (" + topic + ")", e);
        } finally {
            tx.finish();
        }
    }



    // === Associations ===

    @GET
    @Path("/association/{id}")
    @Override
    public AttachedAssociation getAssociation(@PathParam("id") long assocId) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedAssociation assoc = attach(storage.getAssociation(assocId));
            tx.success();
            return assoc;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving association " + assocId + " failed", e);
        } finally {
            tx.finish();
        }
    }

    @GET
    @Path("/association/multiple/{topic1_id}/{topic2_id}")
    @Override
    public Set<Association> getAssociations(@PathParam("topic1_id") long topic1Id,
                                            @PathParam("topic2_id") long topic2Id) {
        return getAssociations(topic1Id, topic2Id, null);
    }

    @GET
    @Path("/association/multiple/{topic1_id}/{topic2_id}/{assoc_type_uri}")
    @Override
    public Set<Association> getAssociations(@PathParam("topic1_id") long topic1Id,
                                            @PathParam("topic2_id") long topic2Id,
                                            @PathParam("assoc_type_uri") String assocTypeUri) {
        logger.info("topic1Id=" + topic1Id + ", topic2Id=" + topic2Id + ", assocTypeUri=\"" + assocTypeUri + "\"");
        DeepaMehtaTransaction tx = beginTx();
        try {
            Set<Association> assocs = attach(storage.getAssociations(topic1Id, topic2Id, assocTypeUri));
            tx.success();
            return assocs;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving associations between topics " + topic1Id +
                " and " + topic2Id + " failed (assocTypeUri=\"" + assocTypeUri + "\")", e);
        } finally {
            tx.finish();
        }
    }

    // ---

    @POST
    @Path("/association")
    @Override
    public Association createAssociation(AssociationModel model,
                                         @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            storage.createAssociation(model);
            associateWithAssociationType(model);
            Association assoc = attach(model);
            //
            tx.success();
            return assoc;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Creating association failed (" + model + ")", e);
        } finally {
            tx.finish();
        }
    }

    @PUT
    @Path("/association")
    @Override
    public Directives updateAssociation(AssociationModel model,
                                        @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedAssociation assoc = getAssociation(model.getId());
            //
            // Properties oldProperties = new Properties(topic.getProperties());   // copy old properties for comparison
            // ### triggerHook(Hook.PRE_UPDATE_TOPIC, topic, properties);
            //
            AssociationChangeReport report = assoc.update(model);
            //
            Directives directives = new Directives();
            directives.add(Directive.UPDATE_ASSOCIATION, assoc);
            //
            if (report.typeUriChanged) {
                triggerHook(Hook.POST_RETYPE_ASSOCIATION, assoc, report.oldTypeUri, directives);
            }
            //
            tx.success();
            return directives;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Updating association failed (" + model + ")", e);
        } finally {
            tx.finish();
        }
    }

    @DELETE
    @Path("/association/{id}")
    @Override
    public Directives deleteAssociation(@PathParam("id") long assocId,
                                        @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        Association assoc = null;
        try {
            assoc = getAssociation(assocId);
            //
            Directives directives = new Directives();
            directives.add(Directive.DELETE_ASSOCIATION, assoc);
            //
            triggerHook(Hook.PRE_DELETE_ASSOCIATION, assoc, directives);
            assoc.delete();
            triggerHook(Hook.POST_DELETE_ASSOCIATION, assoc, directives);
            //
            tx.success();
            return directives;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Deleting association " + assocId + " failed (" + assoc + ")", e);
        } finally {
            tx.finish();
        }
    }



    // === Topic Types ===

    @GET
    @Path("/topictype")
    @Override
    public Set<String> getTopicTypeUris() {
        Topic metaType = attach(storage.getTopic("uri", new TopicValue("dm3.core.topic_type")), false);
        Set<RelatedTopic> topicTypes = metaType.getRelatedTopics("dm3.core.instantiation", "dm3.core.type",
                                                                 "dm3.core.instance", "dm3.core.topic_type", false);
        Set<String> topicTypeUris = new HashSet();
        // add meta types
        topicTypeUris.add("dm3.core.topic_type");
        topicTypeUris.add("dm3.core.assoc_type");
        topicTypeUris.add("dm3.core.meta_type");
        topicTypeUris.add("dm3.core.meta_meta_type");
        // add regular types
        for (Topic topicType : topicTypes) {
            topicTypeUris.add(topicType.getUri());
        }
        return topicTypeUris;
    }

    @GET
    @Path("/topictype/{uri}")
    @Override
    public AttachedTopicType getTopicType(@PathParam("uri") String uri,
                                          @HeaderParam("Cookie") ClientContext clientContext) {
        if (uri == null) {
            throw new IllegalArgumentException("Tried to get a topic type with null URI");
        }
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedTopicType topicType = typeCache.getTopicType(uri);
            triggerHook(Hook.ENRICH_TOPIC_TYPE, topicType, clientContext);
            tx.success();
            return topicType;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving topic type \"" + uri + "\" failed", e);
        } finally {
            tx.finish();
        }
    }

    @POST
    @Path("/topictype")
    @Override
    public TopicType createTopicType(TopicTypeModel topicTypeModel,
                                     @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedTopicType topicType = new AttachedTopicType(topicTypeModel, this);
            topicType.store();
            typeCache.put(topicType);
            //
            // Note: the modification must be applied *before* the enrichment.
            // Consider the Access Control plugin: the creator must be set *before* the permissions can be determined.
            triggerHook(Hook.MODIFY_TOPIC_TYPE, topicType, clientContext);
            triggerHook(Hook.ENRICH_TOPIC_TYPE, topicType, clientContext);
            //
            tx.success();
            return topicType;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Creating topic type \"" + topicTypeModel.getUri() +
                "\" failed (" + topicTypeModel + ")", e);
        } finally {
            tx.finish();
        }
    }

    @PUT
    @Path("/topictype")
    @Override
    public TopicType updateTopicType(TopicTypeModel topicTypeModel,
                                     @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            // Note: type lookup is by ID. The URI might have changed, the ID does not.
            String topicTypeUri = getTopic(topicTypeModel.getId(), false, clientContext).getUri();  // fetchComp..=false
            AttachedTopicType topicType = getTopicType(topicTypeUri, clientContext);
            //
            // Properties oldProperties = new Properties(topic.getProperties());   // copy old properties for comparison
            // ### triggerHook(Hook.PRE_UPDATE_TOPIC, topic, properties);
            //
            topicType.update(topicTypeModel);
            //
            // ### triggerHook(Hook.POST_UPDATE_TOPIC, topic, oldProperties);
            //
            tx.success();
            return topicType;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Updating topic type failed (" + topicTypeModel + ")", e);
        } finally {
            tx.finish();
        }
    }



    // === Association Types ===

    @GET
    @Path("/assoctype")
    @Override
    public Set<String> getAssociationTypeUris() {
        Topic metaType = attach(storage.getTopic("uri", new TopicValue("dm3.core.assoc_type")), false);
        Set<RelatedTopic> assocTypes = metaType.getRelatedTopics("dm3.core.instantiation", "dm3.core.type",
                                                                 "dm3.core.instance", "dm3.core.assoc_type", false);
        Set<String> assocTypeUris = new HashSet();
        for (Topic assocType : assocTypes) {
            assocTypeUris.add(assocType.getUri());
        }
        return assocTypeUris;
    }

    @GET
    @Path("/assoctype/{uri}")
    @Override
    public AttachedAssociationType getAssociationType(@PathParam("uri") String uri,
                                                      @HeaderParam("Cookie") ClientContext clientContext) {
        if (uri == null) {
            throw new IllegalArgumentException("Tried to get an association type with null URI");
        }
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedAssociationType assocType = typeCache.getAssociationType(uri);
            // ### triggerHook(Hook.ENRICH_TOPIC_TYPE, topicType, clientContext);
            tx.success();
            return assocType;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Retrieving association type \"" + uri + "\" failed", e);
        } finally {
            tx.finish();
        }
    }

    @Override
    public AssociationType createAssociationType(AssociationTypeModel assocTypeModel,
                                                 @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            AttachedAssociationType assocType = new AttachedAssociationType(assocTypeModel, this);
            assocType.store();
            //
            tx.success();
            return assocType;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Creating association type \"" + assocTypeModel.getUri() +
                "\" failed (" + assocTypeModel + ")", e);
        } finally {
            tx.finish();
        }
    }



    // === Commands ===

    @POST
    @Path("/command/{command}")
    @Consumes("application/json, multipart/form-data")
    @Override
    public CommandResult executeCommand(@PathParam("command") String command, CommandParams params,
                                        @HeaderParam("Cookie") ClientContext clientContext) {
        DeepaMehtaTransaction tx = beginTx();
        try {
            Iterator i = triggerHook(Hook.EXECUTE_COMMAND, command, params, clientContext).values().iterator();
            if (!i.hasNext()) {
                throw new RuntimeException("Command is not handled by any plugin");
            }
            CommandResult result = (CommandResult) i.next();
            if (i.hasNext()) {
                throw new RuntimeException("Ambiguity: more than one plugin returned a result");
            }
            tx.success();
            return result;
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Executing command \"" + command + "\" failed (params=" + params + ")", e);
        } finally {
            tx.finish();
        }
    }



    // === Plugins ===

    @Override
    public void registerPlugin(Plugin plugin) {
        pluginCache.put(plugin);
    }

    @Override
    public void unregisterPlugin(String pluginId) {
        pluginCache.remove(pluginId);
    }

    @Override
    public Plugin getPlugin(String pluginId) {
        return pluginCache.get(pluginId);
    }

    @GET
    @Path("/plugin")
    @Override
    public Set<PluginInfo> getPluginInfo() {
        final Set info = new HashSet();
        new PluginCache.Iterator() {
            @Override
            void body(Plugin plugin) {
                String pluginFile = plugin.getConfigProperty("clientSidePluginFile");
                info.add(new PluginInfo(plugin.getId(), pluginFile));
            }
        };
        return info;
    }

    @Override
    public void runPluginMigration(Plugin plugin, int migrationNr, boolean isCleanInstall) {
        runMigration(migrationNr, plugin, isCleanInstall);
        plugin.setMigrationNr(migrationNr);
    }



    // === Misc ===

    @Override
    public DeepaMehtaTransaction beginTx() {
        return storage.beginTx();
    }

    @Override
    public void checkPluginsReady() {
        Bundle[] bundles = bundleContext.getBundles();
        int plugins = 0;
        int registered = 0;
        for (Bundle bundle : bundles) {
            if (isDeepaMehtaPlugin(bundle)) {
                plugins++;
                if (isPluginRegistered(bundle)) {
                    registered++;
                }
            }
        }
        logger.info("### bundles total: " + bundles.length +
            ", DM plugins: " + plugins + ", registered: " + registered);
        if (plugins == registered) {
            logger.info("########## All plugins ready ##########");
            triggerHook(Hook.ALL_PLUGINS_READY);
        }
    }

    @Override
    public void setupDB() {
        DeepaMehtaTransaction tx = beginTx();
        try {
            logger.info("----- Initializing DeepaMehta 3 Core -----");
            boolean isCleanInstall = initDB();
            if (isCleanInstall) {
                setupBootstrapContent();
            }
            runCoreMigrations(isCleanInstall);
            tx.success();
            tx.finish();
            logger.info("----- Initialization of DeepaMehta 3 Core complete -----");
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            tx.finish();
            shutdown();
            throw new RuntimeException("Setting up the database failed", e);
        }
        // Note: we use no finally clause here because in case of error the core service has to be shut down.
    }

    @Override
    public void shutdown() {
        closeDB();
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods



    // === Topic REST API ===

    @GET
    @Path("/topic/{id}/related_topics")
    public Set<RelatedTopic> getRelatedTopics(@PathParam("id") long topicId,
                                              @QueryParam("assoc_type_uri") String assocTypeUri) {
        logger.info("topicId=" + topicId + ", assocTypeUri=\"" + assocTypeUri + "\"");
        try {
            return getTopic(topicId, false, null).getRelatedTopics(assocTypeUri);   // fetchComposite=false
        } catch (Exception e) {
            throw new RuntimeException("Retrieving related topics of topic " + topicId +
                " failed (assocTypeUri=\"" + assocTypeUri + "\")", e);
        }
    }

    /* TODO: activate for REST API
    Set<RelatedTopic> getRelatedTopics(long topicId, String assocTypeUri, String myRoleTypeUri,
                                                                          String othersRoleTypeUri,
                                                                          String othersTopicTypeUri,
                                                                          boolean fetchComposite) {
        return getTopic(topicId, null).getRelatedTopics(assocTypeUri, myRoleTypeUri, othersRoleTypeUri,
            othersTopicTypeUri, fetchComposite);
    } */



    // === Association REST API ===



    // === Helper ===

    Set<TopicModel> getTopicModels(Set<RelatedTopic> topics) {
        Set<TopicModel> models = new HashSet();
        for (Topic topic : topics) {
            models.add(((AttachedTopic) topic).getModel());
        }
        return models;
    }

    /**
     * Convenience method.
     */
    Association createAssociation(String typeUri, RoleModel roleModel1, RoleModel roleModel2) {
        // FIXME: clientContext=null
        return createAssociation(new AssociationModel(typeUri, roleModel1, roleModel2), null);
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Attaches this core service to a topic retrieved from storage layer.
     */
    AttachedTopic attach(TopicModel model, boolean fetchComposite) {
        AttachedTopic topic = new AttachedTopic(model, this);
        fetchComposite(fetchComposite, topic);
        return topic;
    }

    private Set<Topic> attach(Set<TopicModel> models, boolean fetchComposite) {
        Set<Topic> topics = new LinkedHashSet();
        for (TopicModel model : models) {
            topics.add(attach(model, fetchComposite));
        }
        return topics;
    }

    // ---

    AttachedRelatedTopic attach(RelatedTopicModel model, boolean fetchComposite) {
        AttachedRelatedTopic relTopic = new AttachedRelatedTopic(model, this);
        fetchComposite(fetchComposite, relTopic);
        return relTopic;
    }

    Set<RelatedTopic> attach(Iterable<RelatedTopicModel> models, boolean fetchComposite) {
        Set<RelatedTopic> relTopics = new LinkedHashSet();
        for (RelatedTopicModel model : models) {
            relTopics.add(attach(model, fetchComposite));
        }
        return relTopics;
    }

    // ---

    /**
     * Attaches this core service to an association retrieved from storage layer.
     */
    private AttachedAssociation attach(AssociationModel model) {
        return new AttachedAssociation(model, this);
    }

    Set<Association> attach(Set<AssociationModel> models) {
        Set<Association> assocs = new LinkedHashSet();
        for (AssociationModel model : models) {
            assocs.add(attach(model));
        }
        return assocs;
    }

    // ---

    AttachedRelatedAssociation attach(RelatedAssociationModel model) {
        return new AttachedRelatedAssociation(model, this);
    }

    // ---

    private void fetchComposite(boolean fetchComposite, AttachedTopic topic) {
        if (fetchComposite) {
            if (topic.getTopicType().getDataTypeUri().equals("dm3.core.composite")) {
                topic.loadComposite();
            }
        }
    }



    // === Topic/Association Storage ===

    void associateWithTopicType(TopicModel topic) {
        try {
            AssociationModel model = new AssociationModel("dm3.core.instantiation");
            model.setRoleModel1(new TopicRoleModel(topic.getTypeUri(), "dm3.core.type"));
            model.setRoleModel2(new TopicRoleModel(topic.getId(), "dm3.core.instance"));
            storage.createAssociation(model);
            associateWithAssociationType(model);
            // storage low-level call used here ### explain
        } catch (Exception e) {
            throw new RuntimeException("Associating topic with topic type \"" +
                topic.getTypeUri() + "\" failed (" + topic + ")", e);
        }
    }

    void associateWithAssociationType(AssociationModel assoc) {
        try {
            AssociationModel model = new AssociationModel("dm3.core.instantiation");
            model.setRoleModel1(new TopicRoleModel(assoc.getTypeUri(), "dm3.core.type"));
            model.setRoleModel2(new AssociationRoleModel(assoc.getId(), "dm3.core.instance"));
            storage.createAssociation(model);  // storage low-level call used here ### explain
        } catch (Exception e) {
            throw new RuntimeException("Associating association with association type \"" +
                assoc.getTypeUri() + "\" failed (" + assoc + ")", e);
        }
    }



    // === Topic Type Storage ===

    // FIXME: move to AttachedTopicType
    void associateDataType(String topicTypeUri, String dataTypeUri) {
        AssociationModel model = new AssociationModel("dm3.core.association");
        model.setRoleModel1(new TopicRoleModel(topicTypeUri, "dm3.core.topic_type"));
        model.setRoleModel2(new TopicRoleModel(dataTypeUri,  "dm3.core.data_type"));
        createAssociation(model, null);             // FIXME: clientContext=null
    }



    // === Plugins ===

    /**
     * Triggers a hook for all installed plugins.
     */
    private Map<String, Object> triggerHook(final Hook hook, final Object... params) {
        final Map resultMap = new HashMap();
        new PluginCache.Iterator() {
            @Override
            void body(Plugin plugin) {
                try {
                    Object result = triggerHook(plugin, hook, params);
                    if (result != null) {
                        resultMap.put(plugin.getId(), result);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Triggering hook " + hook + " of " + plugin + " failed", e);
                }
            }
        };
        return resultMap;
    }

    /**
     * @throws  NoSuchMethodException
     * @throws  IllegalAccessException
     * @throws  InvocationTargetException
     */
    private Object triggerHook(Plugin plugin, Hook hook, Object... params) throws Exception {
        Method hookMethod = plugin.getClass().getMethod(hook.methodName, hook.paramClasses);
        return hookMethod.invoke(plugin, params);
    }

    // ---

    private boolean isDeepaMehtaPlugin(Bundle bundle) {
        String packages = (String) bundle.getHeaders().get("Import-Package");
        // Note: packages might be null. Not all bundles import packges.
        return packages != null && packages.contains("de.deepamehta.core.service") &&
            !bundle.getSymbolicName().equals("de.deepamehta.3-core");
    }

    private boolean isPluginRegistered(Bundle bundle) {
        return pluginCache.contains(bundle.getSymbolicName());
    }



    // === DB ===

    /**
     * @return  <code>true</code> if this is a clean install, <code>false</code> otherwise.
     */
    private boolean initDB() {
        return storage.init();
    }

    private void closeDB() {
        storage.shutdown();
    }

    // ---

    private void setupBootstrapContent() {
        // Before topic types and asscociation types can be created the meta types must be created
        // Note: storage low-level call used here ### explain
        TopicModel tt = new TopicModel("dm3.core.topic_type", new TopicValue("Topic Type"), "dm3.core.meta_type");
        TopicModel at = new TopicModel("dm3.core.assoc_type", new TopicValue("Association Type"), "dm3.core.meta_type");
        _createTopic(tt);
        _createTopic(at);
        // Create topic type "Data Type"
        // ### Note: the topic type "Data Type" depends on the data type "Text" and the data type "Text" in turn
        // depends on the topic type "Data Type". To resolve this circle we use a low-level storage call here
        // and postpone the data type association.
        TopicModel dataType = new TopicTypeModel("dm3.core.data_type", "Data Type", "dm3.core.text");
        TopicModel roleType = new TopicTypeModel("dm3.core.role_type", "Role Type", "dm3.core.text");
        _createTopic(dataType);
        _createTopic(roleType);
        // Create data type "Text"
        TopicModel text = new TopicModel("dm3.core.text", new TopicValue("Text"), "dm3.core.data_type");
        _createTopic(text);
        // Create role types "Type" and "Instance"
        TopicModel type =     new TopicModel("dm3.core.type",     new TopicValue("Type"),     "dm3.core.role_type");
        TopicModel instance = new TopicModel("dm3.core.instance", new TopicValue("Instance"), "dm3.core.role_type");
        _createTopic(type);
        _createTopic(instance);
        // Before data type topics can be associated we must create the association type "Association"
        TopicModel association = new AssociationTypeModel("dm3.core.association", "Association");
        _createTopic(association);
        //
        TopicModel instantiation = new AssociationTypeModel("dm3.core.instantiation", "Instantiation");
        _createTopic(instantiation);
        // Postponed data type association
        associateDataType("dm3.core.meta_type",  "dm3.core.text");
        associateDataType("dm3.core.topic_type", "dm3.core.text");
        associateDataType("dm3.core.assoc_type", "dm3.core.text");
        associateDataType("dm3.core.data_type",  "dm3.core.text");
        associateDataType("dm3.core.role_type",  "dm3.core.text");
        // Postponed topic type association
        associateWithTopicType(tt);
        associateWithTopicType(at);
        associateWithTopicType(dataType);
        associateWithTopicType(roleType);
        associateWithTopicType(text);
        associateWithTopicType(type);
        associateWithTopicType(instance);
        associateWithTopicType(association);
        associateWithTopicType(instantiation);
    }

    private void _createTopic(TopicModel model) {
        storage.createTopic(model);
        storage.setTopicValue(model.getId(), model.getValue());
    }

    // ---

    private void bootstrapTypeCache() {
        typeCache.put(new AttachedTopicType(new TopicTypeModel("dm3.core.meta_meta_type", "Meta Meta Type",
            "dm3.core.meta_meta_meta_type", "dm3.core.text"), this));
    }



    // === Migrations ===

    private void runCoreMigrations(boolean isCleanInstall) {
        int migrationNr = storage.getMigrationNr();
        int requiredMigrationNr = REQUIRED_CORE_MIGRATION;
        int migrationsToRun = requiredMigrationNr - migrationNr;
        logger.info("Running " + migrationsToRun + " core migrations (migrationNr=" + migrationNr +
            ", requiredMigrationNr=" + requiredMigrationNr + ")");
        for (int i = migrationNr + 1; i <= requiredMigrationNr; i++) {
            runCoreMigration(i, isCleanInstall);
        }
    }

    private void runCoreMigration(int migrationNr, boolean isCleanInstall) {
        runMigration(migrationNr, null, isCleanInstall);
        storage.setMigrationNr(migrationNr);
    }

    // ---

    /**
     * Runs a core migration or a plugin migration.
     *
     * @param   migrationNr     Number of the migration to run.
     * @param   plugin          The plugin that provides the migration to run.
     *                          <code>null</code> for a core migration.
     * @param   isCleanInstall  <code>true</code> if the migration is run as part of a clean install,
     *                          <code>false</code> if the migration is run as part of an update.
     */
    private void runMigration(int migrationNr, Plugin plugin, boolean isCleanInstall) {
        MigrationInfo mi = null;
        try {
            mi = new MigrationInfo(migrationNr, plugin);
            if (!mi.success) {
                throw mi.exception;
            }
            // error checks
            if (!mi.isDeclarative && !mi.isImperative) {
                throw new RuntimeException("Neither a types file (" + mi.migrationFile +
                    ") nor a migration class (" + mi.migrationClassName + ") is found");
            }
            if (mi.isDeclarative && mi.isImperative) {
                throw new RuntimeException("Ambiguity: a types file (" + mi.migrationFile +
                    ") AND a migration class (" + mi.migrationClassName + ") are found");
            }
            // run migration
            String runInfo = " (runMode=" + mi.runMode + ", isCleanInstall=" + isCleanInstall + ")";
            if (mi.runMode.equals(MigrationRunMode.CLEAN_INSTALL.name()) == isCleanInstall ||
                mi.runMode.equals(MigrationRunMode.ALWAYS.name())) {
                logger.info("Running " + mi.migrationInfo + runInfo);
                if (mi.isDeclarative) {
                    JSONHelper.readMigrationFile(mi.migrationIn, mi.migrationFile, this);
                } else {
                    Migration migration = (Migration) mi.migrationClass.newInstance();
                    logger.info("Running " + mi.migrationType + " migration class " + mi.migrationClassName);
                    migration.setService(this);
                    migration.run();
                }
                logger.info("Completing " + mi.migrationInfo);
            } else {
                logger.info("Do NOT run " + mi.migrationInfo + runInfo);
            }
            logger.info("Updating migration number (" + migrationNr + ")");
        } catch (Exception e) {
            throw new RuntimeException("Running " + mi.migrationInfo + " failed", e);
        }
    }

    // ---

    /**
     * Collects the info required to run a migration.
     */
    private class MigrationInfo {

        String migrationType;       // "core", "plugin"
        String migrationInfo;       // for logging
        String runMode;             // "CLEAN_INSTALL", "UPDATE", "ALWAYS"
        //
        boolean isDeclarative;
        boolean isImperative;
        //
        String migrationFile;       // for declarative migration
        InputStream migrationIn;    // for declarative migration
        //
        String migrationClassName;  // for imperative migration
        Class migrationClass;       // for imperative migration
        //
        boolean success;            // error occurred?
        Exception exception;        // the error

        MigrationInfo(int migrationNr, Plugin plugin) {
            try {
                String configFile = migrationConfigFile(migrationNr);
                InputStream configIn;
                migrationFile = migrationFile(migrationNr);
                migrationType = plugin != null ? "plugin" : "core";
                //
                if (migrationType.equals("core")) {
                    migrationInfo = "core migration " + migrationNr;
                    logger.info("Preparing " + migrationInfo + " ...");
                    configIn     = getClass().getResourceAsStream(configFile);
                    migrationIn  = getClass().getResourceAsStream(migrationFile);
                    migrationClassName = coreMigrationClassName(migrationNr);
                    migrationClass = loadClass(migrationClassName);
                } else {
                    migrationInfo = "migration " + migrationNr + " of plugin \"" + plugin.getName() + "\"";
                    logger.info("Preparing " + migrationInfo + " ...");
                    configIn     = plugin.getResourceAsStream(configFile);
                    migrationIn  = plugin.getResourceAsStream(migrationFile);
                    migrationClassName = plugin.getMigrationClassName(migrationNr);
                    if (migrationClassName != null) {
                        migrationClass = plugin.loadClass(migrationClassName);
                    }
                }
                //
                isDeclarative = migrationIn != null;
                isImperative = migrationClass != null;
                //
                readMigrationConfigFile(configIn, configFile);
                //
                success = true;
            } catch (Exception e) {
                exception = e;
            }
        }

        // ---

        private void readMigrationConfigFile(InputStream in, String configFile) {
            try {
                Properties migrationConfig = new Properties();
                if (in != null) {
                    logger.info("Reading migration config file \"" + configFile + "\"");
                    migrationConfig.load(in);
                } else {
                    logger.info("Using default migration configuration (no migration config file found, " +
                        "tried \"" + configFile + "\")");
                }
                //
                runMode = migrationConfig.getProperty("migrationRunMode", MigrationRunMode.ALWAYS.name());
                MigrationRunMode.valueOf(runMode);  // check if value is valid
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Error in config file \"" + configFile + "\": \"" + runMode +
                    "\" is an invalid value for \"migrationRunMode\"");
            } catch (IOException e) {
                throw new RuntimeException("Config file \"" + configFile + "\" can't be read", e);
            }
        }

        // ---

        private String migrationFile(int migrationNr) {
            return "/migrations/migration" + migrationNr + ".json";
        }

        private String migrationConfigFile(int migrationNr) {
            return "/migrations/migration" + migrationNr + ".properties";
        }

        private String coreMigrationClassName(int migrationNr) {
            return CORE_MIGRATIONS_PACKAGE + ".Migration" + migrationNr;
        }

        // --- Generic Utilities ---

        /**
         * Uses the core bundle's class loader to load a class by name.
         *
         * @return  the class, or <code>null</code> if the class is not found.
         */
        private Class loadClass(String className) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}