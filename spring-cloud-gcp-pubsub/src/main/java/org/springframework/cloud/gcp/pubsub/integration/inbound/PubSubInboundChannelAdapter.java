/*
 *  Copyright 2017-2018 original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.gcp.pubsub.integration.inbound;

import java.util.Map;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;

import org.springframework.cloud.gcp.pubsub.core.PubSubOperations;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.PubSubHeaderMapper;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.mapping.HeaderMapper;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.Assert;

/**
 * Converts from GCP Pub/Sub message to Spring message and sends the Spring message to the
 * attached channels.
 *
 * @author João André Martins
 * @author Mike Eltsufin
 */
public class PubSubInboundChannelAdapter extends MessageProducerSupport {

	private final String subscriptionName;

	private final PubSubOperations pubSubTemplate;

	private Subscriber subscriber;

	private AckMode ackMode = AckMode.AUTO;

	private MessageConverter messageConverter;

	private HeaderMapper<Map<String, String>> headerMapper = new PubSubHeaderMapper();

	public PubSubInboundChannelAdapter(PubSubOperations pubSubTemplate, String subscriptionName) {
		this.pubSubTemplate = pubSubTemplate;
		this.subscriptionName = subscriptionName;
	}

	@Override
	protected void doStart() {
		super.doStart();

		this.subscriber =
				this.pubSubTemplate.subscribe(this.subscriptionName, this::receiveMessage);
	}

	private void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer consumer) {
		Map<String, Object> messageHeaders =
				this.headerMapper.toHeaders(pubsubMessage.getAttributesMap());

		if (this.ackMode == AckMode.MANUAL) {
			// Send the consumer downstream so user decides on when to ack/nack.
			messageHeaders.put(GcpPubSubHeaders.ACKNOWLEDGEMENT, consumer);
		}

		try {
			Message message;
			if (this.messageConverter != null) {
				message = this.messageConverter.toMessage(pubsubMessage.getData().toByteArray(),
						new MessageHeaders(messageHeaders));
			}
			else {
				message = MessageBuilder.withPayload(pubsubMessage.getData().toByteArray())
						.copyHeaders(messageHeaders).build();
			}
			sendMessage(message);
		}
		catch (RuntimeException re) {
			if (this.ackMode == AckMode.AUTO) {
				consumer.nack();
			}
			throw re;
		}

		if (this.ackMode == AckMode.AUTO) {
			consumer.ack();
		}
	}

	@Override
	protected void doStop() {
		if (this.subscriber != null) {
			this.subscriber.stopAsync();
		}

		super.doStop();
	}

	public AckMode getAckMode() {
		return this.ackMode;
	}

	public void setAckMode(AckMode ackMode) {
		Assert.notNull(ackMode, "The acknowledgement mode can't be null.");
		this.ackMode = ackMode;
	}

	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

	/**
	 * Set the {@link MessageConverter} to convert an incoming Pub/Sub message payload to an
	 * {@code Object}. If the converter is null, the payload is unchanged.
	 * @param messageConverter converts a {@link PubsubMessage} payload to a
	 * {@link org.springframework.messaging.Message} payload. Can be set to null.
	 */
	public void setMessageConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	/**
	 * Set the header mapper to map headers from incoming {@link PubsubMessage} into
	 * {@link org.springframework.messaging.Message}.
	 * @param headerMapper the header mapper
	 */
	public void setHeaderMapper(HeaderMapper<Map<String, String>> headerMapper) {
		Assert.notNull(headerMapper, "The header mapper can't be null.");
		this.headerMapper = headerMapper;
	}
}
