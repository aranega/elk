/*******************************************************************************
 * Copyright (c) 2017 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Christoph Daniel Schulze - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.graph.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkGraphElement;
import org.eclipse.elk.graph.ElkLabel;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.emf.ecore.EObject;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

/**
 * Generates identifiers for graph elements where missing. Inside ELK, this class is mainly used to generate
 * proper identifiers before a graph is serialized using the textual graph languages, which uses identifiers for
 * elements referencing each other.
 */
public final class GraphIdentifierGenerator {
    
    private ElkNode graph;
    private boolean generate = false;
    private boolean unique = false;
    
    /**
     * @param graph the graph for which to generate identifiers. 
     * @return a new {@link GraphIdentifierGenerator} instance that can be configured and finally executed. 
     * 
     * @see #assertExists()
     * @see #assertUnique()
     * @see #execute()
     */
    public static GraphIdentifierGenerator forGraph(final ElkNode graph) {
        return new GraphIdentifierGenerator(graph);
    }
    
    /**
     * Generates identifiers for the graph's elements, where missing.
     */
    public GraphIdentifierGenerator assertExists() {
        generate = true;
        return this;
    }
    
    /**
     * Makes sure existing identifiers are unique by appending a "_" to non-unique ones. 
     */
    public GraphIdentifierGenerator assertUnique() {
        unique = true;
        return this;
    }
    
    /**
     * Execute this generator. 
     */
    public void execute() {
        if (generate) {
            generateIdentifiers(graph);
        }
        if (unique) {
            assertAllIdsUnique(graph);
        }
    }
    
    /**
     * Enumeration of possible graph elements that can receive identifiers.
     */
    private static enum ElementType {
        NODE("N"),
        PORT("P"),
        EDGE("E"),
        EDGE_SECTION("ES"),
        LABEL("L");
        
        private String elementPrefix;
        
        ElementType(final String prefix) {
            elementPrefix = prefix;
        }
    }
    
    /** Array of highest identifiers generated so far for each kind of element. */
    private int[] currentIDs = new int[ElementType.values().length];
    /** Set of existing identifiers in the graph for collision detection. */
    private Set<String> existingIdentifiers = new HashSet<>();
    
    
    /**
     * Constructor is only called from the inside.
     */
    private GraphIdentifierGenerator(final ElkNode graph) {
        this.graph = graph;
        Iterator<EObject> iterator = graph.eAllContents();
        while (iterator.hasNext()) {
            EObject currentEObject = iterator.next();
            
            if (currentEObject instanceof ElkGraphElement) {
                ElkGraphElement element = (ElkGraphElement) currentEObject;
                if (element.getIdentifier() != null && !element.getIdentifier().trim().isEmpty()) {
                    existingIdentifiers.add(element.getIdentifier());
                }
            } else if (currentEObject instanceof ElkEdgeSection) {
                ElkEdgeSection section = (ElkEdgeSection) currentEObject;
                if (section.getIdentifier() != null && !section.getIdentifier().trim().isEmpty()) {
                    existingIdentifiers.add(section.getIdentifier());
                }
            }
        }
    }
   
    /**
     * Recursively generates identifiers for the given element and its child elements.
     * 
     * @param element the element to generate an identifier for.
     */
    private GraphIdentifierGenerator generateIdentifiers(final EObject element) {
        new ElkGraphSwitch<Object>() {
            
            @Override
            public Object caseElkNode(final ElkNode node) {
                if (node.getParent() == null) {
                    if (node.getIdentifier() == null || node.getIdentifier().trim().isEmpty()) {
                        node.setIdentifier("G1");
                    }
                } else {
                    setIdentifierIfMissing(node, ElementType.NODE);
                }
                
                node.getLabels().stream().forEach(l -> generateIdentifiers(l));
                node.getPorts().stream().forEach(p -> generateIdentifiers(p));
                node.getContainedEdges().stream().forEach(e -> generateIdentifiers(e));
                node.getChildren().stream().forEach(c -> generateIdentifiers(c));
                return null;
            }
            
            @Override
            public Object caseElkPort(final ElkPort port) {
                setIdentifierIfMissing(port, ElementType.PORT);
                
                port.getLabels().stream().forEach(l -> generateIdentifiers(l));
                return null;
            }
            
            @Override
            public Object caseElkLabel(final ElkLabel label) {
                setIdentifierIfMissing(label, ElementType.LABEL);
                
                label.getLabels().stream().forEach(l -> generateIdentifiers(l));
                return null;
            }
            
            @Override
            public Object caseElkEdge(final ElkEdge edge) {
                setIdentifierIfMissing(edge, ElementType.EDGE);
                
                edge.getLabels().stream().forEach(l -> generateIdentifiers(l));
                edge.getSections().stream().forEach(s -> generateIdentifiers(s));
                return null;
            }
            
            @Override
            public Object caseElkEdgeSection(final ElkEdgeSection section) {
                setIdentifierIfMissing(section);
                return null;
            }
            
        }.doSwitch(element);
        
        return this;
    }
    
    /**
     * Generates and sets a new identifier for the given element of the given type.
     */
    private void setIdentifierIfMissing(final ElkGraphElement element, final ElementType elementType) {
        if (element.getIdentifier() == null || element.getIdentifier().trim().isEmpty()) {
            String id = nextIdentifier(elementType);
            element.setIdentifier(id);
        }
    }
    
    /**
     * Generates and sets a new identifier for the given edge section.
     */
    private void setIdentifierIfMissing(final ElkEdgeSection section) {
        if (section.getIdentifier() == null || section.getIdentifier().trim().isEmpty()) {
            String id = nextIdentifier(ElementType.EDGE_SECTION);
            section.setIdentifier(id);
        }
    }
    
    /**
     * Returns the next unused identifier for an element of the given type.
     */
    private String nextIdentifier(final ElementType elementType) {
        String identifier;
        do {
            identifier = elementType.elementPrefix + ++currentIDs[elementType.ordinal()];
        } while (existingIdentifiers.contains(identifier));
        
        return identifier;
    }
    
    private GraphIdentifierGenerator assertAllIdsUnique(final EObject element) {
        Set<String> knownIds = Sets.newHashSet();
        Iterator<ElkGraphElement> elementIt = Iterators.filter(element.eAllContents(), ElkGraphElement.class);
        while (elementIt.hasNext()) {
            ElkGraphElement e = elementIt.next();
            while (knownIds.contains(e.getIdentifier())) {
                e.setIdentifier(e.getIdentifier() + "_");
            }
            knownIds.add(e.getIdentifier());
        }
        
        return this;
    }
    
}
