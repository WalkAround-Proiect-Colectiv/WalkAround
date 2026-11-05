import { useState } from 'react';
import { ActivityIndicator, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { ChevronDown, ExternalLink, Phone, ShieldPlus } from 'lucide-react-native';
import type { CitySuggestion, CountryHotline, CountryHotlines, HotlineNumber } from '../api/tripPlanning';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const INK = '#202632';

const CHANNEL_LABELS: Record<HotlineNumber['type'], string> = {
  voice: 'Call',
  sms: 'SMS',
  text: 'Text',
  short_code: 'Short code',
};

function readable(value: string): string {
  return value.replace(/_/g, ' ');
}

export default function CountryHotlinesCard({ city, data, busy, error, onRetry }: {
  city: CitySuggestion | null;
  data: CountryHotlines | null;
  busy: boolean;
  error: string | null;
  onRetry(): void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);

  async function openNumber(number: HotlineNumber) {
    if (number.type === 'short_code' || !/^\+?[0-9 ()-]+$/.test(number.value)) return;
    const digits = number.value.replace(/[ ()-]/g, '');
    const scheme = number.type === 'voice' ? 'tel' : 'sms';
    try {
      await Linking.openURL(`${scheme}:${digits}`);
      setLinkError(null);
    } catch {
      setLinkError('Could not open this number on your device.');
    }
  }

  async function openWebsite(value: string) {
    try {
      const url = new URL(value);
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password ||
          url.search.toLowerCase().includes('api_key')) {
        throw new Error('Invalid hotline website');
      }
      await Linking.openURL(url.toString());
      setLinkError(null);
    } catch {
      setLinkError('Could not open this website.');
    }
  }

  const hotlines = data?.hotlines ?? [];
  const generalNumbers = [...new Set(hotlines
    .filter((hotline) => hotline.generalEmergency)
    .flatMap((hotline) => hotline.numbers
      .filter((number) => number.type === 'voice')
      .map((number) => number.value)))];

  return <View style={styles.card}>
    <View style={styles.heading}>
      <View style={styles.icon}><ShieldPlus size={21} color={PURPLE} /></View>
      <View style={styles.headingCopy}>
        <Text style={styles.eyebrow}>HELP IN YOUR DESTINATION</Text>
        <Text style={styles.title}>Emergency & support numbers</Text>
        <Text style={styles.helper}>{city
          ? `Numbers for ${city.country}, based on your destination in ${city.name}`
          : 'Choose a destination city to see its country hotlines'}</Text>
      </View>
    </View>

    {busy && <View style={styles.loading}><ActivityIndicator color={BLUE} />
      <Text style={styles.helper}>Loading country hotlines…</Text></View>}
    {error && <View style={styles.errorRow}>
      <Text accessibilityRole="alert" style={styles.error}>{error}</Text>
      <Pressable accessibilityRole="button" onPress={onRetry}><Text style={styles.action}>Try again</Text></Pressable>
    </View>}

    {data && <>
      {generalNumbers.length > 0 && <View style={styles.generalPanel}>
        <Text style={styles.generalTitle}>GENERAL EMERGENCY</Text>
        <View style={styles.numberRow}>{generalNumbers.map((value) =>
          <Pressable key={value} accessibilityRole="button"
            disabled={!/^\+?[0-9 ()-]+$/.test(value)}
            onPress={() => openNumber({ value, type: 'voice' })} style={styles.generalNumber}>
            <Phone size={17} color={BLUE} /><Text style={styles.generalNumberText}>{value}</Text>
          </Pressable>)}</View>
      </View>}
      {generalNumbers.length === 0 && <Text style={styles.helper}>No general emergency number is listed in the imported data.</Text>}
      <Pressable accessibilityRole="button" accessibilityState={{ expanded }}
        onPress={() => setExpanded((open) => !open)} style={styles.expandButton}>
        <Text style={styles.action}>{expanded ? 'Hide hotlines' : `View all hotlines (${hotlines.length})`}</Text>
        <ChevronDown size={18} color={BLUE} style={expanded && styles.chevronOpen} />
      </Pressable>
      {expanded && <View style={styles.list}>
        {hotlines.length === 0 && <Text style={styles.helper}>No hotlines are listed for this country yet.</Text>}
        {hotlines.map((hotline: CountryHotline) => <View key={hotline.id} style={styles.hotline}>
          <Text style={styles.category}>{readable(hotline.category || 'support')}</Text>
          <Text style={styles.hotlineTitle}>{hotline.name || hotline.organization || 'Hotline'}</Text>
          {hotline.organization && hotline.organization !== hotline.name &&
            <Text style={styles.detail}>{hotline.organization}</Text>}
          <View style={styles.numberRow}>{hotline.numbers.map((number, index) => {
            const dialable = number.type !== 'short_code' && /^\+?[0-9 ()-]+$/.test(number.value);
            return <Pressable key={`${number.type}-${number.value}-${index}`}
              accessibilityRole={dialable ? 'button' : undefined}
              disabled={!dialable} onPress={() => openNumber(number)} style={styles.numberPill}>
              <Text style={styles.numberType}>{CHANNEL_LABELS[number.type]}</Text>
              <Text style={styles.numberText}>{number.value}</Text>
            </Pressable>;
          })}</View>
          {hotline.hours && <Text style={styles.detail}>Hours: {hotline.hours}</Text>}
          {hotline.scope && <Text style={styles.detail}>Scope: {readable(hotline.scope)}</Text>}
          {hotline.cost && <Text style={styles.detail}>Cost: {readable(hotline.cost)}</Text>}
          {hotline.target && <Text style={styles.detail}>Who it helps: {hotline.target}</Text>}
          {hotline.geography && <Text style={styles.detail}>Coverage: {hotline.geography}</Text>}
          {hotline.languages && hotline.languages.length > 0 &&
            <Text style={styles.detail}>Languages: {hotline.languages.join(', ')}</Text>}
          {hotline.notes && <Text style={styles.detail}>{hotline.notes}</Text>}
          <Text style={styles.meta}>
            {hotline.verificationStatus ? readable(hotline.verificationStatus) : 'Verification not recorded'}
            {hotline.lastVerified ? ` · Last checked ${hotline.lastVerified}` : ''}
            {` · Source: ${hotline.source}`}
          </Text>
          {hotline.website && <Pressable accessibilityRole="link" onPress={() => openWebsite(hotline.website!)}
            style={styles.website}><Text style={styles.action}>Visit website</Text><ExternalLink size={14} color={BLUE} /></Pressable>}
          {hotline.chatUrl && <Pressable accessibilityRole="link" onPress={() => openWebsite(hotline.chatUrl!)}
            style={styles.website}><Text style={styles.action}>Open chat</Text><ExternalLink size={14} color={BLUE} /></Pressable>}
          {hotline.email && <Text style={styles.detail}>Email: {hotline.email}</Text>}
          {hotline.sourceLinks?.filter((link) => link.startsWith('https://')).map((link, index) =>
            <Pressable key={`${link}-${index}`} accessibilityRole="link" onPress={() => openWebsite(link)}
              style={styles.website}><Text style={styles.action}>Check source {index + 1}</Text>
              <ExternalLink size={14} color={BLUE} /></Pressable>)}
        </View>)}
      </View>}
      <Text style={styles.meta}>Check local availability before relying on a number. Some services require a local SIM or only work in certain areas.</Text>
      {linkError && <Text accessibilityRole="alert" style={styles.error}>{linkError}</Text>}
    </>}
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderRadius: 26, borderWidth: 1, borderColor: '#E5E7F0', padding: 20, gap: 14 },
  heading: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  icon: { width: 43, height: 43, borderRadius: 15, backgroundColor: '#F3ECF6', alignItems: 'center', justifyContent: 'center' },
  headingCopy: { flex: 1, gap: 3 },
  eyebrow: { color: PURPLE, fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  title: { color: INK, fontSize: 19, fontWeight: '800' },
  helper: { color: '#7A8595', fontSize: 13, lineHeight: 19 },
  loading: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  errorRow: { gap: 5 },
  error: { color: '#C51B2D', fontSize: 13 },
  action: { color: BLUE, fontSize: 13, fontWeight: '800' },
  generalPanel: { backgroundColor: '#F1F7FF', borderRadius: 15, padding: 14, gap: 9 },
  generalTitle: { color: PURPLE, fontSize: 11, fontWeight: '800', letterSpacing: 0.8 },
  numberRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  generalNumber: { flexDirection: 'row', alignItems: 'center', gap: 7, backgroundColor: '#fff', borderRadius: 12, paddingVertical: 10, paddingHorizontal: 12 },
  generalNumberText: { color: BLUE, fontSize: 20, fontWeight: '800' },
  expandButton: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  chevronOpen: { transform: [{ rotate: '180deg' }] },
  list: { gap: 10 },
  hotline: { borderWidth: 1, borderColor: '#E5EAF1', borderRadius: 15, padding: 13, gap: 5 },
  category: { color: PURPLE, fontSize: 11, fontWeight: '800', textTransform: 'uppercase' },
  hotlineTitle: { color: INK, fontSize: 15, fontWeight: '800' },
  detail: { color: '#455267', fontSize: 13, lineHeight: 19 },
  numberPill: { backgroundColor: '#F1F7FF', borderRadius: 10, paddingVertical: 8, paddingHorizontal: 10, gap: 2 },
  numberType: { color: '#66758A', fontSize: 10, fontWeight: '700' },
  numberText: { color: BLUE, fontSize: 15, fontWeight: '800' },
  meta: { color: '#7A8595', fontSize: 11, lineHeight: 17 },
  website: { alignSelf: 'flex-start', minHeight: 36, flexDirection: 'row', alignItems: 'center', gap: 5 },
});
