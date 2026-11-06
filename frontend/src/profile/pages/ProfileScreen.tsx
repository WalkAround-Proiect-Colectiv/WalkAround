import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Redirect, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, Check, ChevronRight, LogOut, Pencil, SlidersHorizontal, UserRound, X } from 'lucide-react-native';
import * as ImagePicker from 'expo-image-picker';
import { authColors as colors } from '../../auth/components/AuthLayout';
import { useSession } from '../../login/components/SessionProvider';
import { getCurrentUser, signOut, updateSessionUser } from '../../login/api/session';
import type { RegisteredUser } from '../../register/api/registration';
import { pictureSource, removePicture, saveProfile, uploadPicture, type ProfileFields } from '../api/profile';

const fields = [
  { name: 'username', label: 'Username', limit: 50, placeholder: 'Choose a username' },
  { name: 'firstName', label: 'First name', limit: 255, placeholder: 'Your first name' },
  { name: 'lastName', label: 'Last name', limit: 255, placeholder: 'Your last name' },
  { name: 'description', label: 'About you', limit: 2000, placeholder: 'Tell us a little about yourself' },
] as const;
const profileFields = (user: RegisteredUser): ProfileFields => ({ username: user.username ?? '', firstName: user.firstName ?? '', lastName: user.lastName ?? '', description: user.description ?? '' });

export default function ProfileScreen() {
  const { session, ready } = useSession();
  if (!ready) return <View style={styles.center}><ActivityIndicator color={colors.primary} accessibilityLabel="Loading account" /></View>;
  if (!session) return <Redirect href="/login" />;
  return <ProfileContent key={session.user.id} />;
}

