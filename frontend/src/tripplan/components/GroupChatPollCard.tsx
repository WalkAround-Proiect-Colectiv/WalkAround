import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Check, ListChecks, LockKeyhole } from 'lucide-react-native';
import type { GroupChatMessage } from '../api/groupChat';

interface Props {
  message: GroupChatMessage;
  own: boolean;
  busy: boolean;
  onVote: (optionId: string) => void;
  onClose: () => void;
}

export default function GroupChatPollCard({ message, own, busy, onVote, onClose }: Props) {
  const poll = message.poll;
  if (!poll) return null;
  const total = poll.options.reduce((sum, option) => sum + option.votes, 0);
  const closed = Boolean(poll.closedAt);
  const canVote = !closed && !busy;
  const voteHint = closed ? '' : poll.selectedOptionId
    ? ' · Tap another choice to change your vote'
    : ' · Choose one';

  return <View style={styles.card}>
    <View style={styles.eyebrow}>
      <ListChecks size={15} color="#0065EB" />
      <Text style={styles.eyebrowText}>GROUP POLL</Text>
      {closed && <View style={styles.closedBadge}><LockKeyhole size={11} color="#7B4A25" />
        <Text style={styles.closedText}>Closed</Text></View>}
    </View>
    {!own && <Text style={styles.sender}>{message.senderName}</Text>}
    <Text style={styles.question}>{message.body}</Text>
    <View style={styles.options}>
      {poll.options.map((option) => {
        const selected = poll.selectedOptionId === option.id;
        const canSelect = canVote && !selected;
        const percent = total ? Math.round(option.votes / total * 100) : 0;
        const progressWidth: `${number}%` = `${percent}%`;
        return <Pressable key={option.id} accessibilityRole="button"
          accessibilityLabel={`${option.label}, ${option.votes} votes`}
          accessibilityState={{ selected, disabled: !canSelect }} disabled={!canSelect}
          onPress={() => onVote(option.id)} style={[styles.option, selected && styles.selectedOption]}>
          <View style={[styles.progress, { width: progressWidth }]} />
          <View style={styles.optionContent}>
            <Text style={[styles.optionLabel, selected && styles.selectedLabel]} numberOfLines={2}>
              {option.label}
            </Text>
            <View style={styles.countRow}>
              {selected && <Check size={13} color="#0065EB" />}
              <Text style={styles.count}>{option.votes} · {percent}%</Text>
            </View>
          </View>
        </Pressable>;
      })}
    </View>
    <View style={styles.footer}>
      <Text style={styles.footerText}>{total} {total === 1 ? 'vote' : 'votes'}
        {voteHint}
        {' · '}{new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      </Text>
      {own && !closed && <Pressable accessibilityRole="button" disabled={busy} onPress={onClose}>
        <Text style={styles.closeText}>{busy ? 'Working…' : 'Close poll'}</Text>
      </Pressable>}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  card: { width: '100%', maxWidth: 450, alignSelf: 'center', padding: 16,
    borderRadius: 20, backgroundColor: '#fff', borderWidth: 1, borderColor: '#DDE8F7', gap: 11,
    shadowColor: '#253043', shadowOpacity: 0.06, shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 } },
  eyebrow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  eyebrowText: { color: '#0065EB', fontSize: 10, fontWeight: '800', letterSpacing: 0.8, flex: 1 },
  closedBadge: { flexDirection: 'row', alignItems: 'center', gap: 4,
    backgroundColor: '#FFF3E9', paddingHorizontal: 7, paddingVertical: 4, borderRadius: 9 },
  closedText: { color: '#7B4A25', fontSize: 10, fontWeight: '700' },
  sender: { color: '#23012C', fontSize: 11, fontWeight: '800' },
  question: { color: '#253043', fontSize: 16, fontWeight: '800', lineHeight: 22 },
  options: { gap: 7 },
  option: { overflow: 'hidden', minHeight: 44, borderRadius: 12,
    borderWidth: 1, borderColor: '#E2E9F3', backgroundColor: '#F7F9FC' },
  selectedOption: { borderColor: '#81B5FF' },
  progress: { position: 'absolute', left: 0, top: 0, bottom: 0, backgroundColor: '#E4F0FF' },
  optionContent: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 12, gap: 7 },
  optionLabel: { flex: 1, color: '#354257', fontSize: 13, fontWeight: '600' },
  selectedLabel: { color: '#0053C5', fontWeight: '800' },
  countRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  count: { color: '#627287', fontSize: 11, fontWeight: '700' },
  footer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  footerText: { color: '#8693A5', fontSize: 11 },
  closeText: { color: '#B12338', fontSize: 11, fontWeight: '800' },
});
