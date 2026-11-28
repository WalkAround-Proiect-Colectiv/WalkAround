import { useState } from 'react';
import { ActivityIndicator, Linking, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Download, FileText } from 'lucide-react-native';
import { getGroupChatDocumentUrl, type GroupChatMessage } from '../api/groupChat';
import GroupChatQuote from './GroupChatQuote';

export function documentSize(bytes: number): string {
  return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.ceil(bytes / 1024))} KB`;
}

const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  'application/pdf': 'PDF',
  'text/plain': 'TXT',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'DOCX',
  'text/csv': 'CSV',
};

export default function GroupChatDocumentCard({ groupId, message, own }: {
  groupId: string; message: GroupChatMessage; own: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attachment = message.document;
  if (!attachment) return null;

  async function download() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const { url } = await getGroupChatDocumentUrl(groupId, message.id);
      if (new URL(url).protocol !== 'https:') throw new Error('The download link is invalid.');
      if (Platform.OS === 'web') {
        const link = document.createElement('a');
        link.href = url;
        link.download = attachment!.fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
      } else {
        await Linking.openURL(url);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not download the document.');
    } finally { setBusy(false); }
  }

  return <View style={[styles.card, own && styles.own]}>
    {!own && <Text style={styles.sender}>{message.senderName}</Text>}
    <GroupChatQuote reply={message.replyTo} />
    <View style={styles.row}>
      <View style={styles.icon}><FileText size={25} color="#0065EB" /></View>
      <View style={styles.details}>
        <Text style={styles.name} numberOfLines={2}>{attachment.fileName}</Text>
        <Text style={styles.meta}>{DOCUMENT_TYPE_LABELS[attachment.contentType] ?? 'Document'} · {documentSize(attachment.sizeBytes)}</Text>
      </View>
    </View>
    {Boolean(message.body) && <Text style={styles.caption}>{message.body}</Text>}
    <Pressable accessibilityRole="button" accessibilityLabel={`Download ${attachment.fileName}`}
      disabled={busy} onPress={() => { void download(); }} style={styles.download}>
      {busy ? <ActivityIndicator size="small" color="#0065EB" /> : <Download size={17} color="#0065EB" />}
      <Text style={styles.downloadText}>{busy ? 'Preparing download…' : 'Download'}</Text>
    </Pressable>
    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    <Text style={styles.time}>{new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
  </View>;
}

const styles = StyleSheet.create({
  card: { alignSelf: 'flex-start', width: '84%', maxWidth: 360, padding: 13, gap: 9,
    borderRadius: 20, borderBottomLeftRadius: 6, backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF3' },
  own: { alignSelf: 'flex-end', backgroundColor: '#EAF3FF', borderBottomLeftRadius: 20, borderBottomRightRadius: 6 },
  sender: { color: '#23012C', fontWeight: '800', fontSize: 11 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  icon: { width: 44, height: 48, borderRadius: 12, backgroundColor: '#DDEBFF', alignItems: 'center', justifyContent: 'center' },
  details: { flex: 1, minWidth: 0 },
  name: { fontSize: 13, fontWeight: '700', color: '#253043' },
  meta: { fontSize: 11, color: '#65758B', marginTop: 4 },
  caption: { fontSize: 14, lineHeight: 21, color: '#253043' },
  download: { minHeight: 44, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: '#fff' },
  downloadText: { color: '#0065EB', fontSize: 12, fontWeight: '700' },
  time: { color: '#65758B', fontSize: 10, alignSelf: 'flex-end' },
  error: { color: '#C51B2D', fontSize: 12 },
});
