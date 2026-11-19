import { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { getGroupChatImageUrl, type GroupChatMessage } from '../api/groupChat';

interface Props {
  groupId: string;
  message: GroupChatMessage;
  own: boolean;
}

export default function GroupChatImageCard({ groupId, message, own }: Props) {
  const [url, setUrl] = useState(message.image?.url ?? '');
  const [refreshing, setRefreshing] = useState(false);
  const [refreshed, setRefreshed] = useState(false);
  const [failed, setFailed] = useState(false);

  async function refreshUrl() {
    if (refreshing || failed) return;
    if (refreshed) {
      setFailed(true);
      return;
    }
    setRefreshed(true);
    setRefreshing(true);
    try {
      const image = await getGroupChatImageUrl(groupId, message.id);
      setUrl(image.url);
      setFailed(false);
    } catch {
      setFailed(true);
    } finally {
      setRefreshing(false);
    }
  }

  const ratio = message.image ? message.image.width / message.image.height : 1;
  const displayRatio = Math.min(2, Math.max(0.75, ratio));

  return <View style={[styles.card, own ? styles.own : styles.other]}>
    {!own && <Text style={styles.sender}>{message.senderName}</Text>}
    {failed ? <Text style={styles.missing}>Image unavailable</Text>
      : <Image source={{ uri: url }} resizeMode="contain"
        style={[styles.image, { aspectRatio: displayRatio }]}
        accessibilityLabel={message.body || 'Group chat image'} onError={() => { void refreshUrl(); }} />}
    {Boolean(message.body) && <Text style={own ? styles.ownCaption : styles.caption}>{message.body}</Text>}
    <Text style={own ? styles.ownTime : styles.time}>
      {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
    </Text>
  </View>;
}

const styles = StyleSheet.create({
  card: { width: '72%', maxWidth: 300, padding: 7, borderRadius: 18, gap: 6 },
  own: { alignSelf: 'flex-end', backgroundColor: '#0065EB', borderBottomRightRadius: 6 },
  other: { alignSelf: 'flex-start', backgroundColor: '#fff', borderWidth: 1,
    borderColor: '#E7ECF4', borderBottomLeftRadius: 6 },
  sender: { color: '#23012C', fontSize: 11, fontWeight: '800', marginHorizontal: 5 },
  image: { width: '100%', maxHeight: 320, borderRadius: 13, backgroundColor: '#E8EDF5' },
  caption: { color: '#253043', fontSize: 14, marginHorizontal: 5 },
  ownCaption: { color: '#fff', fontSize: 14, marginHorizontal: 5 },
  missing: { color: '#6C788B', fontSize: 12, margin: 10 },
  time: { color: '#8793A3', fontSize: 10, alignSelf: 'flex-end', marginHorizontal: 5 },
  ownTime: { color: '#D9E9FF', fontSize: 10, alignSelf: 'flex-end', marginHorizontal: 5 },
});