function ProfileContent() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [profile, setProfile] = useState<RegisteredUser | null>(null);
  const [form, setForm] = useState<ProfileFields>({ username: '', firstName: '', lastName: '', description: '' });
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photo, setPhoto] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [leaveAction, setLeaveAction] = useState<'map' | 'logout' | null>(null);
  const [attempt, setAttempt] = useState(0);
  const dirty = Boolean(profile && JSON.stringify(form) !== JSON.stringify(profileFields(profile)));
  const disabled = busy || photoBusy;

  useEffect(() => {
    let active = true;
    getCurrentUser().then(user => {
      if (active) { setProfile(user); setForm(profileFields(user)); updateSessionUser(user); }
    }).catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load your profile.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);

  useEffect(() => {
    let active = true;
    pictureSource(profile?.profilePictureUrl ?? null).then(source => {
      if (active) setPhoto(source);
    }).catch(() => { if (active) { setPhoto(null); setPhotoError('Could not load your photo. You can still edit your profile.'); } });
    return () => { active = false; };
  }, [profile?.profilePictureUrl]);

  const leave = useCallback(async (action: 'map' | 'logout') => {
    setLeaveAction(null);
    if (action === 'map') { router.replace('/map'); return; }
    setBusy(true);
    setError(null);
    try { await signOut(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not sign out.'); }
    finally { setBusy(false); }
  }, [router]);

  function requestLeave(action: 'map' | 'logout') {
    if (editing && dirty) setLeaveAction(action);
    else void leave(action);
  }

  async function save() {
    if (disabled) return;
    const username = form.username?.trim();
    if (username && !/^[A-Za-z0-9_]{3,50}$/.test(username)) {
      setError('Username must be 3–50 letters, numbers or underscores.');
      return;
    }
    setBusy(true); setError(null); setNotice(null);
    try {
      const updated = await saveProfile(form);
      setProfile(updated); setForm(profileFields(updated)); setEditing(false); setNotice('Profile saved.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not save your profile.'); }
    finally { setBusy(false); }
  }

  async function changePhoto(remove = false) {
    if (disabled) return;
    setPhotoBusy(true); setPhotoError(null); setNotice(null);
    try {
      let updated: RegisteredUser;
      if (remove) updated = await removePicture();
      else {
        const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.85 });
        if (result.canceled) return;
        updated = await uploadPicture(result.assets[0]);
      }
      setProfile(updated); setNotice(remove ? 'Profile photo removed.' : 'Profile photo saved.');
    } catch (reason) { setPhotoError(reason instanceof Error ? reason.message : 'Could not update your photo.'); }
    finally { setPhotoBusy(false); }
  }

  return <View style={[styles.screen, { paddingTop: insets.top }]}>
    <StatusBar style="dark" />
    <View style={styles.panel}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to map" disabled={disabled} onPress={() => requestLeave('map')} style={styles.iconButton}><ArrowLeft color={colors.text} size={24} /></Pressable>
        <Text accessibilityRole="header" style={styles.heading}>{editing ? 'Edit profile' : 'Account'}</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="Close account" disabled={disabled} onPress={() => requestLeave('map')} style={styles.iconButton}><X color={colors.text} size={24} /></Pressable>
      </View>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 24) }]}>
        {loading ? <ActivityIndicator color={colors.primary} accessibilityLabel="Loading profile" /> : !profile ? <>
          <Text accessibilityRole="alert" style={styles.error}>{error}</Text>
          <Pressable accessibilityRole="button" onPress={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} style={styles.primaryButton}><Text style={styles.primaryText}>Retry</Text></Pressable>
        </> : <>
          <View style={styles.identity}>
            <View style={styles.avatarWrap}>
              <View style={styles.avatar}>{photo ? <Image source={{ uri: photo }} accessibilityLabel="Profile photo" style={styles.photo} onError={() => { setPhoto(null); setPhotoError('Could not load your photo.'); }} /> : <UserRound size={60} color={colors.muted} />}</View>
              <Pressable accessibilityRole="button" accessibilityLabel="Change profile photo" accessibilityState={{ disabled, busy: photoBusy }} disabled={disabled} onPress={() => void changePhoto()} style={styles.editPhoto}>
                {photoBusy ? <ActivityIndicator color={colors.primary} /> : <Pencil size={22} color={colors.primary} />}
              </Pressable>
            </View>
            <Text style={styles.displayName}>{profile.username ? '@' + profile.username : [profile.firstName, profile.lastName].filter(Boolean).join(' ') || 'Your profile'}</Text>
            {profile.profilePictureUrl && <Pressable accessibilityRole="button" disabled={disabled} onPress={() => void changePhoto(true)} style={styles.textButton}><Text style={styles.link}>Remove photo</Text></Pressable>}
            <Text style={styles.hint}>Choose a JPEG or PNG photo. Photo changes save immediately.</Text>
            {photoError && <Text accessibilityRole="alert" style={styles.error}>{photoError}</Text>}
          </View>
          {notice && <View accessibilityLiveRegion="polite" style={styles.success}><Check size={18} color={colors.primary} /><Text style={styles.successText}>{notice}</Text></View>}
          {fields.map(field => <View key={field.name} style={styles.field}>
            <Text nativeID={'label-' + field.name} style={styles.label}>{field.label}</Text>
            {editing ? <>
              <TextInput accessibilityLabel={field.label} accessibilityLabelledBy={'label-' + field.name} value={form[field.name] ?? ''} editable={!disabled}
                onChangeText={value => setForm(current => ({ ...current, [field.name]: value }))}
                placeholder={field.placeholder} placeholderTextColor="#767d87" maxLength={field.limit}
                autoCapitalize={field.name === 'username' ? 'none' : 'sentences'} autoCorrect={field.name !== 'username'}
                multiline={field.name === 'description'} style={[styles.input, field.name === 'description' && styles.descriptionInput]} />
              {field.name === 'username' && <Text style={styles.hint}>Optional · 3–50 letters, numbers or underscores.</Text>}
              {field.name === 'description' && <Text style={styles.counter}>{form.description?.length ?? 0}/2000</Text>}
            </> : <Text style={[styles.value, !profile[field.name] && styles.empty]}>{profile[field.name] || 'Not set'}</Text>}
          </View>)}
          {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
          {editing ? <View style={styles.actions}>
            <Pressable accessibilityRole="button" disabled={disabled} onPress={() => { setForm(profileFields(profile)); setEditing(false); setError(null); }} style={[styles.secondaryButton, disabled && styles.disabled]}><Text style={styles.link}>Cancel</Text></Pressable>
            <Pressable accessibilityRole="button" disabled={disabled || !dirty} onPress={() => void save()} style={[styles.primaryButton, (disabled || !dirty) && styles.disabled]}><Text style={styles.primaryText}>{busy ? 'Saving…' : 'Save changes'}</Text></Pressable>
          </View> : <Pressable accessibilityRole="button" disabled={disabled} onPress={() => { setEditing(true); setNotice(null); setError(null); }} style={[styles.primaryButton, disabled && styles.disabled]}><Pencil size={18} color="#fff" /><Text style={styles.primaryText}>Edit profile</Text></Pressable>}
          {!editing && <Pressable accessibilityRole="button" accessibilityLabel="Preferences" disabled={disabled}
            onPress={() => router.push('/preferences')} style={[styles.preferences, disabled && styles.disabled]}>
            <SlidersHorizontal size={20} color={colors.primary} /><Text style={[styles.link, { flex: 1 }]}>Preferences</Text><ChevronRight size={20} color={colors.primary} />
          </Pressable>}
          <View style={styles.divider} />
          <Pressable accessibilityRole="button" accessibilityLabel="Sign out" disabled={disabled} onPress={() => requestLeave('logout')} style={[styles.logout, disabled && styles.disabled]}><LogOut size={20} color={colors.danger} /><Text style={styles.logoutText}>{busy && !editing ? 'Signing out…' : 'Sign out'}</Text></Pressable>
        </>}
      </ScrollView>
    </View>
    <Modal transparent visible={leaveAction !== null} animationType="fade" onRequestClose={() => setLeaveAction(null)}>
      <View style={styles.modalBackdrop}><View style={styles.dialog}>
        <Text accessibilityRole="header" style={styles.heading}>Discard your changes?</Text>
        <Text style={styles.value}>Your unsaved profile details will be lost.</Text>
        <Pressable accessibilityRole="button" onPress={() => setLeaveAction(null)} style={styles.primaryButton}><Text style={styles.primaryText}>Keep editing</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => { if (leaveAction) void leave(leaveAction); }} style={styles.textButton}><Text style={styles.logoutText}>Discard changes</Text></Pressable>
      </View></View>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  preferences: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 52, padding: 14, borderWidth: 1, borderColor: colors.border, borderRadius: 14 },
  screen: { flex: 1, backgroundColor: colors.background, alignItems: 'center' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' },
  panel: { flex: 1, width: '100%', maxWidth: 560, backgroundColor: '#fff', borderTopLeftRadius: 24, borderTopRightRadius: 24, overflow: 'hidden' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 12, paddingVertical: 12, borderBottomWidth: 1, borderColor: '#eef0f3' },
  heading: { fontSize: 20, fontWeight: '700', color: colors.text },
  iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 },
  content: { padding: 20, gap: 18 },
  identity: { alignItems: 'center', gap: 8, paddingTop: 8, paddingBottom: 12 },
  avatarWrap: { width: 112, height: 112, marginBottom: 8 },
  avatar: { width: 112, height: 112, borderRadius: 56, borderWidth: 4, borderColor: '#e7e9ed', backgroundColor: '#f3f4f6', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  photo: { width: '100%', height: '100%' },
  editPhoto: { position: 'absolute', right: -8, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  displayName: { fontSize: 22, lineHeight: 30, fontWeight: '700', color: colors.text, textAlign: 'center' },
  field: { gap: 8 },
  label: { fontSize: 14, fontWeight: '600', color: colors.muted },
  value: { fontSize: 16, lineHeight: 24, color: colors.text, padding: 14, borderWidth: 1, borderColor: colors.border, borderRadius: 14 },
  empty: { color: colors.muted },
  input: { minHeight: 48, paddingHorizontal: 14, paddingVertical: 12, borderWidth: 1, borderColor: colors.border, borderRadius: 14, backgroundColor: '#f8f9fb', fontSize: 16, lineHeight: 24, color: colors.text },
  descriptionInput: { minHeight: 128, textAlignVertical: 'top' },
  hint: { fontSize: 12, lineHeight: 18, color: colors.muted },
  counter: { fontSize: 12, color: colors.muted, textAlign: 'right' },
  primaryButton: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 14, backgroundColor: colors.primary },
  primaryText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  secondaryButton: { minHeight: 48, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 14, borderWidth: 1, borderColor: colors.border, justifyContent: 'center', alignItems: 'center' },
  textButton: { minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  link: { color: colors.primary, fontSize: 15, fontWeight: '600' },
  actions: { flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 },
  divider: { height: 1, backgroundColor: '#eef0f3', marginTop: 6 },
  logout: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderWidth: 1, borderColor: colors.danger, borderRadius: 14 },
  logoutText: { color: '#a60000', fontSize: 15, fontWeight: '600' },
  disabled: { opacity: 0.5 },
  error: { color: '#a60000', backgroundColor: '#fff5f5', padding: 12, borderRadius: 12, fontSize: 14, lineHeight: 21 },
  success: { flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.blueTint, padding: 12, borderRadius: 12 },
  successText: { color: colors.primary, fontSize: 14, flexShrink: 1 },
  modalBackdrop: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24, backgroundColor: 'rgba(0,0,0,0.35)' },
  dialog: { width: '100%', maxWidth: 380, padding: 24, gap: 20, backgroundColor: '#fff', borderRadius: 24 },
});
