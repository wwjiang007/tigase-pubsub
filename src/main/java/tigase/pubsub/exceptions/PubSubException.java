/*
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
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
package tigase.pubsub.exceptions;

import tigase.component.exceptions.ComponentException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * <p> Created: 2007-05-25 11:55:48 </p>
 *
 * @author bmalkow
 * @version $Rev$
 */
public class PubSubException
		extends ComponentException {

	private static final long serialVersionUID = 1L;

	private Authorization errorCondition;
	private Element item;
	private String message;
	private PubSubErrorCondition pubSubErrorCondition;
	private String xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas";

	public PubSubException(final Authorization errorCondition) {
		this(null, errorCondition, (String) null);
	}

	public PubSubException(final Authorization errorCondition, PubSubErrorCondition pubSubErrorConditions) {
		this((Element) null, errorCondition, pubSubErrorConditions);
	}

	public PubSubException(Authorization errorCondition, PubSubErrorCondition pubSubErrorConditions, String message) {
		this((Element) null, errorCondition, pubSubErrorConditions);
		this.message = message;
	}

	public PubSubException(final Authorization errorCondition, String message) {
		this(null, errorCondition, message);
	}

	public PubSubException(final Authorization errorCondition, String message, Exception ex) {
		this(null, errorCondition, message, ex);
	}

	public PubSubException(final Element item, final Authorization errorCondition) {
		this(item, errorCondition, (String) null);
	}

	public PubSubException(final Element item, final Authorization errorCondition,
						   PubSubErrorCondition pubSubErrorConditions) {
		this(item, errorCondition, (String) null);
		this.pubSubErrorCondition = pubSubErrorConditions;
	}

	public PubSubException(final Element item, final Authorization errorCondition, final String message) {
		this(item, errorCondition, message, null);
	}

	public PubSubException(final Element item, final Authorization errorCondition, final String message,
						   final Exception ex) {
		super(errorCondition);
		this.item = item;
		this.errorCondition = errorCondition;
		this.message = message;
	}

	@Override
	public String getCode() {
		return String.valueOf(this.errorCondition.getErrorCode());
	}

	@Override
	public Authorization getErrorCondition() {
		return errorCondition;
	}

	public Element getItem() {
		return item;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public String getName() {
		return errorCondition.getCondition();
	}

	public PubSubErrorCondition getPubSubErrorCondition() {
		return pubSubErrorCondition;
	}

	@Override
	public String getType() {
		return errorCondition.getErrorType();
	}

	public Element makeElement() {
		return makeElement(true);
	}

	public Element makeElement(boolean insertOriginal) {
		Element answer = insertOriginal ? item.clone() : new Element(item.getName());

		answer.addAttribute("id", item.getAttributeStaticStr("id"));
		answer.addAttribute("type", "error");
		answer.addAttribute("to", item.getAttributeStaticStr("from"));
		answer.addAttribute("from", item.getAttributeStaticStr("to"));
		if (this.message != null) {
			Element text = new Element("text", this.message, new String[]{"xmlns"},
									   new String[]{"urn:ietf:params:xml:ns:xmpp-stanzas"});

			answer.addChild(text);
		}
		answer.addChild(makeErrorElement());
		if (this.pubSubErrorCondition != null) {
			answer.addChild(this.pubSubErrorCondition.getElement());
		}

		return answer;
	}

	public Element makeElement(Element sourceElement) {
		this.item = sourceElement;

		return makeElement(true);
	}

	public Element makeErrorElement() {
		Element error = new Element("error");

		error.setAttribute("code", String.valueOf(this.errorCondition.getErrorCode()));
		error.setAttribute("type", this.errorCondition.getErrorType());
		error.addChild(new Element(this.errorCondition.getCondition(), new String[]{"xmlns"}, new String[]{xmlns}));

		return error;
	}
}
