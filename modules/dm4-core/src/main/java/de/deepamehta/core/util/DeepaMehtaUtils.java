package de.deepamehta.core.util;

import de.deepamehta.core.Identifiable;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.DeepaMehtaService;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



public class DeepaMehtaUtils {

    private static final Logger logger = Logger.getLogger(DeepaMehtaUtils.class.getName());

    private static final String DM4_HOST_URL = System.getProperty("dm4.host.url");  // ### TODO: default value (#734)
    static {
        logger.info("Host setting:\ndm4.host.url=\"" + DM4_HOST_URL + "\"");
    }



    // ************
    // *** URLs ***
    // ************



    /**
     * Checks if an URL refers to this DeepaMehta installation.
     * The check relies on the "dm4.host.url" system property.
     */
    public static boolean isDeepaMehtaURL(URL url) {
        try {
            return url.toString().startsWith(DM4_HOST_URL);
        } catch (Exception e) {
            throw new RuntimeException("Checking for DeepaMehta URL failed (url=\"" + url + "\")", e);
        }
    }



    // *******************
    // *** Collections ***
    // *******************



    public static List<Long> idList(Iterable<? extends Identifiable> items) {
        List<Long> ids = new ArrayList();
        for (Identifiable item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    public static List<TopicModel> toTopicModels(Iterable<? extends Topic> topics) {
        List<TopicModel> topicModels = new ArrayList();
        for (Topic topic : topics) {
            topicModels.add(topic.getModel());
        }
        return topicModels;
    }

    public static String topicNames(Iterable<? extends Topic> topics) {
        StringBuilder names = new StringBuilder();
        Iterator<? extends Topic> i = topics.iterator();
        while (i.hasNext()) {
            Topic topic = i.next();
            names.append('"').append(topic.getSimpleValue()).append('"');
            if (i.hasNext()) {
                names.append(", ");
            }
        }
        return names.toString();
    }



    // ************
    // *** JSON ***
    // ************



    // === Generic ===

    public static Map toMap(JSONObject o) {
        return toMap(o, new HashMap());
    }

    public static Map toMap(JSONObject o, Map map) {
        try {
            Iterator<String> i = o.keys();
            while (i.hasNext()) {
                String key = i.next();
                map.put(key, o.get(key));   // throws JSONException
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException("Converting JSONObject to Map failed", e);
        }
    }

    // ---

    public static List toList(JSONArray o) {
        try {
            List list = new ArrayList();
            for (int i = 0; i < o.length(); i++) {
                list.add(o.get(i));         // throws JSONException
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("Converting JSONArray to List failed", e);
        }
    }

    // === DeepaMehta specific ===

    public static JSONArray toJSONArray(Iterable<? extends JSONEnabled> items) {
        JSONArray array = new JSONArray();
        for (JSONEnabled item : items) {
            array.put(item.toJSON());
        }
        return array;
    }



    // *******************************
    // *** Association Auto-Typing ***
    // *******************************



    public static RoleModel[] associationAutoTyping(AssociationModel assoc, String topicTypeUri1, String topicTypeUri2,
                                 String assocTypeUri, String roleTypeUri1, String roleTypeUri2, DeepaMehtaService dms) {
        if (!assoc.getTypeUri().equals("dm4.core.association")) {
            return null;
        }
        RoleModel[] roles = getRoleModels(assoc, topicTypeUri1, topicTypeUri2, dms);
        if (roles != null) {
            logger.info("### Auto typing association into \"" + assocTypeUri +
                "\" (\"" + topicTypeUri1 + "\" <-> \"" + topicTypeUri2 + "\")");
            assoc.setTypeUri(assocTypeUri);
            roles[0].setRoleTypeUri(roleTypeUri1);
            roles[1].setRoleTypeUri(roleTypeUri2);
        }
        return roles;
    }

    public static RoleModel[] getRoleModels(AssociationModel assoc, String topicTypeUri1, String topicTypeUri2,
                                                                                          DeepaMehtaService dms) {
        RoleModel r1 = assoc.getRoleModel1();
        RoleModel r2 = assoc.getRoleModel2();
        String t1 = (String) dms.getProperty(r1.getPlayerId(), "type_uri");
        String t2 = (String) dms.getProperty(r2.getPlayerId(), "type_uri");
        RoleModel roleModel1 = getRoleModel(r1, r2, t1, t2, topicTypeUri1, 1);
        RoleModel roleModel2 = getRoleModel(r1, r2, t1, t2, topicTypeUri2, 2);
        if (roleModel1 != null && roleModel2 != null) {
            return new RoleModel[] {roleModel1, roleModel2};
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private static RoleModel getRoleModel(RoleModel r1, RoleModel r2, String t1, String t2, String topicTypeUri,
                                                                                            int nr) {
        boolean m1 = t1.equals(topicTypeUri);
        boolean m2 = t2.equals(topicTypeUri);
        if (m1 && m2) {
            return nr == 1 ? r1 : r2;
        }
        return m1 ? r1 : m2 ? r2 : null;
    }
}
