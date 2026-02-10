/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class to normalize JSON strings for comparison in tests, by sorting arrays and parsing embedded JSON strings
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
public final class TestJsonNormalizer {
    private TestJsonNormalizer() {
    }

    // normalize JSON string for comparison: sort primitive arrays and arrays of objects deterministically; coerce textual numeric "id" to numeric node
    static String normalizeJsonForComparison(ObjectMapper objectMapper, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            normalizeNode(objectMapper, root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    static void normalizeNode(ObjectMapper objectMapper, JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> it = obj.fieldNames();
            List<String> names = new ArrayList<>();
            while (it.hasNext()) {
                names.add(it.next());
            }
            for (String name : names) {
                JsonNode child = obj.get(name);
                if (child == null) {
                    continue;
                }

                // If the value is a textual JSON (embedded JSON string), parse and replace it so we compare structures
                if (child.isTextual()) {
                    String text = child.asText().trim();
                    if (text.startsWith("{") && text.endsWith("}") || text.startsWith("[") && text.endsWith("]")) {
                        try {
                            JsonNode parsed = objectMapper.readTree(text);
                            normalizeNode(objectMapper, parsed);
                            obj.set(name, parsed);
                            continue;
                        } catch (Exception ignored) {
                            // keep original text if parsing fails
                        }
                    }
                }

                if (child.isArray()) {
                    ArrayNode arr = (ArrayNode) child;
                    // parse elements that are textual JSON
                    for (int i = 0; i < arr.size(); i++) {
                        JsonNode el = arr.get(i);
                        if (el != null && el.isTextual()) {
                            String t = el.asText().trim();
                            if (t.startsWith("{") && t.endsWith("}") || t.startsWith("[") && t.endsWith("]")) {
                                try {
                                    JsonNode parsed = objectMapper.readTree(t);
                                    normalizeNode(objectMapper, parsed);
                                    arr.set(i, parsed);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }

                    // determine if all elements are primitive (value nodes)
                    boolean allPrimitive = true;
                    for (JsonNode el : arr) {
                        if (!el.isValueNode()) {
                            allPrimitive = false;
                            break;
                        }
                    }
                    if (allPrimitive) {
                        List<String> values = new ArrayList<>();
                        for (JsonNode el : arr) {
                            values.add(el.isNumber() ? el.numberValue().toString() : el.asText());
                        }
                        Collections.sort(values);
                        ArrayNode newArr = objectMapper.createArrayNode();
                        for (String v : values) {
                            if (v.matches("-?\\d+(\\.\\d+)?")) {
                                if (v.matches("-?\\d+")) {
                                    newArr.add(Long.parseLong(v));
                                } else {
                                    newArr.add(Double.parseDouble(v));
                                }
                            } else {
                                newArr.add(v);
                            }
                        }
                        obj.set(name, newArr);
                    } else {
                        // sort array of objects by stable serialization
                        List<JsonNode> list = new ArrayList<>();
                        for (JsonNode el : arr) {
                            list.add(el);
                        }
                        list.sort(Comparator.comparing(n -> {
                            try {
                                return objectMapper.writeValueAsString(n);
                            } catch (Exception e) {
                                return "";
                            }
                        }));
                        ArrayNode newArr = objectMapper.createArrayNode();
                        for (JsonNode el : list) {
                            newArr.add(el);
                        }
                        obj.set(name, newArr);
                        // still normalize children
                        for (JsonNode el : newArr) {
                            normalizeNode(objectMapper, el);
                        }
                    }
                } else if ("id".equals(name) && child.isTextual() && child.asText().matches("\\d+")) {
                    obj.set(name, objectMapper.getNodeFactory().numberNode(Integer.parseInt(child.asText())));
                } else {
                    normalizeNode(objectMapper, child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;

            // parse textual elements that contain JSON
            for (int i = 0; i < arr.size(); i++) {
                JsonNode el = arr.get(i);
                if (el != null && el.isTextual()) {
                    String t = el.asText().trim();
                    if (t.startsWith("{") && t.endsWith("}") || t.startsWith("[") && t.endsWith("]")) {
                        try {
                            JsonNode parsed = objectMapper.readTree(t);
                            normalizeNode(objectMapper, parsed);
                            arr.set(i, parsed);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            boolean allPrimitive = true;
            for (JsonNode el : arr) {
                if (!el.isValueNode()) {
                    allPrimitive = false;
                    break;
                }
            }
            if (allPrimitive) {
                List<String> values = new ArrayList<>();
                for (JsonNode el : arr) {
                    values.add(el.isNumber() ? el.numberValue().toString() : el.asText());
                }
                Collections.sort(values);
                arr.removeAll();
                for (String v : values) {
                    if (v.matches("-?\\d+(\\.\\d+)?")) {
                        if (v.matches("-?\\d+")) {
                            arr.add(Long.parseLong(v));
                        } else {
                            arr.add(Double.parseDouble(v));
                        }
                    } else {
                        arr.add(v);
                    }
                }
            } else {
                List<JsonNode> list = new ArrayList<>();
                for (JsonNode el : arr) {
                    list.add(el);
                }
                list.sort(Comparator.comparing(n -> {
                    try {
                        return objectMapper.writeValueAsString(n);
                    } catch (Exception e) {
                        return "";
                    }
                }));
                arr.removeAll();
                for (JsonNode el : list) {
                    arr.add(el);
                }
                for (JsonNode el : arr) {
                    normalizeNode(objectMapper, el);
                }
            }
        }
    }
}
