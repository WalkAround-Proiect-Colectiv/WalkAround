import { useState } from 'react';
import { ActivityIndicator, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { AlertTriangle, ChevronDown, ExternalLink, MapPin, RefreshCw, ShieldAlert } from 'lucide-react-native';
import type {
  CitySuggestion, DestinationSafety, SafetyAlert, SafetyConflict,
} from '../api/tripPlanning';

const BLUE = '#0065EB';
const RED = '#FF0000';
const PURPLE = '#23012C';
const INK = '#202632';

function eventDate(value: string | null): string | null {
  return value ? value.replace('T', ' ').slice(0, 16) : null;
}

function checkedDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function severityColor(severity: string | null): string {
  return severity === 'High' || severity === 'Critical' ? RED : BLUE;
}

function Report({
  title, severity, location, description, date, distanceKm, source, sourceUrl, onOpen,
}: {
  title: string;
  severity: string | null;
  location: string | null;
  description: string | null;
  date: string | null;
  distanceKm: number;
  source: string | null;
  sourceUrl: string | null;
  onOpen(url: string): void;
}) {
  return <View style={styles.report}>
    <View style={styles.reportHeading}>
      <AlertTriangle size={17} color={severityColor(severity)} />
      <Text style={styles.reportTitle}>{title}</Text>
      {severity && <Text style={[styles.severity, { color: severityColor(severity) }]}>{severity}</Text>}
    </View>
    {location && <Text style={styles.detail}>{location}</Text>}
    {description && <Text style={styles.detail}>{description}</Text>}
    <Text style={styles.meta}>
      {distanceKm.toFixed(1)} km from city center{eventDate(date) ? ` · ${eventDate(date)}` : ''}
    </Text>
    {source && <Text style={styles.meta}>Source: {source}</Text>}
    {sourceUrl && <Pressable accessibilityRole="link" onPress={() => onOpen(sourceUrl)} style={styles.sourceLink}>
      <Text style={styles.sourceLinkText}>Read source</Text><ExternalLink size={14} color={BLUE} />
    </Pressable>}
  </View>;
}

export default function SafetyInformationCard({ city, safety, busy, error, onCheck }: {
  city: CitySuggestion | null;
  safety: DestinationSafety | null;
  busy: boolean;
  error: string | null;
  onCheck(): void;
}) {
  const [linkError, setLinkError] = useState<string | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);

  async function openSource(value: string) {
    try {
      const url = new URL(value);
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password ||
          url.search.toLowerCase().includes('api_key')) {
        throw new Error('Invalid source link');
      }
      await Linking.openURL(url.toString());
      setLinkError(null);
    } catch {
      setLinkError('Could not open this source. Please try again.');
    }
  }

  const advisory = safety?.advisory;
  const advisoryHigh = (advisory?.maxLevel ?? 0) >= 3;
  const alerts: SafetyAlert[] = safety?.alerts ?? [];
  const conflicts: SafetyConflict[] = safety?.conflicts ?? [];

  return <View style={styles.card}>
    <View style={styles.heading}>
      <View style={styles.icon}><ShieldAlert size={21} color={PURPLE} /></View>
      <View style={styles.headingCopy}>
        <Text style={styles.eyebrow}>DESTINATION SAFETY</Text>
        <Text style={styles.title}>Know before you go</Text>
        <Text style={styles.helper}>{city
          ? `Check advisories and nearby reports for ${city.name}`
          : 'Choose a destination city to check safety information'}</Text>
      </View>
    </View>

    {city && <Pressable accessibilityRole="button" disabled={busy} onPress={onCheck}
      style={[styles.checkButton, busy && styles.disabled]}>
      {busy ? <ActivityIndicator size="small" color="#fff" /> : <RefreshCw size={17} color="#fff" />}
      <Text style={styles.checkButtonText}>{busy ? 'Checking safety…' : safety ? 'Refresh safety' : 'Check safety'}</Text>
    </Pressable>}

    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}

    {safety && <>
      <Text style={styles.meta}>Checked {checkedDate(safety.checkedAt)} · Data: Travel Risk API</Text>

      <View style={styles.summary}>
        <Text style={[styles.summaryLevel, advisoryHigh && styles.advisoryTitleHigh]}>
          {advisory?.maxLevel != null ? `Country advisory: level ${advisory.maxLevel}` : 'Country advisory unavailable'}
        </Text>
        <Text style={styles.detail}>
          {alerts.length} nearby alert{alerts.length === 1 ? '' : 's'} · {conflicts.length} recent conflict report{conflicts.length === 1 ? '' : 's'} shown
        </Text>
        {(safety.alertsPartial || safety.conflictsPartial) &&
          <Text style={styles.caution}>Some reports may be omitted from these counts.</Text>}
        <Text style={styles.meta}>Nearby counts use geolocated reports; zero does not establish safety.</Text>
      </View>

      <Pressable accessibilityRole="button" accessibilityState={{ expanded: detailsOpen }}
        onPress={() => setDetailsOpen((open) => !open)} style={styles.detailsButton}>
        <Text style={styles.detailsButtonText}>{detailsOpen ? 'Hide safety details' : 'View advisories and reports'}</Text>
        <ChevronDown size={18} color={BLUE} style={detailsOpen && styles.chevronOpen} />
      </Pressable>

      {detailsOpen && <>
      <View style={styles.group}>
        <Text style={styles.groupTitle}>COUNTRY ADVISORIES · {safety.country.toUpperCase()}</Text>
        {advisory ? <View style={[styles.advisory, advisoryHigh && styles.advisoryHigh]}>
          <Text style={[styles.advisoryTitle, advisoryHigh && styles.advisoryTitleHigh]}>
            {advisory.maxLevel != null ? `Level ${advisory.maxLevel}` : 'Advisory'}
            {advisory.summary ? ` · ${advisory.summary}` : ''}
          </Text>
          {advisory.sources.map((source, index) => <View key={`${source.agency}-${index}`} style={styles.agency}>
            <Text style={styles.agencyTitle}>{source.agency || 'Government source'}
              {source.level != null ? ` · Level ${source.level}` : ''}</Text>
            {source.summary && <Text style={styles.detail}>{source.summary}</Text>}
            {source.updatedAt && <Text style={styles.meta}>Updated {eventDate(source.updatedAt)}</Text>}
            {source.url && <Pressable accessibilityRole="link" onPress={() => openSource(source.url!)}
              style={styles.sourceLink}><Text style={styles.sourceLinkText}>View advisory</Text>
              <ExternalLink size={14} color={BLUE} /></Pressable>}
          </View>)}
        </View> : <Text style={styles.empty}>No country advisory was returned.</Text>}
      </View>

      <View style={styles.group}>
        <Text style={styles.groupTitle}>NEARBY ALERTS · {safety.nearbyRadiusKm} KM</Text>
        {alerts.length === 0 && <Text style={styles.empty}>No geolocated alerts were found near this city in the retrieved data.</Text>}
        {alerts.map((alert, index) => <Report key={`${alert.id}-${index}`}
          title={alert.type?.replace(/_/g, ' ') || 'Alert'} severity={alert.severity}
          location={alert.location} description={alert.description} date={alert.eventDate}
          distanceKm={alert.distanceKm} source={alert.source} sourceUrl={null} onOpen={openSource} />)}
        {safety.alertsPartial && <Text style={styles.caution}>More alerts may exist than shown; this nearby list may be incomplete.</Text>}
      </View>

      <View style={styles.group}>
        <Text style={styles.groupTitle}>RECENT CONFLICT REPORTS · {safety.nearbyRadiusKm} KM</Text>
        {conflicts.length === 0 && <Text style={styles.empty}>No geolocated conflict reports were found near this city in the retrieved data.</Text>}
        {conflicts.map((conflict, index) => <Report key={`${conflict.id}-${index}`}
          title="Conflict report" severity={conflict.severity} location={conflict.location}
          description={conflict.description} date={conflict.eventDate} distanceKm={conflict.distanceKm}
          source={null} sourceUrl={conflict.sourceUrl} onOpen={openSource} />)}
        {safety.conflictsPartial && <Text style={styles.caution}>More conflict reports may exist than shown; this nearby list may be incomplete.</Text>}
      </View>

      <View style={styles.noteRow}><MapPin size={15} color="#66758A" />
        <Text style={styles.note}>Nearby reports use available coordinates and may omit unlocated events. Check official sources before travel.</Text>
      </View>
      {linkError && <Text accessibilityRole="alert" style={styles.error}>{linkError}</Text>}
      </>}
    </>}
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderRadius: 26, borderWidth: 1, borderColor: '#E5E7F0', padding: 20, gap: 16 },
  heading: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  icon: { width: 43, height: 43, borderRadius: 15, backgroundColor: '#F3ECF6', alignItems: 'center', justifyContent: 'center' },
  headingCopy: { flex: 1, gap: 3 },
  eyebrow: { color: PURPLE, fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  title: { color: INK, fontSize: 19, fontWeight: '800' },
  helper: { color: '#7A8595', fontSize: 13, lineHeight: 19 },
  checkButton: { minHeight: 47, borderRadius: 14, backgroundColor: BLUE, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  checkButtonText: { color: '#fff', fontWeight: '800', fontSize: 14 },
  disabled: { opacity: 0.55 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  meta: { color: '#7A8595', fontSize: 12, lineHeight: 18 },
  summary: { borderRadius: 15, backgroundColor: '#F5F7FC', padding: 13, gap: 4 },
  summaryLevel: { color: BLUE, fontSize: 14, fontWeight: '800' },
  detailsButton: { minHeight: 43, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  detailsButtonText: { color: BLUE, fontSize: 13, fontWeight: '800' },
  chevronOpen: { transform: [{ rotate: '180deg' }] },
  group: { gap: 9 },
  groupTitle: { color: PURPLE, fontWeight: '800', fontSize: 11, letterSpacing: 0.8 },
  advisory: { backgroundColor: '#F1F7FF', borderRadius: 15, padding: 13, gap: 10 },
  advisoryHigh: { backgroundColor: '#FFF1F1' },
  advisoryTitle: { color: BLUE, fontWeight: '800', fontSize: 15 },
  advisoryTitleHigh: { color: '#C51B2D' },
  agency: { borderTopWidth: 1, borderColor: '#E2E8F0', paddingTop: 9, gap: 3 },
  agencyTitle: { color: INK, fontWeight: '700', fontSize: 13 },
  detail: { color: '#455267', fontSize: 13, lineHeight: 19 },
  empty: { color: '#66758A', fontSize: 13, lineHeight: 19 },
  report: { borderWidth: 1, borderColor: '#E5EAF1', borderRadius: 15, padding: 13, gap: 6 },
  reportHeading: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 7 },
  reportTitle: { color: INK, fontSize: 14, fontWeight: '800', flex: 1 },
  severity: { fontSize: 12, fontWeight: '800' },
  sourceLink: { alignSelf: 'flex-start', minHeight: 36, flexDirection: 'row', alignItems: 'center', gap: 6 },
  sourceLinkText: { color: BLUE, fontSize: 12, fontWeight: '800' },
  caution: { color: '#B13A00', fontSize: 12, lineHeight: 18 },
  noteRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 7 },
  note: { color: '#66758A', fontSize: 12, lineHeight: 18, flex: 1 },
});
