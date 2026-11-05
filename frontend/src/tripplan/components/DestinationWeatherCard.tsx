import { useState } from 'react';
import { ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  ArrowRight, Cloud, CloudDrizzle, CloudFog, CloudLightning, CloudRain,
  CloudSnow, CloudSun, Droplets, RefreshCw, Sun, Wind, type LucideIcon,
} from 'lucide-react-native';
import type { CitySuggestion, DestinationWeather, WeatherDay } from '../api/tripPlanning';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const INK = '#202632';
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function condition(code: number | null): { label: string; Icon: LucideIcon } {
  if (code === 0) return { label: 'Clear sky', Icon: Sun };
  if (code === 1 || code === 2) return { label: 'Partly cloudy', Icon: CloudSun };
  if (code === 3) return { label: 'Cloudy', Icon: Cloud };
  if (code === 45 || code === 48) return { label: 'Foggy', Icon: CloudFog };
  if (code != null && code >= 51 && code <= 57) return { label: 'Drizzle', Icon: CloudDrizzle };
  if (code != null && ((code >= 61 && code <= 67) || (code >= 80 && code <= 82))) {
    return { label: 'Rainy', Icon: CloudRain };
  }
  if (code != null && ((code >= 71 && code <= 77) || (code >= 85 && code <= 86))) {
    return { label: 'Snowy', Icon: CloudSnow };
  }
  if (code != null && code >= 95) return { label: 'Thunderstorms', Icon: CloudLightning };
  return { label: 'Conditions unavailable', Icon: Cloud };
}

function dayLabel(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return `${WEEKDAYS[date.getUTCDay()]} ${day}`;
}

function addDays(value: string, count: number): string {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + count)).toISOString().slice(0, 10);
}

function temperature(value: number | null): string {
  return value == null ? '—' : `${Math.round(value)}°`;
}

function measure(value: number | null, unit: string): string {
  return value == null ? '—' : `${Math.round(value)} ${unit}`;
}

function precipitation(value: number | null): string {
  return value == null ? '—' : `${value.toFixed(1)} mm`;
}

