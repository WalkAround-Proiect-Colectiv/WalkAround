# Group chat delivery

`/ws/group-chats?token=...` uses a short-lived JWT issued by the Application API only after group membership verification. `GroupChatHandshakeInterceptor` validates its signature, issuer, audience, expiry, `token_use=group_chat`, user ID, and `conversation_id`. A socket subscribes to exactly that conversation and receives a `SUBSCRIBED` acknowledgement. Chat writes over the socket are rejected; clients save through HTTP. Expired subscriptions are closed before another message is delivered, and clients obtain a fresh ticket when reconnecting.

`GroupChatSocketHandler` consumes `GROUP_CHAT_MESSAGE` records from `GROUP_CHAT_TOPIC`. The Kafka key is the conversation UUID, placing a conversation's messages in one partition while the topic supports multiple conversations in parallel. Every realtime-service instance uses its own Kafka consumer group so it can forward the event to its own connected clients. New groups start at the latest offset; missed messages are obtained from the Application API with an HTTP sequence cursor. The handler ignores duplicate sequence numbers per connection; the frontend also merges by message ID because an outbox retry can publish the same Kafka event again.

For each received event, the service logs its conversation ID, sequence, and number of local WebSocket connections reached. Compare this with the Application API's outbox publication log when investigating missing live updates. The frontend also performs periodic HTTP catch-up while connected, so a lost event does not require reopening chat.

This service stores no chat history and does not authorize HTTP history reads. The Application API owns membership checks and message storage. Do not put message bodies or socket tokens in logs.
