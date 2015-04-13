package de.deepamehta.core;

import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.TopicModel;

import java.util.List;



public interface ChildTopics extends Iterable<String> {



    // === Accessors ===

    /**
     * Accesses a single-valued child.
     * Throws if there is no such child.
     */
    RelatedTopic getTopic(String childTypeUri);

    /**
     * Accesses a multiple-valued child.
     * Throws if there is no such child.
     */
    List<RelatedTopic> getTopics(String childTypeUri);

    // ---

    Object get(String childTypeUri);

    boolean has(String childTypeUri);

    int size();

    // ---

    ChildTopicsModel getModel();



    // === Convenience Accessors ===

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    String getString(String childTypeUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    int getInt(String childTypeUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    long getLong(String childTypeUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    double getDouble(String childTypeUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    boolean getBoolean(String childTypeUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    Object getObject(String childTypeUri);

    // ---

    /**
     * Convenience accessor for the *composite* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    ChildTopics getChildTopics(String childTypeUri);

    // Note: there are no convenience accessors for a multiple-valued child.



    // === Manipulators ===

    // --- Single-valued Childs ---

    /**
     * Sets a child.
     */
    ChildTopics set(String childTypeUri, TopicModel value);

    // ---

    /**
     * Convenience method to set the simple value of a child.
     *
     * @param   value   The simple value.
     *                  Either String, Integer, Long, Double, or Boolean. Primitive values are auto-boxed.
     */
    ChildTopics set(String childTypeUri, Object value);

    /**
     * Convenience method to set the composite value of a child.
     */
    ChildTopics set(String childTypeUri, ChildTopicsModel value);

    // ---

    ChildTopics setRef(String childTypeUri, long refTopicId);

    ChildTopics setRef(String childTypeUri, long refTopicId, ChildTopicsModel relatingAssocChildTopics);

    ChildTopics setRef(String childTypeUri, String refTopicUri);

    ChildTopics setRef(String childTypeUri, String refTopicUri, ChildTopicsModel relatingAssocChildTopics);

    // ---

    ChildTopics setDeletionRef(String childTypeUri, long refTopicId);

    ChildTopics setDeletionRef(String childTypeUri, String refTopicUri);

    // --- Multiple-valued Childs ---

    ChildTopics add(String childTypeUri, TopicModel value);

    // ---

    ChildTopics add(String childTypeUri, Object value);

    ChildTopics add(String childTypeUri, ChildTopicsModel value);

    // ---

    ChildTopics addRef(String childTypeUri, long refTopicId);

    ChildTopics addRef(String childTypeUri, long refTopicId, ChildTopicsModel relatingAssocChildTopics);

    ChildTopics addRef(String childTypeUri, String refTopicUri);

    ChildTopics addRef(String childTypeUri, String refTopicUri, ChildTopicsModel relatingAssocChildTopics);

    // ---

    ChildTopics addDeletionRef(String childTypeUri, long refTopicId);

    ChildTopics addDeletionRef(String childTypeUri, String refTopicUri);
}
