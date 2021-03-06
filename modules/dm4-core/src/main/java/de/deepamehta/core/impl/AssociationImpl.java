package de.deepamehta.core.impl;

import de.deepamehta.core.Association;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedObject;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Role;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicRole;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicRoleModel;

import java.util.List;

import java.util.logging.Logger;



/**
 * An association model that is attached to the DB.
 */
class AssociationImpl extends DeepaMehtaObjectImpl implements Association {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssociationImpl(AssociationModelImpl model, PersistenceLayer pl) {
        super(model, pl);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // **********************************
    // *** Association Implementation ***
    // **********************************



    @Override
    public Role getRole1() {
        return getModel().getRoleModel1().instantiate(getModel());
    }

    @Override
    public Role getRole2() {
        return getModel().getRoleModel2().instantiate(getModel());
    }

    // ---

    @Override
    public DeepaMehtaObject getPlayer1() {
        return getRole1().getPlayer();
    }

    @Override
    public DeepaMehtaObject getPlayer2() {
        return getRole2().getPlayer();
    }

    // --- Convenience Methods ---

    @Override
    public Role getRole(String roleTypeUri) {
        return getModel().getRoleModel(roleTypeUri).instantiate(getModel());
    }

    @Override
    public boolean hasSameRoleTypeUris() {
        return getModel().hasSameRoleTypeUris();
    }

    @Override
    public boolean matches(String roleTypeUri1, long playerId1, String roleTypeUri2, long playerId2) {
        return getModel().matches(roleTypeUri1, playerId1, roleTypeUri2, playerId2);
    }

    @Override
    public long getOtherPlayerId(long id) {
        return getModel().getOtherPlayerId(id);
    }

    // ---

    @Override
    public RelatedObject getPlayer(String roleTypeUri) {
        DeepaMehtaObjectModelImpl object = getModel().getPlayer(roleTypeUri);
        return object != null ? (RelatedObject) object.instantiate() : null;    // ### TODO: permission check?
    }

    @Override
    public Topic getTopicByType(String topicTypeUri) {
        TopicModelImpl topic = getModel().getTopicByType(topicTypeUri);
        return topic != null ? topic.instantiate() : null;                      // ### TODO: permission check?
    }

    // ---

    // ### TODO: make use of model's getRole()
    @Override
    public Role getRole(RoleModel roleModel) {
        if (getRole1().getModel().refsSameObject(roleModel)) {
            return getRole1();
        } else if (getRole2().getModel().refsSameObject(roleModel)) {
            return getRole2();
        }
        throw new RuntimeException("Role is not part of association (role=" + roleModel + ", association=" + this);
    }

    @Override
    public boolean isPlayer(TopicRoleModel roleModel) {
        return filterRole(getRole1(), roleModel) != null || filterRole(getRole2(), roleModel) != null;
    }

    // ---

    @Override
    public void update(AssociationModel updateModel) {
        model.update((AssociationModelImpl) updateModel);     // ### FIXME: call through pl for access control
    }

    // ---

    @Override
    public Association loadChildTopics() {
        model.loadChildTopics();
        return this;
    }

    @Override
    public Association loadChildTopics(String assocDefUri) {
        model.loadChildTopics(assocDefUri);
        return this;
    }

    // ---

    @Override
    public AssociationModelImpl getModel() {
        return (AssociationModelImpl) model;
    }



    // ***************************************
    // *** DeepaMehtaObject Implementation ***
    // ***************************************



    // === Traversal ===

    // ### TODO: move logic to model

    // --- Topic Retrieval ---

    @Override
    public List<RelatedTopic> getRelatedTopics(List assocTypeUris, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                         String othersTopicTypeUri) {
        List<RelatedTopicModelImpl> topics = pl.fetchAssociationRelatedTopics(getId(), assocTypeUris, myRoleTypeUri,
            othersRoleTypeUri, othersTopicTypeUri);
        return pl.checkReadAccessAndInstantiate(topics);
    }

    // --- Association Retrieval ---

    @Override
    public RelatedAssociation getRelatedAssociation(String assocTypeUri, String myRoleTypeUri,
                                                    String othersRoleTypeUri, String othersAssocTypeUri) {
        RelatedAssociationModelImpl assoc = pl.fetchAssociationRelatedAssociation(getId(),
            assocTypeUri, myRoleTypeUri, othersRoleTypeUri, othersAssocTypeUri);
        return assoc != null ? pl.<RelatedAssociation>checkReadAccessAndInstantiate(assoc) : null;
    }

    @Override
    public List<RelatedAssociation> getRelatedAssociations(String assocTypeUri, String myRoleTypeUri,
                                                           String othersRoleTypeUri, String othersAssocTypeUri) {
        List<RelatedAssociationModelImpl> assocs = pl.fetchAssociationRelatedAssociations(getId(), assocTypeUri,
            myRoleTypeUri, othersRoleTypeUri, othersAssocTypeUri);
        return pl.checkReadAccessAndInstantiate(assocs);
    }

    // ---

    @Override
    public Association getAssociation(String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                 long othersTopicId) {
        AssociationModelImpl assoc = pl.fetchAssociationBetweenTopicAndAssociation(assocTypeUri,
            othersTopicId, getId(), othersRoleTypeUri, myRoleTypeUri);
        return assoc != null ? pl.<Association>checkReadAccessAndInstantiate(assoc) : null;
    }

    @Override
    public List<Association> getAssociations() {
        return pl.checkReadAccessAndInstantiate(pl.fetchAssociationAssociations(getId()));
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    // --- Helper ---

    // ### TODO: move to model
    private TopicRole filterRole(Role role, TopicRoleModel roleModel) {
        return role instanceof TopicRole && role.getRoleTypeUri().equals(roleModel.getRoleTypeUri()) &&
            role.getPlayerId() == roleModel.getPlayerId() ? (TopicRole) role : null;
    }
}
