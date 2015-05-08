package de.deepamehta.core.impl;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.DeepaMehtaObjectModel;
import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicReferenceModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.model.TypeModel;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.util.JavaUtils;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;



/**
 * Helper for storing/fetching simple values and composite value models.
 */
class ValueStorage {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String LABEL_CHILD_SEPARATOR = " ";
    private static final String LABEL_TOPIC_SEPARATOR = ", ";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EmbeddedService dms;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    ValueStorage(EmbeddedService dms) {
        this.dms = dms;
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    /**
     * Fetches the child topic models (recursively) of the given parent object model and updates it in-place.
     * ### TODO: recursion is required in some cases (e.g. when fetching a topic through REST API) but is possibly
     * overhead in others (e.g. when updating composite structures).
     */
    private void fetchChildTopics(DeepaMehtaObjectModel parent) {
        for (AssociationDefinitionModel assocDef : getType(parent).getAssocDefs()) {
            fetchChildTopics(parent, assocDef);
        }
    }

    /**
     * Fetches the child topic models (recursively) of the given parent object model and updates it in-place.
     * ### TODO: recursion is required in some cases (e.g. when fetching a topic through REST API) but is possibly
     * overhead in others (e.g. when updating composite structures).
     * <p>
     * Works for both, "one" and "many" association definitions.
     *
     * @param   assocDef    The child topic models according to this association definition are fetched.
     */
    void fetchChildTopics(DeepaMehtaObjectModel parent, AssociationDefinitionModel assocDef) {
        try {
            ChildTopicsModel childTopics = parent.getChildTopicsModel();
            String cardinalityUri = assocDef.getChildCardinalityUri();
            String childTypeUri   = assocDef.getChildTypeUri();
            if (cardinalityUri.equals("dm4.core.one")) {
                RelatedTopicModel childTopic = fetchChildTopic(parent.getId(), assocDef);
                // Note: topics just created have no child topics yet
                if (childTopic != null) {
                    childTopics.put(childTypeUri, childTopic);
                    fetchChildTopics(childTopic);    // recursion
                }
            } else if (cardinalityUri.equals("dm4.core.many")) {
                for (RelatedTopicModel childTopic : fetchChildTopics(parent.getId(), assocDef)) {
                    childTopics.add(childTypeUri, childTopic);
                    fetchChildTopics(childTopic);    // recursion
                }
            } else {
                throw new RuntimeException("\"" + cardinalityUri + "\" is an unexpected cardinality URI");
            }
        } catch (Exception e) {
            throw new RuntimeException("Fetching the \"" + assocDef.getChildTypeUri() + "\" child topics of object " +
                parent.getId() + " failed", e);
        }        
    }

    // ---

    /**
     * Stores and indexes the specified model's value, either a simple value or a composite value (child topics).
     * Depending on the model type's data type dispatches either to storeSimpleValue() or to storeChildTopics().
     * <p>
     * Called to store the initial value of a newly created topic/association.
     */
    void storeValue(DeepaMehtaObjectModel model) {
        if (getType(model).getDataTypeUri().equals("dm4.core.composite")) {
            storeChildTopics(model);
            refreshLabel(model);
        } else {
            storeSimpleValue(model);
        }
    }

    /**
     * Indexes the simple value of the given object model according to the given index mode.
     * <p>
     * Called to index existing topics/associations once an index mode has been added to a type definition.
     */
    void indexSimpleValue(DeepaMehtaObjectModel model, IndexMode indexMode) {
        if (model instanceof TopicModel) {
            dms.storageDecorator.indexTopicValue(
                model.getId(),
                indexMode,
                model.getTypeUri(),
                getIndexValue(model)
            );
        } else if (model instanceof AssociationModel) {
            dms.storageDecorator.indexAssociationValue(
                model.getId(),
                indexMode,
                model.getTypeUri(),
                getIndexValue(model)
            );
        }
    }

    // ---

    /**
     * Prerequisite: this is a composite object.
     */
    void refreshLabel(DeepaMehtaObjectModel model) {
        try {
            String label = buildLabel(model);
            setSimpleValue(model, new SimpleValue(label));
        } catch (Exception e) {
            throw new RuntimeException("Refreshing label of object " + model.getId() + " failed (" + model + ")", e);
        }
    }

    void setSimpleValue(DeepaMehtaObjectModel model, SimpleValue value) {
        if (value == null) {
            throw new IllegalArgumentException("Tried to set a null SimpleValue (" + this + ")");
        }
        // update memory
        model.setSimpleValue(value);
        // update DB
        storeSimpleValue(model);
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Stores and indexes the simple value of the specified topic or association model.
     * Determines the index key and index modes.
     */
    private void storeSimpleValue(DeepaMehtaObjectModel model) {
        TypeModel type = getType(model);
        if (model instanceof TopicModel) {
            dms.storageDecorator.storeTopicValue(
                model.getId(),
                model.getSimpleValue(),
                type.getIndexModes(),
                type.getUri(),
                getIndexValue(model)
            );
        } else if (model instanceof AssociationModel) {
            dms.storageDecorator.storeAssociationValue(
                model.getId(),
                model.getSimpleValue(),
                type.getIndexModes(),
                type.getUri(),
                getIndexValue(model)
            );
        }
    }

    // ---

    /**
     * Called to store the initial value of a newly created topic/association.
     * Just prepares the arguments and calls storeChildTopics() repetitively.
     * <p>
     * Note: the given model can contain childs not defined in the type definition.
     * Only the childs defined in the type definition are stored.
     */
    private void storeChildTopics(DeepaMehtaObjectModel parent) {
        ChildTopicsModel model = null;
        try {
            model = parent.getChildTopicsModel();
            for (AssociationDefinitionModel assocDef : getType(parent).getAssocDefs()) {
                String childTypeUri   = assocDef.getChildTypeUri();
                String cardinalityUri = assocDef.getChildCardinalityUri();
                RelatedTopicModel childTopic        = null;     // only used for "one"
                List<RelatedTopicModel> childTopics = null;     // only used for "many"
                if (cardinalityUri.equals("dm4.core.one")) {
                    childTopic = model.getTopic(childTypeUri, null);        // defaultValue=null
                    // skip if not contained in create request
                    if (childTopic == null) {
                        continue;
                    }
                } else if (cardinalityUri.equals("dm4.core.many")) {
                    childTopics = model.getTopics(childTypeUri, null);      // defaultValue=null
                    // skip if not contained in create request
                    if (childTopics == null) {
                        continue;
                    }
                } else {
                    throw new RuntimeException("\"" + cardinalityUri + "\" is an unexpected cardinality URI");
                }
                //
                storeChildTopics(childTopic, childTopics, parent, assocDef);
            }
        } catch (Exception e) {
            throw new RuntimeException("Storing the child topics of object " + parent.getId() + " failed (" +
                model + ")", e);
        }
    }

    private void storeChildTopics(RelatedTopicModel childTopic, List<RelatedTopicModel> childTopics,
                                  DeepaMehtaObjectModel parent, AssociationDefinitionModel assocDef) {
        String assocTypeUri = assocDef.getTypeUri();
        boolean one = childTopic != null;
        if (assocTypeUri.equals("dm4.core.composition_def")) {
            if (one) {
                storeCompositionOne(childTopic, parent, assocDef);
            } else {
                storeCompositionMany(childTopics, parent, assocDef);
            }
        } else if (assocTypeUri.equals("dm4.core.aggregation_def")) {
            if (one) {
                storeAggregationOne(childTopic, parent, assocDef);
            } else {
                storeAggregationMany(childTopics, parent, assocDef);
            }
        } else {
            throw new RuntimeException("Unexpected association type: \"" + assocTypeUri + "\"");
        }
    }

    // --- Composition ---

    private void storeCompositionOne(RelatedTopicModel childTopic, DeepaMehtaObjectModel parent,
                                                                   AssociationDefinitionModel assocDef) {
        createAndAssociateChildTopic(childTopic, parent, assocDef);
    }

    private void storeCompositionMany(List<RelatedTopicModel> childTopics, DeepaMehtaObjectModel parent,
                                                                           AssociationDefinitionModel assocDef) {
        for (RelatedTopicModel childTopic : childTopics) {
            createAndAssociateChildTopic(childTopic, parent, assocDef);
        }
    }

    // --- Aggregation ---

    private void storeAggregationOne(RelatedTopicModel childTopic, DeepaMehtaObjectModel parent,
                                                                   AssociationDefinitionModel assocDef) {
        if (childTopic instanceof TopicReferenceModel) {
            resolveRefAndAssociateChildTopic((TopicReferenceModel) childTopic, parent, assocDef);
        } else {
            createAndAssociateChildTopic(childTopic, parent, assocDef);
        }
    }

    private void storeAggregationMany(List<RelatedTopicModel> childTopics, DeepaMehtaObjectModel parent,
                                                                           AssociationDefinitionModel assocDef) {
        for (RelatedTopicModel childTopic : childTopics) {
            if (childTopic instanceof TopicReferenceModel) {
                resolveRefAndAssociateChildTopic((TopicReferenceModel) childTopic, parent, assocDef);
            } else {
                createAndAssociateChildTopic(childTopic, parent, assocDef);
            }
        }
    }

    // ---

    private void createAndAssociateChildTopic(RelatedTopicModel childTopic, DeepaMehtaObjectModel parent,
                                                                            AssociationDefinitionModel assocDef) {
        dms.createTopic(childTopic);
        //
        associateChildTopic(parent, childTopic, assocDef);
    }

    private void resolveRefAndAssociateChildTopic(TopicReferenceModel childTopicRef, DeepaMehtaObjectModel parent,
                                                                                  AssociationDefinitionModel assocDef) {
        resolveReference(childTopicRef);
        //
        associateChildTopic(parent, childTopicRef, assocDef);
    }

    // ---

    /**
     * Replaces a reference with the real thing.
     */
    void resolveReference(TopicReferenceModel topicRef) {
        topicRef.set(fetchReferencedTopic(topicRef).getModel());
    }

    private Topic fetchReferencedTopic(TopicReferenceModel topicRef) {
        // Note: the resolved topic must be fetched including its composite value.
        // It might be required at client-side. ### TODO
        if (topicRef.isReferenceById()) {
            return dms.getTopic(topicRef.getId());                          // ### FIXME: had fetchComposite=true
        } else if (topicRef.isReferenceByUri()) {
            return dms.getTopic("uri", new SimpleValue(topicRef.getUri())); // ### FIXME: had fetchComposite=true
        } else {
            throw new RuntimeException("Invalid topic reference (" + topicRef + ")");
        }
    }

    // ---

    /**
     * Creates an association between the given parent object ("Parent" role) and the child topic ("Child" role).
     * The association type is taken from the given association definition.
     */
    void associateChildTopic(DeepaMehtaObjectModel parent, RelatedTopicModel childTopic,
                                                           AssociationDefinitionModel assocDef) {
        AssociationModel assoc = childTopic.getRelatingAssociation();
        assoc.setTypeUri(assocDef.getInstanceLevelAssocTypeUri());
        assoc.setRoleModel1(parent.createRoleModel("dm4.core.parent"));
        assoc.setRoleModel2(childTopic.createRoleModel("dm4.core.child"));
        dms.createAssociation(assoc);
    }



    // === Label ===

    private String buildLabel(DeepaMehtaObjectModel model) {
        TypeModel type = getType(model);
        if (type.getDataTypeUri().equals("dm4.core.composite")) {
            List<String> labelConfig = type.getLabelConfig();
            if (labelConfig.size() > 0) {
                return buildLabelFromConfig(model, labelConfig);
            } else {
                return buildDefaultLabel(model);
            }
        } else {
            return model.getSimpleValue().toString();
        }
    }

    /**
     * Builds the specified object model's label according to a label configuration.
     */
    private String buildLabelFromConfig(DeepaMehtaObjectModel model, List<String> labelConfig) {
        StringBuilder builder = new StringBuilder();
        for (String childTypeUri : labelConfig) {
            appendLabel(buildChildLabel(model, childTypeUri), builder, LABEL_CHILD_SEPARATOR);
        }
        return builder.toString();
    }

    private String buildDefaultLabel(DeepaMehtaObjectModel model) {
        Iterator<AssociationDefinitionModel> i = getType(model).getAssocDefs().iterator();
        // Note: types just created might have no child types yet
        if (!i.hasNext()) {
            return "";
        }
        //
        String childTypeUri = i.next().getChildTypeUri();
        return buildChildLabel(model, childTypeUri);
    }

    // ---

    private String buildChildLabel(DeepaMehtaObjectModel parent, String childTypeUri) {
        Object value = parent.getChildTopicsModel().get(childTypeUri);
        // Note: topics just created have no child topics yet
        if (value == null) {
            return "";
        }
        //
        if (value instanceof TopicModel) {
            TopicModel childTopic = (TopicModel) value;
            return buildLabel(childTopic);                                          // recursion
        } else if (value instanceof List) {
            StringBuilder builder = new StringBuilder();
            for (TopicModel childTopic : (List<TopicModel>) value) {
                appendLabel(buildLabel(childTopic), builder, LABEL_TOPIC_SEPARATOR);  // recursion
            }
            return builder.toString();
        } else {
            throw new RuntimeException("Unexpected value in a ChildTopicsModel: " + value);
        }
    }

    private void appendLabel(String label, StringBuilder builder, String separator) {
        // add separator
        if (builder.length() > 0 && label.length() > 0) {
            builder.append(separator);
        }
        //
        builder.append(label);
    }



    // === Helper ===

    /**
     * Fetches and returns a child topic or <code>null</code> if no such topic extists.
     */
    private RelatedTopicModel fetchChildTopic(long parentId, AssociationDefinitionModel assocDef) {
        return dms.storageDecorator.fetchRelatedTopic(
            parentId,
            assocDef.getInstanceLevelAssocTypeUri(),
            "dm4.core.parent", "dm4.core.child",
            assocDef.getChildTypeUri()
        );
    }

    private ResultList<RelatedTopicModel> fetchChildTopics(long parentId, AssociationDefinitionModel assocDef) {
        return dms.storageDecorator.fetchRelatedTopics(
            parentId,
            assocDef.getInstanceLevelAssocTypeUri(),
            "dm4.core.parent", "dm4.core.child",
            assocDef.getChildTypeUri()
        );
    }

    // ---

    /**
     * Calculates the simple value that is to be indexed for this object.
     *
     * HTML tags are stripped from HTML values. Non-HTML values are returned directly.
     */
    private SimpleValue getIndexValue(DeepaMehtaObjectModel model) {
        SimpleValue value = model.getSimpleValue();
        if (getType(model).getDataTypeUri().equals("dm4.core.html")) {
            return new SimpleValue(JavaUtils.stripHTML(value.toString()));
        } else {
            return value;
        }
    }

    /**
     * Returns the type model of a DeepaMehta object model.
     * The type is obtained from the type storage.
     */
    private TypeModel getType(DeepaMehtaObjectModel model) {
        if (model instanceof TopicModel) {
            return dms.typeStorage.getTopicType(model.getTypeUri());
        } else if (model instanceof AssociationModel) {
            return dms.typeStorage.getAssociationType(model.getTypeUri());
        }
        throw new RuntimeException("Unexpected model: " + model);
    }
}
