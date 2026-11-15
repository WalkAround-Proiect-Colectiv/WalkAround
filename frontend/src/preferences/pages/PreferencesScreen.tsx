import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Redirect, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, Check, SlidersHorizontal, X } from 'lucide-react-native';
import { authColors as colors } from '../../auth/components/AuthLayout';
import { useSession } from '../../login/components/SessionProvider';
import { getPreferences, savePreferences, type Preferences } from '../api/preferences';

const ordered = (ids: string[]) => [...ids].sort();

export default function PreferencesScreen() {
  const { session, ready } = useSession();
  if (!ready) return <View style={styles.center}><ActivityIndicator color={colors.primary} accessibilityLabel="Restoring session" /></View>;
  if (!session) return <Redirect href="/login" />;
  return <PreferencesContent key={session.user.id} />;
}

function PreferencesContent() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [preferences, setPreferences] = useState<Preferences | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const dirty = preferences !== null && JSON.stringify(ordered(selected)) !== JSON.stringify(ordered(preferences.selectedOptionIds));

  useEffect(() => {
    let active = true;
    getPreferences().then(result => {
      if (active) { setPreferences(result); setSelected(result.selectedOptionIds); }
    }).catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load your preferences.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);

  function leave() {
    if (saving.current) return;
    if (dirty) setConfirmLeave(true);
    else router.replace('/home');
  }

  function toggle(id: string) {
    if (saving.current) return;
    setSelected(current => current.includes(id) ? current.filter(value => value !== id) : [...current, id]);
    setSaved(false); setError(null);
  }

  function cancel() {
    if (saving.current || !preferences) return;
    setSelected(preferences.selectedOptionIds); setSaved(false); setError(null);
  }

  async function save() {
    if (saving.current || !dirty) return;
    saving.current = true; setBusy(true); setSaved(false); setError(null);
    try {
      const result = await savePreferences(selected);
      setPreferences(result); setSelected(result.selectedOptionIds); setSaved(true);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not save your preferences. Please try again.'); }
    finally { saving.current = false; setBusy(false); }
  }

  return <View style={[styles.screen, { paddingTop: insets.top }]}>
    <StatusBar style="dark" />
    <View style={styles.panel}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to account" disabled={busy} onPress={leave} style={styles.iconButton}><ArrowLeft size={24} color={colors.text} /></Pressable>
        <Text accessibilityRole="header" style={styles.heading}>Preferences</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="Close preferences" disabled={busy} onPress={leave} style={styles.iconButton}><X size={24} color={colors.text} /></Pressable>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        {loading ? <ActivityIndicator color={colors.primary} accessibilityLabel="Loading preferences" /> : !preferences ? <>
          <Text accessibilityRole="alert" style={styles.error}>{error}</Text>
          <Pressable accessibilityRole="button" onPress={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} style={styles.primary}><Text style={styles.primaryText}>Retry</Text></Pressable>
        </> : <>
          <View style={styles.intro}>
            <View style={styles.introIcon}><SlidersHorizontal size={30} color={colors.primary} /></View>
            <Text accessibilityRole="header" style={styles.title}>Your interests</Text>
            <Text style={styles.description}>Choose what you enjoy. All questions are optional, and you can change your answers anytime.</Text>
          </View>
          {preferences.questions.length === 0 && <Text style={styles.description}>No preference questions are available yet.</Text>}
          {preferences.questions.map(question => {
            const count = question.options.filter(option => selected.includes(option.id)).length;
            return <View key={question.id} style={styles.question}>
              <Text accessibilityRole="header" style={styles.questionTitle}>{question.title}</Text>
              <Text style={styles.hint}>Select all that apply · {count} selected</Text>
              <View style={styles.options}>
                {question.options.map(option => {
                  const checked = selected.includes(option.id);
                  return <Pressable key={option.id} accessibilityRole="checkbox" accessibilityLabel={option.label}
                    accessibilityState={{ checked, disabled: busy }} aria-checked={checked} aria-disabled={busy} disabled={busy} onPress={() => toggle(option.id)}
                    style={({ pressed }) => [styles.option, checked && styles.optionSelected, pressed && styles.pressed, busy && styles.disabled]}>
                    <View style={[styles.checkbox, checked && styles.checkboxSelected]}>{checked && <Check size={15} color="#fff" strokeWidth={3} />}</View>
                    <Text style={[styles.optionText, checked && styles.optionTextSelected]}>{option.label}</Text>
                  </Pressable>;
                })}
              </View>
            </View>;
          })}
          <Pressable accessibilityRole="button" disabled={busy || selected.length === 0} onPress={() => { setSelected([]); setSaved(false); setError(null); }} style={[styles.clearButton, (busy || selected.length === 0) && styles.disabled]}>
            <Text style={styles.link}>Clear selections</Text>
          </Pressable>
        </>}
      </ScrollView>
      {preferences && <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
        {saved && <View accessibilityLiveRegion="polite" style={styles.success}><Check size={18} color={colors.primary} /><Text style={styles.link}>Preferences saved.</Text></View>}
        <View style={styles.actions}>
          <Pressable accessibilityRole="button" disabled={busy || !dirty} onPress={cancel} style={[styles.cancel, (busy || !dirty) && styles.disabled]}><Text style={styles.link}>Cancel</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityState={{ disabled: busy || !dirty, busy }} disabled={busy || !dirty} onPress={() => void save()} style={[styles.primary, (busy || !dirty) && styles.disabled]}>
            {busy && <ActivityIndicator color="#fff" />}
            <Text style={styles.primaryText}>{busy ? 'Saving…' : 'Save preferences'}</Text>
          </Pressable>
        </View>
      </View>}
    </View>
    <Modal transparent visible={confirmLeave} animationType="fade" onRequestClose={() => setConfirmLeave(false)}>
      <View style={styles.backdrop}><View style={styles.dialog}>
        <Text accessibilityRole="header" style={styles.heading}>Discard your changes?</Text>
        <Text style={styles.description}>Your unsaved selections will be lost.</Text>
        <Pressable accessibilityRole="button" onPress={() => setConfirmLeave(false)} style={styles.primary}><Text style={styles.primaryText}>Keep editing</Text></Pressable>
        <Pressable accessibilityRole="button" onPress={() => { setConfirmLeave(false); router.replace('/home'); }} style={styles.clearButton}><Text style={styles.discard}>Discard changes</Text></Pressable>
      </View></View>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background, alignItems: 'center' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' },
  panel: { flex: 1, width: '100%', maxWidth: 560, backgroundColor: '#fff', borderTopLeftRadius: 24, borderTopRightRadius: 24, overflow: 'hidden' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 12, borderBottomWidth: 1, borderColor: '#eef0f3' },
  heading: { fontSize: 20, fontWeight: '700', color: colors.text },
  iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 },
  content: { padding: 20, gap: 28 },
  intro: { alignItems: 'center', gap: 12 },
  introIcon: { width: 64, height: 64, borderRadius: 20, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 26, fontWeight: '700', color: colors.text },
  description: { fontSize: 15, lineHeight: 23, color: colors.muted, textAlign: 'center' },
  question: { gap: 10 },
  questionTitle: { fontSize: 18, lineHeight: 26, fontWeight: '700', color: colors.text },
  hint: { fontSize: 13, color: colors.muted },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  option: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 48, maxWidth: '100%', paddingHorizontal: 14, paddingVertical: 12, borderWidth: 1, borderColor: colors.border, borderRadius: 14 },
  optionSelected: { borderColor: colors.primary, backgroundColor: colors.blueTint },
  optionText: { fontSize: 15, color: colors.text, flexShrink: 1 },
  optionTextSelected: { color: colors.primary, fontWeight: '600' },
  checkbox: { width: 20, height: 20, borderRadius: 6, borderWidth: 1, borderColor: '#767d87', alignItems: 'center', justifyContent: 'center' },
  checkboxSelected: { borderColor: colors.primary, backgroundColor: colors.primary },
  pressed: { opacity: 0.7 },
  disabled: { opacity: 0.5 },
  clearButton: { minHeight: 44, alignItems: 'center', justifyContent: 'center', padding: 10 },
  link: { color: colors.primary, fontSize: 15, fontWeight: '600' },
  footer: { padding: 16, borderTopWidth: 1, borderColor: colors.border, gap: 12 },
  actions: { flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 },
  primary: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, minHeight: 48, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 14, backgroundColor: colors.primary },
  primaryText: { color: '#fff', fontSize: 15, fontWeight: '600' },
  cancel: { minHeight: 48, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 18, borderRadius: 14, borderWidth: 1, borderColor: colors.border },
  success: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  error: { color: '#a60000', backgroundColor: '#fff5f5', padding: 12, borderRadius: 12, fontSize: 14, lineHeight: 21 },
  backdrop: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24, backgroundColor: 'rgba(0,0,0,0.35)' },
  dialog: { width: '100%', maxWidth: 380, padding: 24, gap: 20, backgroundColor: '#fff', borderRadius: 24 },
  discard: { color: '#a60000', fontSize: 15, fontWeight: '600' },
});
