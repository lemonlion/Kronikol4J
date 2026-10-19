package io.kronikol.report.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which keys the report-data JSON Schema requires, wherever they stand: walked beside the JSON a compressed report
 * holds, so that a required key is written even when it is null or empty and every other empty key is left out. A port
 * of the .NET {@code Kronikol.Compression.RequiredKeys} (plans/REPORT_FORMAT_PLAN.md 4.2): read from the schema's own
 * {@code required} lists at every depth, never typed out.
 *
 * <p>It reads {@code properties}, {@code items} and {@code additionalProperties}, references into {@code definitions}
 * or {@code $defs} (a recursive definition is one node), and the branches of {@code oneOf}, {@code anyOf} and
 * {@code allOf}, whose requirements are joined: a key one branch requires is kept. An object the schema declares but
 * leaves undescribed is open: what it holds is kept exactly as written.
 */
final class ReportRequirements {

    private ReportRequirements() {
    }

    /** The node of the document's root, read from a schema parsed into ordered maps and lists. */
    static Node fromSchema(Object schema) {
        Map<String, Object> definitions = new HashMap<>();
        if (schema instanceof Map<?, ?> root) {
            for (String section : List.of("definitions", "$defs")) {
                if (root.get(section) instanceof Map<?, ?> defs) {
                    for (Map.Entry<?, ?> definition : defs.entrySet()) {
                        definitions.put("#/" + section + "/" + definition.getKey(), definition.getValue());
                    }
                }
            }
        }
        return build(schema, definitions, new HashMap<>());
    }

    private static Node build(Object schema, Map<String, Object> definitions, Map<String, Node> built) {
        if (schema instanceof Map<?, ?> map && map.get("$ref") instanceof String target) {
            Node known = built.get(target);
            if (known != null) {
                return known;
            }
            Node node = new Node();
            built.put(target, node);
            Object definition = definitions.get(target);
            if (definition != null) {
                fill(node, definition, definitions, built);
            }
            return node;
        }
        Node fresh = new Node();
        fill(fresh, schema, definitions, built);
        return fresh;
    }

    private static void fill(Node node, Object schema, Map<String, Object> definitions, Map<String, Node> built) {
        if (!(schema instanceof Map<?, ?> map)) {
            return;
        }
        if (map.containsKey("$ref")) {
            node.join(build(schema, definitions, built));
            return;
        }
        if (map.get("required") instanceof List<?> required) {
            for (Object key : required) {
                if (key instanceof String name) {
                    node.required.add(name);
                }
            }
        }
        if (map.get("properties") instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> property : properties.entrySet()) {
                node.properties.put((String) property.getKey(), build(property.getValue(), definitions, built));
            }
        }
        if (map.get("items") instanceof Map<?, ?> items) {
            node.items = build(items, definitions, built);
        }
        Object additional = map.get("additionalProperties");
        if (additional instanceof Map<?, ?>) {
            node.additional = build(additional, definitions, built);
        } else if (Boolean.TRUE.equals(additional)) {
            node.additional = Node.OPEN;
        }
        // An object the schema leaves undescribed, deliberately (internalFlowSegments, a step's exampleValues): what it
        // holds is kept exactly as written.
        if (isObjectType(map) && !describes(map)) {
            node.open = true;
        }
        for (String combinator : List.of("oneOf", "anyOf", "allOf")) {
            if (map.get(combinator) instanceof List<?> branches) {
                for (Object branch : branches) {
                    Node joined = new Node();
                    fill(joined, branch, definitions, built);
                    node.join(joined);
                }
            }
        }
    }

    private static boolean isObjectType(Map<?, ?> schema) {
        Object type = schema.get("type");
        return "object".equals(type) || type instanceof List<?> types && types.contains("object");
    }

    private static boolean describes(Map<?, ?> schema) {
        for (String keyword : List.of("properties", "additionalProperties", "patternProperties", "items", "$ref",
            "oneOf", "anyOf", "allOf")) {
            if (schema.containsKey(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** One place in the schema: the keys it requires, and the nodes its properties, items and map values stand in. */
    static final class Node {
        /** The node everything inside an open part stands in: every key in it is written, as it came. */
        static final Node OPEN = new Node();

        static {
            OPEN.open = true;
        }

        private final Set<String> required = new LinkedHashSet<>();
        private final Map<String, Node> properties = new LinkedHashMap<>();
        private final List<Node> joined = new ArrayList<>();
        private Node items;
        private Node additional;
        private boolean open;

        /** Whether the schema leaves what this node holds undescribed, so that it is kept exactly as written. */
        boolean isOpen() {
            return open;
        }

        /** Whether this node requires {@code name}. Inside an open part, every key. */
        boolean requires(String name) {
            if (open || required.contains(name)) {
                return true;
            }
            for (Node node : joined) {
                if (node.requires(name)) {
                    return true;
                }
            }
            return false;
        }

        /** The node a property's value stands in, or {@code null} where the schema says nothing of it. */
        Node property(String name) {
            if (open) {
                return OPEN;
            }
            Node child = properties.get(name);
            if (child != null) {
                return child;
            }
            for (Node node : joined) {
                Node found = node.property(name);
                if (found != null) {
                    return found;
                }
            }
            return additional != null ? additional : firstJoined(n -> n.additional);
        }

        /** The node an array's elements stand in, or {@code null} where the schema says nothing of them. */
        Node items() {
            if (open) {
                return OPEN;
            }
            return items != null ? items : firstJoined(n -> n.items);
        }

        private Node firstJoined(Function<Node, Node> select) {
            for (Node node : joined) {
                Node found = select.apply(node);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private void join(Node other) {
            if (other != this) {
                joined.add(other);
            }
        }
    }
}
