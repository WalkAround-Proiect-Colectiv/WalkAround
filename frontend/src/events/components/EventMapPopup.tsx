import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { CalendarDays, ChevronLeft, ChevronRight, ArrowRight, X } from 'lucide-react-native';
import type { UpcomingEvent } from '../api/events';
import { formatEventWhen } from '../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';
const BORDER = '#dce2e9';

const DEFAULT_CARD_WIDTH = 268;
const DEFAULT_CARD_HEIGHT = 128;
const SCREEN_MARGIN = 12;
const PIN_HEAD_OFFSET_Y = 38;
const PIN_BELOW_OFFSET_Y = 10;
const TAIL_SIZE = 12;

export interface EventMapPopupProps {
  event: UpcomingEvent;
  currentIndex: number;
  totalCount: number;
  screenPoint: { x: number; y: number };
  containerWidth: number;
  containerHeight: number;
  topInset?: number;
  onSeeMore(event: UpcomingEvent): void;
  onClose(): void;
  onPrevious?(): void;
  onNext?(): void;
}

export default function EventMapPopup({
  event,
  currentIndex,
  totalCount,
  screenPoint,
  containerWidth,
  containerHeight,
  topInset = 0,
  onSeeMore,
  onClose,
  onPrevious,
  onNext,
}: EventMapPopupProps) {
  const [measuredHeight, setMeasuredHeight] = useState(DEFAULT_CARD_HEIGHT);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const cardWidth = Math.min(
    DEFAULT_CARD_WIDTH,
    Math.max(200, containerWidth - SCREEN_MARGIN * 2),
  );

  // Hide popup if the projected pin is outside the visible map viewport.
  if (
    screenPoint.x < -32 ||
    screenPoint.x > containerWidth + 32 ||
    screenPoint.y < -32 ||
    screenPoint.y > containerHeight + 32
  ) {
    return null;
  }

  const minLeft = SCREEN_MARGIN;
  const maxLeft = Math.max(minLeft, containerWidth - cardWidth - SCREEN_MARGIN);
  const cardLeft = Math.min(
    maxLeft,
    Math.max(minLeft, screenPoint.x - cardWidth / 2),
  );

  const tailCenterInCard = Math.min(
    cardWidth - 22,
    Math.max(22, screenPoint.x - cardLeft),
  );
  const tailLeft = tailCenterInCard - TAIL_SIZE / 2;

  const minTop = Math.max(SCREEN_MARGIN, topInset + 56);
  const aboveTop = screenPoint.y - PIN_HEAD_OFFSET_Y - measuredHeight;
  const placeBelow = aboveTop < minTop;
  const cardTop = placeBelow
    ? Math.min(
        Math.max(minTop, containerHeight - measuredHeight - SCREEN_MARGIN),
        screenPoint.y + PIN_BELOW_OFFSET_Y,
      )
    : aboveTop;

  const whenLabel = formatEventWhen(event.startsAt, event.allDay, event.whenText);

  return (
    <View
      style={[
        styles.wrapper,
        {
          left: cardLeft,
          top: cardTop,
          width: cardWidth,
        },
      ]}
      onLayout={e => {
        const nextHeight = Math.round(e.nativeEvent.layout.height);
        if (nextHeight > 0 && Math.abs(nextHeight - measuredHeight) > 1) {
          setMeasuredHeight(nextHeight);
        }
      }}
    >
      {placeBelow && (
        <View
          pointerEvents="none"
          style={[styles.tail, styles.tailTop, { left: tailLeft }]}
        />
      )}

      <View style={styles.card}>
        <View style={styles.headerRow}>
          <Text style={styles.title} numberOfLines={2}>
            {event.title}
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Close event popup"
            hitSlop={10}
            onPress={onClose}
            style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
          >
            <X size={18} color={MUTED} />
          </Pressable>
        </View>

        {Boolean(whenLabel) && (
          <View style={styles.dateRow}>
            <CalendarDays size={15} color={BLUE} />
            <Text style={styles.dateText} numberOfLines={1}>
              {whenLabel}
            </Text>
          </View>
        )}

        <View style={styles.footerRow}>
          {totalCount > 1 ? (
            <View style={styles.pager}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Previous event at this venue"
                hitSlop={6}
                onPress={onPrevious}
                style={({ pressed }) => [styles.pagerButton, pressed && styles.pressed]}
              >
                <ChevronLeft size={15} color={INK} />
              </Pressable>
              <Text style={styles.pagerText}>
                {`${currentIndex + 1} of ${totalCount}`}
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Next event at this venue"
                hitSlop={6}
                onPress={onNext}
                style={({ pressed }) => [styles.pagerButton, pressed && styles.pressed]}
              >
                <ChevronRight size={15} color={INK} />
              </Pressable>
            </View>
          ) : (
            <View />
          )}

          <Pressable
            accessibilityRole="link"
            accessibilityLabel={`See more about ${event.title}`}
            hitSlop={6}
            onPress={() => onSeeMore(event)}
            style={({ pressed }) => [styles.seeMoreLink, pressed && styles.pressed]}
          >
            <Text style={styles.seeMoreText}>See more</Text>
            <ArrowRight size={14} color={BLUE} />
          </Pressable>
        </View>
      </View>

      {!placeBelow && (
        <View
          pointerEvents="none"
          style={[styles.tail, styles.tailBottom, { left: tailLeft }]}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    zIndex: 15,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: BORDER,
    paddingHorizontal: 13,
    paddingTop: 11,
    paddingBottom: 11,
    gap: 8,
    elevation: 6,
    shadowColor: '#14243f',
    shadowOpacity: 0.18,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 8,
  },
  title: {
    flex: 1,
    color: INK,
    fontSize: 15,
    fontWeight: '700',
    lineHeight: 20,
  },
  closeButton: {
    width: 28,
    height: 28,
    marginTop: -2,
    marginRight: -4,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f1f5f9',
  },
  dateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dateText: {
    flex: 1,
    color: MUTED,
    fontSize: 13,
    fontWeight: '500',
  },
  footerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  pager: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#f4f7fb',
    borderRadius: 10,
    paddingHorizontal: 4,
    paddingVertical: 2,
  },
  pagerButton: {
    width: 22,
    height: 22,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 11,
  },
  pagerText: {
    color: INK,
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  seeMoreLink: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingVertical: 3,
    paddingHorizontal: 2,
  },
  seeMoreText: {
    color: BLUE,
    fontSize: 14,
    fontWeight: '700',
  },
  pressed: {
    opacity: 0.65,
  },
  tail: {
    position: 'absolute',
    width: TAIL_SIZE,
    height: TAIL_SIZE,
    backgroundColor: '#ffffff',
    borderColor: BORDER,
    transform: [{ rotate: '45deg' }],
  },
  tailBottom: {
    bottom: -TAIL_SIZE / 2 + 1,
    borderRightWidth: 1,
    borderBottomWidth: 1,
  },
  tailTop: {
    top: -TAIL_SIZE / 2 + 1,
    borderLeftWidth: 1,
    borderTopWidth: 1,
  },
});
