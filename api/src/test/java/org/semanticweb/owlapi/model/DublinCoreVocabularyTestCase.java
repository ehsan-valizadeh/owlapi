/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */
package org.semanticweb.owlapi.model;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.vocab.DublinCoreVocabulary;
import org.semanticweb.owlapi.vocab.Namespaces;

/**
 * @author Matthew Horridge, Stanford University, Bio-Medical Informatics
 *         Research Group
 * @since 3.5.0
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class DublinCoreVocabularyTestCase {

    private final DublinCoreVocabulary vocabulary;

    public DublinCoreVocabularyTestCase(DublinCoreVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Nonnull
    @Parameterized.Parameters
    public static Collection<Object[]> getData() {
        List<Object[]> data = new ArrayList<>();
        for (DublinCoreVocabulary v : DublinCoreVocabulary.values()) {
            data.add(new Object[] { v });
        }
        return data;
    }

    @Test
    public void getPrefixedName_shouldStartWithDublinCorePrefixName() {
        assertThat(vocabulary.getPrefixedName(),
                startsWith(Namespaces.DC.getPrefixName()));
    }

    @Test
    public void getIRI_shouldReturnAnIRIThatStartsWithDublinCorePrefix() {
        assertThat(vocabulary.getIRI().toString(),
                startsWith(Namespaces.DC.getPrefixIRI()));
    }
}
