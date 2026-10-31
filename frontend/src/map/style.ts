import type { StyleSpecification } from 'maplibre-gl';

export function resolveStyleResources(input: StyleSpecification, styleUrl: string): StyleSpecification {
  const style = structuredClone(input);
  const absolute = (value: string) => new URL(value, styleUrl).href.replace(/%7B/gi, '{').replace(/%7D/gi, '}');
  for (const source of Object.values(style.sources)) {
    if ('url' in source && source.url) source.url = absolute(source.url);
    if ('tiles' in source && source.tiles) source.tiles = source.tiles.map(absolute);
  }
  if (style.glyphs) style.glyphs = absolute(style.glyphs);
  if (typeof style.sprite === 'string') style.sprite = absolute(style.sprite);
  else if (style.sprite) style.sprite = style.sprite.map(sprite => ({ ...sprite, url: absolute(sprite.url) }));
  return style;
}

export function styleMap(input: StyleSpecification): StyleSpecification {
  const style = structuredClone(input);
  const rasterSources = new Set(Object.keys(style.sources).filter(key => style.sources[key].type === 'raster-dem' || style.sources[key].type === 'raster'));
  style.layers = style.layers.filter(layer => layer.type !== 'fill-extrusion' && !('source' in layer && rasterSources.has(layer.source)));
  for (const key of rasterSources) delete style.sources[key];
  delete style.terrain;
  for (const layer of style.layers) {
    if (layer.type === 'background') layer.paint = { ...layer.paint, 'background-color': '#f5f6f7' };
    if (layer.type === 'fill' && layer.id === 'water') layer.paint = { ...layer.paint, 'fill-color': '#a8d7f0' };
    if (layer.type === 'fill' && layer.id === 'landuse_residential') layer.paint = { ...layer.paint, 'fill-color': '#eef0f3' };
    if (layer.type === 'fill' && layer.id === 'building') {
      delete layer.maxzoom;
      layer.paint = { 'fill-color': '#dce0e5', 'fill-outline-color': '#c5cbd3' };
    }
    if (layer.type === 'line' && /^(road|bridge|tunnel)_/.test(layer.id) && !/rail|path|arrow|casing/.test(layer.id)) {
      layer.paint = { ...layer.paint, 'line-color': '#ffffff' };
    }
    if (layer.type === 'line' && /^(road|bridge|tunnel)_.*casing$/.test(layer.id)) layer.paint = { ...layer.paint, 'line-color': '#c5cbd3' };
    if (layer.type === 'symbol' && layer.layout) layer.layout['text-letter-spacing'] = 0;
  }
  return style;
}