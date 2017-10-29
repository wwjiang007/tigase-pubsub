/*
 * XsltTool.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

package tigase.pubsub.modules;

import tigase.kernel.beans.Bean;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@Bean(name = "xslTransformer", parent = PubSubComponent.class, active = true)
public class XsltTool {

	private final SimpleParser parser = SingletonFactory.getParserInstance();

	private TransformerFactory tFactory = TransformerFactory.newInstance();

	public XsltTool() {

	}

	public List<Element> transform(final Element item, AbstractNodeConfig nodeConfig)
			throws TransformerException, IOException {
		Source xsltSource;
		final String bodyXsltUrl = nodeConfig.getBodyXslt();
		final String bodyXsltEmbedded = nodeConfig.getBodyXsltEmbedded();
		if (bodyXsltEmbedded != null && bodyXsltEmbedded.length() > 1) {
			Reader reader = new StringReader(bodyXsltEmbedded);
			xsltSource = new StreamSource(reader);
		} else if (bodyXsltUrl != null && bodyXsltUrl.length() > 1) {
			URL x = new URL(bodyXsltUrl);
			xsltSource = new StreamSource(x.openStream());
		} else {
			return null;
		}
		return transform(item, xsltSource);
	}

	private List<Element> transform(final Element item, Source xslt) throws TransformerException {
		Transformer transformer = tFactory.newTransformer(xslt);
		Reader reader = new StringReader(item.toString());

		StringWriter writer = new StringWriter();
		transformer.transform(new StreamSource(reader), new StreamResult(writer));
		char[] data = writer.toString().toCharArray();

		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);
		Queue<Element> q = domHandler.getParsedElements();

		ArrayList<Element> result = new ArrayList<Element>();
		result.addAll(q);
		return result;
	}

}
