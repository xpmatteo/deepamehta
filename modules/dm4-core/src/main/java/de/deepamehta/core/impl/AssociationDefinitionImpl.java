package de.deepamehta.core.impl;

import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.Type;
import de.deepamehta.core.ViewConfiguration;
import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicModel;

import java.util.logging.Logger;



/**
 * An association definition that is attached to the {@link DeepaMehtaService}.
 */
class AssociationDefinitionImpl extends AssociationImpl implements AssociationDefinition {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssociationDefinitionImpl(AssociationDefinitionModelImpl model, PersistenceLayer pl) {
        super(model, pl);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ********************************************
    // *** AssociationDefinition Implementation ***
    // ********************************************



    @Override
    public String getAssocDefUri() {
        return getModel().getAssocDefUri();
    }

    @Override
    public String getCustomAssocTypeUri() {
        return getModel().getCustomAssocTypeUri();
    }

    @Override
    public String getInstanceLevelAssocTypeUri() {
        return getModel().getInstanceLevelAssocTypeUri();
    }

    @Override
    public String getParentTypeUri() {
        return getModel().getParentTypeUri();
    }

    @Override
    public String getChildTypeUri() {
        return getModel().getChildTypeUri();
    }

    @Override
    public String getParentCardinalityUri() {
        return getModel().getParentCardinalityUri();
    }

    @Override
    public String getChildCardinalityUri() {
        return getModel().getChildCardinalityUri();
    }

    @Override
    public ViewConfiguration getViewConfig() {
        RoleModel configurable = pl.typeStorage.createConfigurableAssocDef(getId());   // ### ID is uninitialized
        return new ViewConfigurationImpl(configurable, getModel().getViewConfigModel(), pl);
    }

    @Override
    public AssociationDefinitionModelImpl getModel() {
        return (AssociationDefinitionModelImpl) super.getModel();
    }

    // ---

    @Override
    public void setParentCardinalityUri(String parentCardinalityUri) {
        // update memory
        getModel().setParentCardinalityUri(parentCardinalityUri);
        // update DB
        pl.typeStorage.storeParentCardinalityUri(getId(), parentCardinalityUri);
    }

    @Override
    public void setChildCardinalityUri(String childCardinalityUri) {
        // update memory
        getModel().setChildCardinalityUri(childCardinalityUri);
        // update DB
        pl.typeStorage.storeChildCardinalityUri(getId(), childCardinalityUri);
    }



    // === Updating ===

    @Override
    public void update(AssociationDefinitionModel newModel) {
        model.update(newModel);
    }
}
