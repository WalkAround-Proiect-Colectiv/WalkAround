import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { GroupChatReply } from '../api/chatReplies';

interface Props {
  reply?: GroupChatReply | null;
  onNavigate?: (messageId: string) => void;
  disabled?: boolean;
}

export default function GroupChatQuote({ reply, onNavigate, disabled }: Props) {
  if (!reply) return null;
  const content = <>
    <Text numberOfLines={1} style={styles.sender}>{reply.senderName}</Text>
    <Text numberOfLines={2} style={styles.preview}>{reply.preview}</Text>
  </>;
  if (onNavigate) return <Pressable style={styles.quote} accessibilityRole="button"
    accessibilityLabel={`Go to original message from ${reply.senderName}: ${reply.preview}`}
    accessibilityHint="Loads earlier messages if needed" disabled={disabled}
    onPress={() => onNavigate(reply.messageId)}>
    {content}
  </Pressable>;
  return <View style={styles.quote} accessible accessibilityLabel={`Replying to ${reply.senderName}: ${reply.preview}`}>
    {content}
  </View>;
}

const styles = StyleSheet.create({
  quote: { borderLeftWidth: 3, borderLeftColor: '#0065EB', backgroundColor: '#EEF3FA',
    borderRadius: 4, paddingHorizontal: 10, paddingVertical: 8, gap: 3, minWidth: 0, flexShrink: 1 },
  sender: { color: '#0053C5', fontSize: 12, fontWeight: '700' },
  preview: { color: '#46566D', fontSize: 13, lineHeight: 18 },
});