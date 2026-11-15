import type { FilterSpecification, LayerSpecification, StyleSpecification } from 'maplibre-gl';
import type { UpcomingEvent } from '../events/api/events';

export type PoiFilter = 'all' | 'events' | 'attractions';
export type EventTypeFilter = 'all' | 'CULTURAL' | 'SPORTS' | 'OTHER';
export const POI_LAYER_IDS = ['poi-dot', 'poi-label', 'poi-cluster', 'poi-cluster-count'];
export const ATTRACTIONS_FILTER: FilterSpecification = ['in', ['get', 'category'], ['literal', ['tourism', 'culture']]];
const HOSTED_ATTRACTIONS = ['attraction', 'museum', 'theatre', 'arts_centre', 'art_gallery', 'gallery', 'cinema', 'library', 'viewpoint', 'artwork', 'castle', 'monument', 'memorial', 'archaeological_site', 'ruins', 'zoo'];
const HOSTED_ATTRACTIONS_FILTER: FilterSpecification = ['any',
  ['in', ['get', 'class'], ['literal', HOSTED_ATTRACTIONS]],
  ['in', ['get', 'subclass'], ['literal', HOSTED_ATTRACTIONS]],
];

export function isPoiLayer(layer: LayerSpecification) {
  return POI_LAYER_IDS.includes(layer.id) || ('source-layer' in layer && layer['source-layer'] === 'poi');
}

export function filterEvents(events: UpcomingEvent[], poiFilter: PoiFilter, eventType: EventTypeFilter) {
  if (poiFilter === 'attractions') return [];
  return events.filter(event => eventType === 'all' || (event.eventType ?? 'OTHER') === eventType);
}

export function filterPoiStyle(style: StyleSpecification, filter: PoiFilter): StyleSpecification {
  return {
    ...style,
    layers: style.layers.map(layer => {
      if (!isPoiLayer(layer)) return layer;
      // Density tiles contain totals for every category, so cannot represent filtered counts.
      const hidden = filter === 'events' || (filter === 'attractions' && layer.id.startsWith('poi-cluster'));
      const filtered = { ...layer, layout: { ...layer.layout, visibility: hidden ? 'none' as const : layer.layout?.visibility ?? 'visible' as const } };
      const attractionFilter = 'source-layer' in layer && layer['source-layer'] === 'poi' ? HOSTED_ATTRACTIONS_FILTER : ATTRACTIONS_FILTER;
      if (filter === 'attractions') return { ...filtered,
        filter: 'filter' in layer && layer.filter ? ['all', layer.filter, attractionFilter] as FilterSpecification : attractionFilter };
      return filtered;
    }),
  };
}
