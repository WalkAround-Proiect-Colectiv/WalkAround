import { StyleSheet, Text, View } from 'react-native';
import {
  Cloud,
  CloudFog,
  CloudLightning,
  CloudRain,
  CloudSnow,
  Sun,
  AlertCircle,
} from 'lucide-react-native';
import type { WeatherBucket } from '../recommendations.types';

const BLUE = '#0065EB';
const INK = '#202632';

export interface WeatherSummaryBannerProps {
  weatherBucket: WeatherBucket;
  temperatureC: number | null;
  weatherSummary: string;
  degraded: boolean;
  isStale: boolean;
}

function WeatherIcon({ bucket }: { bucket: WeatherBucket }) {
  switch (bucket) {
    case 'CLEAR':
      return <Sun size={26} color="#E9A51B" />;
    case 'RAIN':
      return <CloudRain size={26} color={BLUE} />;
    case 'STORM':
      return <CloudLightning size={26} color="#C51B2D" />;
    case 'SNOW':
      return <CloudSnow size={26} color={BLUE} />;
    case 'FOG':
      return <CloudFog size={26} color="#566276" />;
    case 'CLOUDY':
    default:
      return <Cloud size={26} color={BLUE} />;
  }
}

export default function WeatherSummaryBanner({
  weatherBucket,
  temperatureC,
  weatherSummary,
  degraded,
  isStale,
}: WeatherSummaryBannerProps) {
  const showDegradedNotice = degraded || isStale;

  return (
    <View style={styles.banner} accessibilityRole="summary">
      <View style={styles.iconCircle}>
        <WeatherIcon bucket={weatherBucket} />
      </View>
      <View style={styles.copy}>
        <View style={styles.topRow}>
          <Text style={styles.summary}>{weatherSummary}</Text>
          {temperatureC != null && (
            <View style={styles.tempPill}>
              <Text style={styles.tempText}>{Math.round(temperatureC)}°C</Text>
            </View>
          )}
        </View>
        {showDegradedNotice ? (
          <View style={styles.staleRow}>
            <AlertCircle size={14} color="#9A5B00" />
            <Text style={styles.staleText}>
              {degraded
                ? 'Live weather unavailable · showing general recommendations'
                : 'Showing recently cached weather conditions'}
            </Text>
          </View>
        ) : (
          <Text style={styles.subtext}>
            Activities ranked for current conditions and distance
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    padding: 16,
    borderRadius: 18,
    backgroundColor: '#EAF3FF',
    borderWidth: 1,
    borderColor: '#C9DDFB',
  },
  iconCircle: {
    width: 48,
    height: 48,
    borderRadius: 16,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  copy: {
    flex: 1,
    gap: 4,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  summary: {
    flex: 1,
    color: INK,
    fontSize: 16,
    fontWeight: '800',
  },
  tempPill: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: '#ffffff',
  },
  tempText: {
    color: BLUE,
    fontSize: 13,
    fontWeight: '800',
  },
  subtext: {
    color: '#455267',
    fontSize: 13,
  },
  staleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 2,
  },
  staleText: {
    flex: 1,
    color: '#9A5B00',
    fontSize: 12,
    fontWeight: '600',
  },
});
