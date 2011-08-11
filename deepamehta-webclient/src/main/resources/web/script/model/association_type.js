function AssociationType(assoc_type) {

    this.id        = assoc_type.id
    this.uri       = assoc_type.uri
    this.value     = assoc_type.value
    this.type_uri  = assoc_type.type_uri
    this.composite = assoc_type.composite
    //
    this.view_config_topics = dm4c.hash_by_type(assoc_type.view_config_topics)

    // === "Page Displayable" implementation ===

    this.get_type = function() {
        return dm4c.type_cache.get_topic_type(this.type_uri)
    }

    this.get_commands = function(context) {
        return dm4c.get_topic_commands(this, context)
    }

    this.get_page_renderer_class = function() {
        return dm4c.get_view_config(this, "js_page_renderer_class") || "AssociationRenderer"
    }

    // === Public API ===

    this.get_color = function() {
        return dm4c.get_view_config(this, "color") || dm4c.canvas.DEFAULT_ASSOC_COLOR
    }
}