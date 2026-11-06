import type { ReactElement } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import ActivityCard from './ActivityCard';
import type { ActivityRecommendationItem } from '../recommendations.types';

export interface RecommendationListProps {
  items: ActivityRecommendationItem[];
  refreshing: boolean;
  onRefresh(): void;
  header?: ReactElement | null;
}

export default function RecommendationList({
  items,
  refreshing,
  onRefresh,
  header,
}: RecommendationListProps) {
  return (
    <FlatList
      data={items}
      keyExtractor={item => item.id}
      refreshing={refreshing}
      onRefresh={onRefresh}
      ListHeaderComponent={header ? <View style={styles.headerWrapper}>{header}</View> : undefined}
      ItemSeparatorComponent={() => <View style={styles.separator} />}
      contentContainerStyle={styles.contentContainer}
      renderItem={({ item }) => <ActivityCard item={item} />}
    />
  );
}

const styles = StyleSheet.create({
  contentContainer: {
    paddingHorizontal: 18,
    paddingBottom: 32,
  },
  headerWrapper: {
    marginBottom: 14,
  },
  separator: {
    height: 12,
  },
});
