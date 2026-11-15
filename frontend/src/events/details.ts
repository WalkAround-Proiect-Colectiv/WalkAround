import { isSafeLink } from './format';
import type { TicketLink } from './api/events';

export interface TicketLinkGroups {
  buy: TicketLink[];
  info: TicketLink[];
}

/** Splits safe https links into ticket-purchase links and general info links. */
export function splitTicketLinks(links: TicketLink[] | null | undefined): TicketLinkGroups {
  const buy: TicketLink[] = [];
  const info: TicketLink[] = [];
  if (!links) {
    return { buy, info };
  }
  const seen = new Set<string>();
  for (const link of links) {
    if (!link || !isSafeLink(link.url) || seen.has(link.url)) {
      continue;
    }
    seen.add(link.url);
    if (link.kind === 'TICKETS') {
      buy.push(link);
    } else {
      info.push(link);
    }
  }
  return { buy, info };
}

/** Formats a venue rating and review count, e.g. "4.8 ★ (2.8k reviews)". */
export function formatRating(
  rating: number | null | undefined,
  reviewCount: number | null | undefined,
): string | null {
  if (typeof rating !== 'number' || !Number.isFinite(rating) || rating < 0 || rating > 5) {
    return null;
  }
  const ratingText = `${rating.toFixed(1)} ★`;
  if (typeof reviewCount !== 'number' || !Number.isFinite(reviewCount) || reviewCount <= 0) {
    return ratingText;
  }
  const countText = reviewCount >= 1000
    ? `${(reviewCount / 1000).toFixed(1).replace(/\.0$/, '')}k`
    : String(Math.round(reviewCount));
  const label = reviewCount === 1 ? 'review' : 'reviews';
  return `${ratingText} (${countText} ${label})`;
}

/** Returns a safe https URL to open the event location in Maps, or null if no location info exists. */
export function buildMapsUrl(options: {
  mapUrl?: string | null;
  venueName?: string | null;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}): string | null {
  if (isSafeLink(options.mapUrl)) {
    return options.mapUrl;
  }
  const queryParts = [options.venueName?.trim(), options.address?.trim()].filter(
    (part): part is string => Boolean(part && part.length > 0),
  );
  if (queryParts.length > 0) {
    const uniqueParts = Array.from(new Set(queryParts));
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(uniqueParts.join(', '))}`;
  }
  if (
    typeof options.latitude === 'number' &&
    typeof options.longitude === 'number' &&
    Number.isFinite(options.latitude) &&
    Number.isFinite(options.longitude)
  ) {
    return `https://www.google.com/maps/search/?api=1&query=${options.latitude},${options.longitude}`;
  }
  return null;
}