export default function DestinationWeatherCard({
  city, weather, busy, error, checkInDate, stayingNights, onLoad,
}: {
  city: CitySuggestion | null;
  weather: DestinationWeather | null;
  busy: boolean;
  error: string | null;
  checkInDate: string;
  stayingNights: string;
  onLoad(): void;
}) {
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const nights = Number(stayingNights);
  const tripEnd = /^\d{4}-\d{2}-\d{2}$/.test(checkInDate)
    && Number.isInteger(nights) && nights > 0 ? addDays(checkInDate, nights) : null;
  const days = weather?.days ?? [];
  const selected: WeatherDay | undefined = days.find((day) => day.date === selectedDate) ?? days[0];
  const selectedCondition = condition(selected?.weatherCode ?? null);
  const SelectedIcon = selectedCondition.Icon;
  const lastDate = days[days.length - 1]?.date;
  const tripOutsideForecast = tripEnd && lastDate && checkInDate > lastDate;
  const tripPartiallyCovered = tripEnd && lastDate && checkInDate <= lastDate
    && tripEnd > addDays(lastDate, 1);

  return <View style={styles.card}>
    <View style={styles.heading}>
      <View style={styles.headingCopy}>
        <Text style={styles.eyebrow}>DESTINATION WEATHER</Text>
        <Text style={styles.title}>A look at the days ahead</Text>
        <Text style={styles.helper}>{city
          ? `Explore the next 14 days in ${city.name}, ${city.country}`
          : 'Choose a destination city to see its forecast'}</Text>
      </View>
      <CloudSun size={28} color={BLUE} />
    </View>

    {city && <Pressable accessibilityRole="button" disabled={busy} onPress={onLoad}
      style={[styles.loadButton, busy && styles.disabled]}>
      {busy ? <ActivityIndicator color="#fff" /> : weather ? <RefreshCw size={17} color="#fff" /> : <ArrowRight size={17} color="#fff" />}
      <Text style={styles.loadText}>{busy ? 'Checking forecast…' : weather ? 'Refresh forecast' : 'Show 14-day forecast'}</Text>
    </Pressable>}
    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}

    {selected && <>
      <View style={styles.featured}>
        <View style={styles.featuredTop}>
          <View style={styles.featuredCopy}>
            <Text style={styles.featuredDay}>{dayLabel(selected.date)}</Text>
            <Text style={styles.featuredCondition}>{selectedCondition.label}</Text>
          </View>
          <SelectedIcon size={43} color="#fff" strokeWidth={1.7} />
        </View>
        <Text style={styles.featuredTemperature}>
          {temperature(selected.temperatureMaxC)} <Text style={styles.featuredLow}>/ {temperature(selected.temperatureMinC)}</Text>
        </Text>
        <View style={styles.featuredMetrics}>
          <View style={styles.metric}><Droplets size={15} color="#fff" />
            <Text style={styles.metricText}>{measure(selected.precipitationProbabilityPercent, '%')} rain chance</Text></View>
          <View style={styles.metric}><Wind size={15} color="#fff" />
            <Text style={styles.metricText}>{measure(selected.windSpeedMaxKmh, 'km/h')} wind</Text></View>
          <View style={styles.metric}><Droplets size={15} color="#fff" />
            <Text style={styles.metricText}>{precipitation(selected.precipitationMm)} precipitation</Text></View>
        </View>
      </View>

      <Text style={styles.sectionLabel}>DAILY OUTLOOK · {weather?.timezone}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.days}>
        {days.map((day) => {
          const { Icon, label } = condition(day.weatherCode);
          const active = day.date === selected.date;
          const duringStay = Boolean(tripEnd && day.date >= checkInDate && day.date < tripEnd);
          return <Pressable key={day.date} accessibilityRole="button"
            accessibilityLabel={`${dayLabel(day.date)}, ${label}, ${temperature(day.temperatureMaxC)} high${duringStay ? ', during your stay' : ''}`}
            accessibilityState={{ selected: active }} onPress={() => setSelectedDate(day.date)}
            style={[styles.dayTile, active && styles.dayTileActive, duringStay && styles.dayTileTrip]}>
            <Text style={[styles.dayName, active && styles.dayTextActive]}>{dayLabel(day.date)}</Text>
            <Icon size={25} color={active ? '#fff' : BLUE} />
            <Text style={[styles.dayTemperature, active && styles.dayTextActive]}>{temperature(day.temperatureMaxC)}</Text>
            <Text style={[styles.dayLow, active && styles.dayTextActive]}>{temperature(day.temperatureMinC)}</Text>
            {duringStay && <View style={[styles.tripDot, active && styles.tripDotActive]} />}
          </Pressable>;
        })}
      </ScrollView>

      {tripEnd && <Text style={styles.tripNote}>{tripOutsideForecast
        ? 'Your stay starts after this forecast window. Check again closer to departure.'
        : tripPartiallyCovered
          ? 'Only part of your stay is in the forecast window. Check again closer to departure.'
          : 'Highlighted days fall within your selected stay.'}</Text>}
      {!tripEnd && <Text style={styles.tripNote}>Choose a flight or check-in date and nights to highlight your stay.</Text>}
      <Text style={styles.source}>Forecast by Open-Meteo · Daily conditions may change.</Text>
      <Pressable accessibilityRole="link" onPress={() => { void Linking.openURL('https://open-meteo.com/'); }}>
        <Text style={styles.sourceLink}>About this forecast</Text>
      </Pressable>
    </>}
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderRadius: 26, borderWidth: 1, borderColor: '#E5E7F0', padding: 20, gap: 15 },
  heading: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  headingCopy: { flex: 1, gap: 4 },
  eyebrow: { color: PURPLE, fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  title: { color: INK, fontSize: 19, fontWeight: '800' },
  helper: { color: '#7A8595', fontSize: 13, lineHeight: 19 },
  loadButton: { minHeight: 46, borderRadius: 14, backgroundColor: BLUE, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  loadText: { color: '#fff', fontSize: 14, fontWeight: '800' },
  disabled: { opacity: 0.55 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  featured: { backgroundColor: BLUE, borderRadius: 21, padding: 18, gap: 10, overflow: 'hidden' },
  featuredTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  featuredCopy: { gap: 3 },
  featuredDay: { color: '#D9E8FF', fontSize: 13, fontWeight: '700' },
  featuredCondition: { color: '#fff', fontSize: 17, fontWeight: '800' },
  featuredTemperature: { color: '#fff', fontSize: 41, fontWeight: '800', letterSpacing: -1 },
  featuredLow: { color: '#D9E8FF', fontSize: 23, fontWeight: '600' },
  featuredMetrics: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  metric: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  metricText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  sectionLabel: { color: PURPLE, fontSize: 11, fontWeight: '800', letterSpacing: 0.8 },
  days: { gap: 8, paddingRight: 4 },
  dayTile: { width: 76, minHeight: 122, borderRadius: 15, backgroundColor: '#F2F6FC', paddingVertical: 11, alignItems: 'center', gap: 5 },
  dayTileActive: { backgroundColor: BLUE },
  dayTileTrip: { borderWidth: 2, borderColor: PURPLE },
  dayName: { color: INK, fontSize: 11, fontWeight: '800' },
  dayTextActive: { color: '#fff' },
  dayTemperature: { color: INK, fontSize: 17, fontWeight: '800' },
  dayLow: { color: '#7A8595', fontSize: 12 },
  tripDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: PURPLE },
  tripDotActive: { backgroundColor: '#fff' },
  tripNote: { color: '#455267', fontSize: 12, lineHeight: 18 },
  source: { color: '#7A8595', fontSize: 11 },
  sourceLink: { color: BLUE, fontSize: 12, fontWeight: '800' },
});
