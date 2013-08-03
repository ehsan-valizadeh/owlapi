/*
 * This file is part of the OWL API.
 *
 * The contents of this file are subject to the LGPL License, Version 3.0.
 *
 * Copyright (C) 2011, The University of Manchester
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0
 * in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 *
 * Copyright 2011, The University of Manchester
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.manchester.cs.owl.explanation.ordering;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.semanticweb.owlapi.io.OWLObjectRenderer;
import org.semanticweb.owlapi.model.OWLAxiom;

/** Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 22-Jan-2008<br>
 * <br>
 * Orders an explanation in a flat list, sorting axioms alphabetically. */
public class AlphaExplanationOrderer implements ExplanationOrderer {
    protected final OWLObjectRenderer renderer;

    /** @param renderer
     *            the renderer to use */
    public AlphaExplanationOrderer(@Nonnull OWLObjectRenderer renderer) {
        this.renderer = checkNotNull(renderer);
    }

    @Override
    public ExplanationTree
            getOrderedExplanation(OWLAxiom entailment, Set<OWLAxiom> axioms) {
        EntailedAxiomTree root = new EntailedAxiomTree(entailment);
        List<OWLAxiom> sortedAxioms = new ArrayList<OWLAxiom>(axioms);
        Collections.sort(sortedAxioms, new Comparator<OWLAxiom>() {
            @Override
            public int compare(OWLAxiom o1, OWLAxiom o2) {
                return renderer.render(o1).compareTo(renderer.render(o2));
            }
        });
        for (OWLAxiom ax : sortedAxioms) {
            root.addChild(new ExplanationTree(ax));
        }
        return root;
    }
}
