/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.data.managers.stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.jkiss.dbeaver.ui.data.IValueController;
import org.jkiss.dbeaver.ui.data.managers.AbstractTextPanelEditor;
import org.jkiss.dbeaver.ui.editors.xml.XMLEditor;
import org.jkiss.utils.CommonUtils;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

/**
* XMLPanelEditor
*/
public class XMLPanelEditor extends AbstractTextPanelEditor<XMLEditor> {

    @Override
    protected XMLEditor createEditorParty(IValueController valueController) {
        // Override init function because standard is VEEERY slow
        return new XMLEditor() {
            @Override
            public void init(IEditorSite site, IEditorInput input) throws PartInitException {
                setSite(site);
                try {
                    doSetInput(input);
                } catch (CoreException e) {
                    throw new PartInitException("Error initializing panel XML editor", e);
                }
            }
        };
    }

    @Override
    protected String getFileFolderName() {
        return "dbeaver-xml";
    }

    @Override
    protected String getFileExtension() {
        return ".xml";
    }

    @Override
    public String minify(String value) {
        try {
            final String cleanedInput = value.replaceAll(">\\s+<", "><").trim();

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            if (!value.contains("<?xml")) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Source src = new SAXSource(spf.newSAXParser().getXMLReader(), new InputSource(new StringReader(cleanedInput)));

            StreamResult result = new StreamResult(new StringWriter());
            transformer.transform(src, result);
            String resultString = result.getWriter().toString();
            if (CommonUtils.isEmpty(resultString)) {
                return value;
            }

            return resultString; // Replace all empty lines
        } catch (Throwable e) {
            return value;
        }
    }
}
