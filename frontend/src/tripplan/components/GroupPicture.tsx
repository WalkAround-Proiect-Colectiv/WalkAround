import { useEffect, useState } from 'react';
import { Image, Platform, StyleSheet, View } from 'react-native';
import { UsersRound } from 'lucide-react-native';
import { getGroupInvitePicture, getTripGroupPicture } from '../api/tripGroups';

interface Props {
  groupId?: string;
  inviteToken?: string;
  size: number;
}

export default function GroupPicture({ groupId, inviteToken, size }: Props) {
  const [uri, setUri] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    let reader: FileReader | null = null;
    setUri(null);

    if (groupId || inviteToken) {
      const request = inviteToken ? getGroupInvitePicture(inviteToken) : getTripGroupPicture(groupId!);
      void request.then((blob) => {
        if (!active) return;
        if (Platform.OS === 'web') {
          objectUrl = URL.createObjectURL(blob);
          setUri(objectUrl);
          return;
        }
        reader = new FileReader();
        reader.onload = () => {
          if (active && typeof reader?.result === 'string') setUri(reader.result);
        };
        reader.readAsDataURL(blob);
      }).catch(() => { if (active) setUri(null); });
    }

    return () => {
      active = false;
      reader?.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [groupId, inviteToken]);

  return <View style={[styles.frame, { width: size, height: size, borderRadius: Math.round(size * 0.3) }]}>
    {uri ? <Image source={{ uri }} resizeMode="cover" accessibilityLabel="Group photo"
      style={{ width: size, height: size, borderRadius: Math.round(size * 0.3) }} />
      : <UsersRound size={Math.round(size * 0.48)} color="#0065EB" />}
  </View>;
}

const styles = StyleSheet.create({
  frame: { backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
});
