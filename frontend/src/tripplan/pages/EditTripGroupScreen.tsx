import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import type { ImagePickerAsset } from 'expo-image-picker';
import { ArrowLeft, Camera, Save, UsersRound, X } from 'lucide-react-native';
import { environment } from '../../config/environment';
import { useSession } from '../../login/components/SessionProvider';
import { getTripGroup, getTripGroupPicture, updateTripGroup, type CreatedTripGroup } from '../api/tripGroups';

const BLUE = '#0065EB';
const INK = '#202632';

export default function EditTripGroupScreen() {
  const { session, ready } = useSession();
  const userId = session?.user.id;
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ groupId?: string }>();
  const groupId = typeof params.groupId === 'string' ? params.groupId : null;
  const [group, setGroup] = useState<CreatedTripGroup | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [picture, setPicture] = useState<ImagePickerAsset | null>(null);
  const [currentPhotoUri, setCurrentPhotoUri] = useState<string | null>(null);
  const [removePicture, setRemovePicture] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userId || !groupId) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setGroup(null);
    setError(null);
    void getTripGroup(groupId)
      .then((result) => {
        if (!active) return;
        setGroup(result);
        setName(result.name);
        setDescription(result.description ?? '');
      })
      .catch((reason) => {
        if (active) setError(reason instanceof Error ? reason.message : 'Could not load the group.');
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, groupId]);

  useEffect(() => {
    if (Platform.OS !== 'web' || !group?.pictureUrl) return;
    let active = true;
    let objectUrl: string | null = null;
    setCurrentPhotoUri(null);
    void getTripGroupPicture(group.id).then((blob) => {
      objectUrl = URL.createObjectURL(blob);
      if (active) setCurrentPhotoUri(objectUrl);
      else URL.revokeObjectURL(objectUrl);
    }).catch(() => {
      if (active) setCurrentPhotoUri(null);
    });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [group?.id, group?.pictureUrl]);

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  function goBack() {
    router.replace(groupId
      ? { pathname: '/trip-groups', params: { groupId } }
      : '/trip-groups');
  }

  async function choosePicture() {
    if (submitting.current) return;
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.85,
      });
      if (result.canceled) return;
      const selected = result.assets[0];
      const mimeType = selected.file?.type || selected.mimeType;
      if (mimeType && mimeType !== 'image/jpeg' && mimeType !== 'image/png') {
        setError('Choose a JPEG or PNG photo.');
        return;
      }
      if (selected.fileSize && selected.fileSize > 2_097_152) {
        setError('Choose a photo smaller than 2 MB.');
        return;
      }
      setPicture(selected);
      setRemovePicture(false);
      setError(null);
    } catch {
      setError('Could not open your photos. Please try again.');
    }
  }

  async function save() {
    if (submitting.current || !groupId || group?.memberRole !== 'ADMIN') return;
    const groupName = name.trim();
    const groupDescription = description.trim();
    if (!groupName || groupName.length > 100 || /[\u0000-\u001F\u007F]/.test(groupName)) {
      setError('Enter a group name of up to 100 characters.');
      return;
    }
    if (groupDescription.length > 1000
        || /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(groupDescription)) {
      setError('The description is too long or contains invalid characters.');
      return;
    }

    submitting.current = true;
    setBusy(true);
    setError(null);
    try {
      await updateTripGroup(groupId, groupName, groupDescription || null, picture, removePicture);
      goBack();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not update the group. Try again.');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }

  const currentPicture = group?.pictureUrl && !removePicture && !picture
    ? Platform.OS === 'web'
      ? currentPhotoUri ? { uri: currentPhotoUri } : null
      : { uri: environment.apiBaseUrl + group.pictureUrl,
        headers: { Authorization: `Bearer ${session.accessToken}` } }
    : null;

  return <View style={styles.screen}>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={[styles.scroll, {
      paddingTop: Math.max(insets.top, 16), paddingBottom: Math.max(insets.bottom, 24) + 24,
    }]}>
      <View style={styles.content}>
        <View style={styles.topBar}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to group"
            onPress={goBack} style={styles.backButton}><ArrowLeft size={21} color={INK} /></Pressable>
          <Text style={styles.brand}>WalkAround</Text><View style={styles.spacer} />
        </View>
        <View style={styles.heading}>
          <View style={styles.icon}><UsersRound size={25} color={BLUE} /></View>
          <Text accessibilityRole="header" style={styles.title}>Edit group</Text>
          <Text style={styles.subtitle}>Update the details your group members see.</Text>
        </View>
        {loading ? <ActivityIndicator color={BLUE} /> : group?.memberRole === 'ADMIN' ? <View style={styles.card}>
          <Text style={styles.label}>Group name</Text>
          <TextInput accessibilityLabel="Group name" value={name} maxLength={100} onChangeText={setName}
            autoCapitalize="sentences" placeholder="Group name" placeholderTextColor="#8892A2" style={styles.input} />
          <Text style={styles.label}>Description <Text style={styles.optional}>(optional)</Text></Text>
          <TextInput accessibilityLabel="Group description" value={description} maxLength={1000}
            onChangeText={setDescription} multiline textAlignVertical="top" placeholder="What are you planning together?"
            placeholderTextColor="#8892A2" style={[styles.input, styles.description]} />
          <Text style={styles.label}>Group photo <Text style={styles.optional}>(optional)</Text></Text>
          <Pressable accessibilityRole="button" onPress={() => { void choosePicture(); }} style={styles.photoButton}>
            {picture ? <Image source={{ uri: picture.uri }} style={styles.photoPreview} accessibilityLabel="New group photo" />
              : currentPicture ? <Image source={currentPicture} style={styles.photoPreview} accessibilityLabel="Current group photo" />
                : <Camera size={24} color={BLUE} />}
            <Text style={styles.photoText}>{picture || currentPicture ? 'Change photo' : 'Choose a photo'}</Text>
          </Pressable>
          {(picture || currentPicture) && <Pressable accessibilityRole="button" onPress={() => {
            setPicture(null);
            setRemovePicture(true);
          }} style={styles.removeButton}>
            <X size={15} color="#6A7380" /><Text style={styles.removeText}>Remove photo</Text>
          </Pressable>}
          <Text style={styles.hint}>JPEG or PNG, up to 2 MB.</Text>
          {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
          <Pressable accessibilityRole="button" disabled={busy} onPress={() => { void save(); }}
            style={[styles.primaryButton, busy && styles.disabled]}>
            {busy ? <ActivityIndicator color="#fff" /> : <Save size={18} color="#fff" />}
            <Text style={styles.primaryText}>{busy ? 'Saving…' : 'Save changes'}</Text>
          </Pressable>
        </View> : <View style={styles.card}>
          <Text accessibilityRole="alert" style={styles.error}>
            {error ?? (group ? 'Only group admins can edit this group.' : 'Group was not found.')}
          </Text>
        </View>}
      </View>
    </ScrollView>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F7FB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  scroll: { paddingHorizontal: 16 },
  content: { width: '100%', maxWidth: 560, alignSelf: 'center', gap: 20 },
  topBar: { height: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  backButton: { width: 42, height: 42, backgroundColor: '#fff', borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  brand: { color: BLUE, fontSize: 18, fontWeight: '800' },
  spacer: { width: 42 },
  heading: { alignItems: 'center', gap: 10, paddingTop: 18 },
  icon: { width: 58, height: 58, borderRadius: 20, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  title: { color: INK, fontSize: 27, fontWeight: '800', textAlign: 'center' },
  subtitle: { color: '#697587', fontSize: 14, lineHeight: 20, textAlign: 'center' },
  card: { backgroundColor: '#fff', borderRadius: 24, padding: 20, gap: 12, borderWidth: 1, borderColor: '#E9EEF4' },
  label: { color: INK, fontSize: 14, fontWeight: '700', marginTop: 4 },
  optional: { color: '#788596', fontWeight: '400' },
  input: { minHeight: 50, borderWidth: 1, borderColor: '#DCE4EE', borderRadius: 14, paddingHorizontal: 14,
    backgroundColor: '#FBFCFE', color: INK, fontSize: 15, outlineWidth: 0 },
  description: { minHeight: 100, paddingTop: 13, paddingBottom: 13 },
  photoButton: { minHeight: 75, borderRadius: 15, backgroundColor: '#F1F7FF', borderWidth: 1,
    borderColor: '#D5E5FA', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 13 },
  photoPreview: { width: 48, height: 48, borderRadius: 15 },
  photoText: { color: BLUE, fontWeight: '800', fontSize: 14 },
  removeButton: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', gap: 5 },
  removeText: { color: '#6A7380', fontSize: 13 },
  hint: { color: '#7A8595', fontSize: 12, lineHeight: 17 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  primaryButton: { minHeight: 52, backgroundColor: BLUE, borderRadius: 16, flexDirection: 'row',
    alignItems: 'center', justifyContent: 'center', gap: 9, marginTop: 8 },
  primaryText: { color: '#fff', fontWeight: '800', fontSize: 15 },
  disabled: { opacity: 0.5 },
});
