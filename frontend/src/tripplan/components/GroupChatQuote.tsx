import { StyleSheet, Text, View } from 'react-native';
import type { GroupChatReply } from '../api/chatReplies';

export default function GroupChatQuote({ reply }: { reply?: GroupChatReply | null }) {
  if (!reply) return null;
  return <View style={styles.quote} accessible accessibilityLabel={`Replying to ${reply.senderName}: ${reply.preview}`}>
    <Text numberOfLines={1} style={styles.sender}>{reply.senderName}</Text>
    <Text numberOfLines={2} style={styles.preview}>{reply.preview}</Text>
  </View>;
}

const styles = StyleSheet.create({
  quote: { borderLeftWidth: 3, borderLeftColor: '#0065EB', backgroundColor: '#EEF3FA',
    borderRadius: 4, paddingHorizontal: 10, paddingVertical: 8, gap: 3, minWidth: 0, flexShrink: 1 },
  sender: { color: '#0053C5', fontSize: 12, fontWeight: '700' },
  preview: { color: '#46566D', fontSize: 13, lineHeight: 18 },
});