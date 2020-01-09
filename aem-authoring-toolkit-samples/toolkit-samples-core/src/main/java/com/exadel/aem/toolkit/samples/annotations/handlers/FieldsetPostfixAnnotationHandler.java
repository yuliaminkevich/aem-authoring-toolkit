package com.exadel.aem.toolkit.samples.annotations.handlers;

import com.exadel.aem.toolkit.api.handlers.DialogWidgetHandler;
import com.exadel.aem.toolkit.samples.annotations.FieldsetPostfix;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;

public class FieldsetPostfixAnnotationHandler implements DialogWidgetHandler {

    @Override
    public String getName() {
        return "postfix";
    }

    @Override
    public void accept(Element element, Field field) {

        Element currentElement = null;
        String currentNodeName = null;
        String newNodeName = null;
        String postfix = field.getAnnotation(FieldsetPostfix.class).postfix();
        NodeList fieldsetNodes = element.getChildNodes().item(0).getChildNodes();
        int numOfNodes = fieldsetNodes.getLength();

        for (int i = 0; i < numOfNodes; ++i) {
            currentElement = (Element) fieldsetNodes.item(i);
            currentNodeName = currentElement.getAttribute("name");
            newNodeName = (currentNodeName != null) ? currentNodeName.concat(postfix) : null;
            currentElement.setAttribute("name", newNodeName);
        }
    }
}