import { StyleSheet, Text } from 'react-native';

export interface AttributionFooterProps {
  attribution: string | null;
}

export default function AttributionFooter({ attribution }: AttributionFooterProps) {
  if (!attribution) {
    return null;
  }
  return <Text style={styles.attribution}>{attribution}</Text>;
}

const styles = StyleSheet.create({
  attribution: {
    color: '#8c96a4',
    fontSize: 11,
    textAlign: 'center',
    marginTop: 8,
  },
});
