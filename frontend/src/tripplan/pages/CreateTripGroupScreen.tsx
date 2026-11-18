import { useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Redirect, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import type { ImagePickerAsset } from 'expo-image-picker';
import { ArrowLeft, Camera, Check, UsersRound, X } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { createTripGroup, type CreatedTripGroup } from '../api/tripGroups';

const BLUE = '#0065EB';
const INK = '#202632';

export default function CreateTripGroupScreen() {
  const { session, ready } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [picture, setPicture] = useState<ImagePickerAsset | null>(null);
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreatedTripGroup | null>(null);

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  async function choosePicture() {
    if (submitting.current || created) return;
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
      setError(null);
    } catch {
      setError('Could not open your photos. Please try again.');
    }
  }

  async function save() {
    if (submitting.current || created) return;
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
      setCreated(await createTripGroup(groupName, groupDescription || null, picture));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not create the group. Try again.');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }

  return <View style={styles.screen}>
    <ScrollView keyboardShouldPersistTaps="handled"
      contentContainerStyle={[styles.scroll, {
        paddingTop: Math.max(insets.top, 16), paddingBottom: Math.max(insets.bottom, 24) + 24,
      }]}>
      <View style={styles.content}>
        <View style={styles.topBar}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to trip planner"
            onPress={() => router.push('/trip-plan')} style={styles.backButton}>
            <ArrowLeft size={21} color={INK} />
          </Pressable>
          <Text style={styles.brand}>WalkAround</Text>
          <View style={styles.spacer} />
        </View>

        <View style={styles.heading}>
          <View style={styles.icon}><UsersRound size={25} color={BLUE} /></View>
          <Text accessibilityRole="header" style={styles.title}>
            {created ? 'Your group is ready' : 'Create a trip group'}
          </Text>
          <Text style={styles.subtitle}>
            {created ? 'Your group has been created, and you are its admin.'
              : 'Give your group a name and an optional photo to get started.'}
          </Text>
        </View>

        {created ? <View style={styles.card}>
          {picture && <Image source={{ uri: picture.uri }} style={styles.photo} accessibilityLabel="Group photo" />}
          <Text style={styles.createdName}>{created.name}</Text>
          {created.description && <Text style={styles.subtitle}>{created.description}</Text>}
          <View style={styles.roleBadge}><Check size={16} color={BLUE} /><Text style={styles.roleText}>You are an admin</Text></View>
          <Pressable accessibilityRole="button" onPress={() => router.push('/trip-plan')}
            style={styles.primaryButton}><Text style={styles.primaryText}>Back to trip planner</Text></Pressable>
        </View> : <View style={styles.card}>
          <Text style={styles.label}>Group name</Text>
          <TextInput accessibilityLabel="Group name" value={name} maxLength={100}
            onChangeText={setName} autoCapitalize="sentences" placeholder="e.g. Summer trip crew"
            placeholderTextColor="#8892A2" style={styles.input} />

          <Text style={styles.label}>Description <Text style={styles.optional}>(optional)</Text></Text>
          <TextInput accessibilityLabel="Group description" value={description} maxLength={1000}
            onChangeText={setDescription} multiline textAlignVertical="top"
            placeholder="What are you planning together?" placeholderTextColor="#8892A2"
            style={[styles.input, styles.description]} />

          <Text style={styles.label}>Group photo <Text style={styles.optional}>(optional)</Text></Text>
          <Pressable accessibilityRole="button" onPress={() => { void choosePicture(); }} style={styles.photoButton}>
            {picture ? <Image source={{ uri: picture.uri }} style={styles.photoPreview} accessibilityLabel="Selected group photo" />
              : <Camera size={24} color={BLUE} />}
            <Text style={styles.photoText}>{picture ? 'Change photo' : 'Choose a photo'}</Text>
          </Pressable>
          {picture && <Pressable accessibilityRole="button" onPress={() => setPicture(null)}
            style={styles.removeButton}><X size={15} color="#6A7380" /><Text style={styles.removeText}>Remove photo</Text></Pressable>}
          <Text style={styles.hint}>JPEG or PNG, up to 2 MB. Your photo is stored with the group.</Text>

          {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
          <Pressable accessibilityRole="button" disabled={busy} onPress={() => { void save(); }}
            style={[styles.primaryButton, busy && styles.disabled]}>
            {busy ? <ActivityIndicator color="#fff" /> : <UsersRound size={19} color="#fff" />}
            <Text style={styles.primaryText}>{busy ? 'Creating group…' : 'Create group'}</Text>
          </Pressable>
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
  createdName: { color: INK, fontSize: 22, fontWeight: '800', textAlign: 'center' },
  photo: { width: 90, height: 90, borderRadius: 28, alignSelf: 'center' },
  roleBadge: { flexDirection: 'row', alignSelf: 'center', alignItems: 'center', gap: 7,
    backgroundColor: '#EAF3FF', borderRadius: 15, paddingHorizontal: 12, paddingVertical: 8 },
  roleText: { color: BLUE, fontWeight: '700', fontSize: 13 },
});
